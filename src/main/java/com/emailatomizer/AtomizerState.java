package com.emailatomizer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import javax.swing.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AtomizerState {
    private final MontoyaApi api;
    private final List<MutationCase> mutations = MutationCatalog.all();
    private final CopyOnWriteArrayList<AtomResult> results = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, AtomResult> resultsByCorrelation = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> correlationRoles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StandaloneCollaboratorTarget> standaloneCollaboratorTargets = new ConcurrentHashMap<>();
    /** Persistent Collaborator account correlations temporarily/most-recently associated with a matrix row. */
    private final ConcurrentHashMap<String, AtomResult> persistentRunBindings = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<CollaboratorInboxEvent> collaboratorInbox = new CopyOnWriteArrayList<>();
    private final AtomicInteger collaboratorAccountSequence = new AtomicInteger(1);
    private volatile PersistentCollaboratorAccount collaboratorAccountA;
    private volatile PersistentCollaboratorAccount collaboratorAccountB;
    private final CopyOnWriteArrayList<EmailDiscovery> discoveries = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, EmailDiscovery> discoveriesByKey = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1);
    private final AtomicInteger discoverySequence = new AtomicInteger(1);
    private final AtomicBoolean matrixSending = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    /** Exact request strings currently being issued by Atomizer itself; used to suppress self-discovery only. */
    private final java.util.Set<String> activeMatrixRequests = ConcurrentHashMap.newKeySet();
    private volatile ResumeCheckpoint resumeCheckpoint;
    private final ExecutorService matrixExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "email-atomizer-matrix");
        t.setDaemon(true);
        return t;
    });

    private volatile CollaboratorClient collaboratorClient;
    private volatile CollaboratorPoller collaboratorPoller;
    private volatile AtomizerPanel panel;

    public AtomizerState(MontoyaApi api) {
        this.api = api;
    }

    public void setPanel(AtomizerPanel panel) {
        this.panel = panel;
    }

    public void setCollaboratorPoller(CollaboratorPoller collaboratorPoller) {
        this.collaboratorPoller = collaboratorPoller;
    }

    public List<MutationCase> mutations() {
        return mutations;
    }

    public List<String> mutationFamilies() {
        return mutations.stream().map(MutationCase::family).distinct().sorted().toList();
    }

    public List<AtomResult> results() {
        return new ArrayList<>(results);
    }

    public List<EmailDiscovery> discoveries() {
        ArrayList<EmailDiscovery> copy = new ArrayList<>(discoveries);
        copy.sort(Comparator.comparing((EmailDiscovery d) -> d.lastSeen).reversed());
        return copy;
    }

    public CollaboratorClient collaboratorClient() {
        return collaboratorClient;
    }

    public void initializeCollaborator() {
        try {
            collaboratorClient = api.collaborator().createClient();
            if (collaboratorClient != null) {
                api.logging().logToOutput("Email Atomizer: Collaborator client created.");
            } else {
                api.logging().logToOutput("Email Atomizer: Collaborator unavailable; using fallback domain when configured.");
            }
        } catch (Throwable t) {
            collaboratorClient = null;
            api.logging().logToOutput("Email Atomizer: Collaborator unavailable: " + t.getMessage());
        }
        fireCollaboratorInboxChanged();
    }

    public boolean isMatrixSending() {
        return matrixSending.get();
    }

    public String nextCorrelationId() {
        return String.format(Locale.ROOT, "atom%08d", sequence.getAndIncrement());
    }

    public RenderedMutation render(MutationCase mutation) {
        String canonical = panel == null ? "" : panel.canonicalEmail();
        return render(mutation, canonical, false, "", false);
    }

    public RenderedMutation render(MutationCase mutation, String canonical) {
        return render(mutation, canonical, false, "", false, null);
    }

    /**
     * Render one mutation for the manual Mutation Strings tab without sending an HTTP request.
     * Receiver-capable cases use either fresh Collaborator payloads or the controlled mailbox,
     * while non-receiver/probe-only mutations remain unchanged.
     */
    public RenderedMutation renderForMutationStrings(MutationCase mutation, String canonical,
                                                     boolean useControlledReceiver,
                                                     String receiverOverride,
                                                     String fallbackCollaboratorDomain) {
        RenderedMutation rendered = render(mutation, canonical, false, receiverOverride,
                useControlledReceiver, fallbackCollaboratorDomain);
        if (rendered.collaboratorExpected() && collaboratorClient != null) {
            registerStandaloneCollaboratorTarget(rendered.correlationId(),
                    "Mutation Strings",
                    mutation.id() + " — " + mutation.label(),
                    rendered.email(),
                    rendered.collaboratorHost());
        }
        return rendered;
    }

    private RenderedMutation render(MutationCase mutation, String canonical,
                                    boolean forceCollaborator, String receiverOverride,
                                    boolean useControlledReceiverForPrimary) {
        return render(mutation, canonical, forceCollaborator, receiverOverride,
                useControlledReceiverForPrimary, null);
    }

    private RenderedMutation render(MutationCase mutation, String canonical,
                                    boolean forceCollaborator, String receiverOverride,
                                    boolean useControlledReceiverForPrimary,
                                    String fallbackCollaboratorDomain) {
        if (canonical == null || !canonical.contains("@") || canonical.startsWith("@") || canonical.endsWith("@")) {
            throw new IllegalArgumentException("Canonical email must contain a local part and domain.");
        }

        int at = canonical.lastIndexOf('@');
        String local = canonical.substring(0, at);
        String domain = canonical.substring(at + 1);
        String correlation = nextCorrelationId();
        String collabHost = fallbackCollaboratorDomain != null
                ? fallbackCollaboratorDomain.trim()
                : (panel == null ? "" : panel.fallbackDomain());
        boolean controlledReceiver = useControlledReceiverForPrimary &&
                receiverOverride != null && !receiverOverride.isBlank();
        boolean collaboratorExpected = (mutation.collaboratorCapable() || forceCollaborator) && !controlledReceiver;

        if (collaboratorExpected && collaboratorClient != null) {
            try {
                CollaboratorPayload payload = collaboratorClient.generatePayload(correlation);
                collabHost = payload.toString();
            } catch (Throwable t) {
                api.logging().logToError("Email Atomizer: failed to generate Collaborator payload: " + t.getMessage());
            }
        }

        if (collabHost == null || collabHost.isBlank()) collabHost = "collaborator.invalid";
        String receiverEmail = receiverOverride == null || receiverOverride.isBlank() || !controlledReceiver
                ? "atom@" + collabHost
                : receiverOverride.trim();

        MutationContext context = new MutationContext(canonical, local, domain, collabHost, correlation, receiverEmail);
        String mutated = mutation.generator().generate(context);
        return new RenderedMutation(mutation, correlation, collabHost, mutated, receiverEmail, collaboratorExpected);
    }

    public AtomResult record(RenderedMutation rendered, String method, String url) {
        AtomResult result = new AtomResult(rendered.correlationId(), rendered.mutation(), method, url,
                rendered.email(), rendered.collaboratorHost());
        results.add(result);
        resultsByCorrelation.put(result.correlationId, result);
        correlationRoles.put(result.correlationId, rendered.mutation().id().startsWith("DUAL-")
                ? "embedded receiver / mutation"
                : "mutation payload");
        fireResultsChanged();
        return result;
    }

    private SecondaryReceiver createSecondaryReceiver(String parentCorrelation, String receiverOverride) {
        if (receiverOverride != null && !receiverOverride.isBlank()) {
            return new SecondaryReceiver("", "", receiverOverride.trim());
        }
        String correlation = "r" + parentCorrelation;
        String host = panel == null ? "" : panel.fallbackDomain();
        if (collaboratorClient != null) {
            try {
                host = collaboratorClient.generatePayload(correlation).toString();
            } catch (Throwable t) {
                api.logging().logToError("Email Atomizer: failed to generate secondary Collaborator payload: " + t.getMessage());
            }
        }
        if (host == null || host.isBlank()) host = "collaborator.invalid";
        return new SecondaryReceiver(correlation, host, "atom@" + host);
    }

    private void bindSecondaryReceiver(SecondaryReceiver receiver, AtomResult result) {
        if (receiver == null || receiver.correlationId().isBlank()) return;
        resultsByCorrelation.put(receiver.correlationId(), result);
        correlationRoles.put(receiver.correlationId(), "secondary receiver occurrence");
    }

    public AtomResult resultForCorrelation(String correlation) {
        return resultsByCorrelation.get(correlation);
    }

    public String roleForCorrelation(String correlation) {
        return correlationRoles.getOrDefault(correlation, "mutation payload");
    }

    public boolean collaboratorAvailable() {
        return collaboratorClient != null;
    }

    public PersistentCollaboratorAccount persistentCollaboratorAccount(String slot) {
        return "B".equalsIgnoreCase(slot) ? collaboratorAccountB : collaboratorAccountA;
    }

    /**
     * Generate a Collaborator-backed account address and keep it stable until the user explicitly regenerates it.
     * This method does not send anything to the target application.
     */
    public synchronized PersistentCollaboratorAccount generatePersistentCollaboratorAccount(String slot,
                                                                                              String localPart,
                                                                                              String domainLabel) {
        String normalizedSlot = "B".equalsIgnoreCase(slot) ? "B" : "A";
        if (collaboratorClient == null) {
            throw new IllegalStateException("Burp Collaborator is unavailable in this Atomizer session.");
        }
        String local = localPart == null ? "" : localPart.trim();
        String label = domainLabel == null ? "" : domainLabel.trim();
        if (local.isBlank() || local.contains("@") || local.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Local part must be non-empty and must not contain @ or spaces.");
        }
        if (label.isBlank() || label.contains("@") || label.indexOf('.') >= 0 || label.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Domain label must be one non-empty label (for example gmail or yahoo).");
        }

        String correlation = String.format(Locale.ROOT, "acct%s%06d",
                normalizedSlot.toLowerCase(Locale.ROOT), collaboratorAccountSequence.getAndIncrement());
        String root;
        try {
            root = collaboratorClient.generatePayload(correlation).toString();
        } catch (Throwable t) {
            throw new IllegalStateException("Could not generate Burp Collaborator payload: " + t.getMessage(), t);
        }
        if (root == null || root.isBlank()) {
            throw new IllegalStateException("Burp Collaborator returned an empty payload.");
        }

        String email = local + "@" + label + "." + root;
        PersistentCollaboratorAccount account = new PersistentCollaboratorAccount(
                normalizedSlot, correlation, root, local, label, email, Instant.now());
        registerStandaloneCollaboratorTarget(correlation,
                "Persistent account " + normalizedSlot,
                "Persistent Collaborator-backed test account " + normalizedSlot,
                email,
                root);

        if ("B".equals(normalizedSlot)) collaboratorAccountB = account;
        else collaboratorAccountA = account;

        api.logging().logToOutput("Email Atomizer: generated persistent Collaborator account " +
                normalizedSlot + " address " + email + ". No target request was sent.");
        fireCollaboratorInboxChanged();
        return account;
    }


    /**
     * Associate persistent Collaborator Account A/B roots with the current matrix row. A persistent account
     * necessarily reuses one Collaborator correlation across many requests, so the binding intentionally tracks
     * the most recent row that referenced that account. The post-request collection window makes this deterministic
     * for normal prompt SMTP delivery, while the standalone inbox remains the lossless source for delayed events.
     */
    public void bindPersistentCollaboratorAccountsForRunResult(AtomResult result, String... candidateValues) {
        if (result == null) return;
        bindPersistentAccountForRunResult(collaboratorAccountA, result, candidateValues);
        bindPersistentAccountForRunResult(collaboratorAccountB, result, candidateValues);
    }

    private void bindPersistentAccountForRunResult(PersistentCollaboratorAccount account, AtomResult result,
                                                   String... candidateValues) {
        if (account == null || account.correlationId() == null || account.correlationId().isBlank()) return;
        if (candidateValues == null || candidateValues.length == 0) return;
        String root = account.collaboratorRoot();
        if (root == null || root.isBlank()) return;
        String needle = root.toLowerCase(Locale.ROOT);
        for (String value : candidateValues) {
            if (value == null || value.isBlank()) continue;
            if (value.toLowerCase(Locale.ROOT).contains(needle)) {
                persistentRunBindings.put(account.correlationId(), result);
                correlationRoles.put(account.correlationId(), "persistent account " + account.slot() + " delivery");
                return;
            }
        }
    }

    /** Latest matrix result associated with a persistent Collaborator account correlation, if any. */
    public AtomResult resultForPersistentRunCorrelation(String correlation) {
        if (correlation == null || correlation.isBlank()) return null;
        return persistentRunBindings.get(correlation);
    }

    public boolean resultHasPersistentRunBinding(AtomResult result) {
        return result != null && persistentRunBindings.containsValue(result);
    }

    /**
     * End the clean attribution window for a matrix row. Persistent account roots are reused across cases;
     * keeping an old binding after the collection window could incorrectly attach a delayed SMTP event to a
     * later/previous row. Delayed evidence is still preserved by the standalone Collaborator inbox.
     */
    public void clearPersistentRunBindingsForResult(AtomResult result) {
        if (result == null) return;
        persistentRunBindings.entrySet().removeIf(entry -> entry.getValue() == result);
    }

    private void clearAllPersistentRunBindings() {
        persistentRunBindings.clear();
    }

    public StandaloneCollaboratorTarget standaloneCollaboratorTarget(String correlation) {
        return standaloneCollaboratorTargets.get(correlation);
    }

    public void registerStandaloneCollaboratorTarget(String correlation, String source, String label,
                                                     String generatedAddress, String collaboratorRoot) {
        if (correlation == null || correlation.isBlank()) return;
        standaloneCollaboratorTargets.put(correlation, new StandaloneCollaboratorTarget(
                correlation,
                source == null ? "" : source,
                label == null ? "" : label,
                generatedAddress == null ? "" : generatedAddress,
                collaboratorRoot == null ? "" : collaboratorRoot));
    }

    public void recordStandaloneCollaboratorEvidence(String correlation, CollaboratorEvidence evidence) {
        if (correlation == null || correlation.isBlank() || evidence == null) return;
        StandaloneCollaboratorTarget target = standaloneCollaboratorTargets.get(correlation);
        if (target == null) return;
        collaboratorInbox.add(new CollaboratorInboxEvent(
                correlation, target.source(), target.label(), target.generatedAddress(), target.collaboratorRoot(), evidence));
        fireCollaboratorInboxChanged();
    }

    public List<CollaboratorInboxEvent> collaboratorInbox() {
        ArrayList<CollaboratorInboxEvent> copy = new ArrayList<>(collaboratorInbox);
        copy.sort(Comparator.comparing((CollaboratorInboxEvent e) ->
                e.timestamp() == null ? ZonedDateTime.parse("1970-01-01T00:00:00Z") : e.timestamp()).reversed());
        return copy;
    }

    public void clearCollaboratorInbox() {
        collaboratorInbox.clear();
        fireCollaboratorInboxChanged();
    }

    public void pollCollaboratorNow() {
        CollaboratorPoller poller = collaboratorPoller;
        if (poller != null) poller.poll();
    }

    public void fireCollaboratorInboxChanged() {
        AtomizerPanel p = panel;
        if (p != null) SwingUtilities.invokeLater(p::refreshCollaboratorInbox);
    }

    public AtomResult matchResultForRequest(String requestText) {
        if (requestText == null) return null;
        for (int i = results.size() - 1; i >= 0; i--) {
            AtomResult r = results.get(i);
            if (containsMutationForm(requestText, r.mutatedEmail)) return r;
        }
        return null;
    }

    private boolean containsMutationForm(String requestText, String value) {
        if (requestText.contains(value)) return true;
        String enc = RequestMutator.urlEncode(value);
        if (requestText.contains(enc)) return true;
        if (requestText.contains(RequestMutator.doubleUrlEncode(value))) return true;
        return requestText.contains(value.replace("@", "%40"));
    }

    public void observeRequest(HttpRequest request) {
        AtomizerPanel p = panel;
        if (p == null || !p.passiveDiscoveryEnabled()) return;
        // Do not globally disable passive observation while a matrix runs: the user may keep browsing.
        // Suppress only the exact requests issued by Atomizer itself.
        if (request == null || activeMatrixRequests.contains(request.toString())) return;
        if (p.passiveScopeOnly() && !request.isInScope()) return;
        if (p.passiveSkipGet() && (request.method().equalsIgnoreCase("GET") || request.method().equalsIgnoreCase("HEAD"))) return;

        List<EmailCandidate> candidates;
        try {
            candidates = EmailDetector.detect(request);
        } catch (Throwable t) {
            api.logging().logToError("Email Atomizer passive detection failed: " + t.getMessage());
            return;
        }
        if (candidates.isEmpty()) return;

        HttpRequest durable;
        try {
            durable = request.copyToTempFile();
        } catch (Throwable t) {
            durable = request;
        }

        boolean changed = false;
        String method = safe(request::method, "?");
        String url = safe(request::url, "");
        String host = hostOf(url);
        String path = pathOf(url);
        String operation = RequestClassifier.operation(request);

        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            EmailCandidate candidate = candidates.get(candidateIndex);
            String slot = discoverySlot(candidates, candidateIndex);
            String key = discoveryKey(method, host, path, operation, slot, candidate);
            EmailDiscovery existing = discoveriesByKey.get(key);
            if (existing != null) {
                existing.observeAgain(durable);
                changed = true;
                continue;
            }

            EmailDiscovery created = new EmailDiscovery(
                    String.format(Locale.ROOT, "disc%06d", discoverySequence.getAndIncrement()),
                    durable, candidate, method, url, host, path, operation, candidateIndex + 1);
            EmailDiscovery race = discoveriesByKey.putIfAbsent(key, created);
            if (race == null) {
                discoveries.add(created);
            } else {
                race.observeAgain(durable);
            }
            changed = true;
        }

        if (changed) fireDiscoveriesChanged();
    }

    /** Attach the latest response to any passive discovery represented by this initiating request. */
    public void observeResponse(burp.api.montoya.http.handler.HttpResponseReceived response) {
        AtomizerPanel p = panel;
        if (p == null || response == null || !p.passiveDiscoveryEnabled()) return;
        HttpRequest initiating = response.initiatingRequest();
        if (initiating == null || activeMatrixRequests.contains(initiating.toString())) return;
        if (p.passiveScopeOnly() && !initiating.isInScope()) return;
        if (p.passiveSkipGet() && (initiating.method().equalsIgnoreCase("GET") || initiating.method().equalsIgnoreCase("HEAD"))) return;

        List<EmailCandidate> candidates;
        try {
            candidates = EmailDetector.detect(initiating);
        } catch (Throwable t) {
            api.logging().logToError("Email Atomizer passive response matching failed: " + t.getMessage());
            return;
        }
        if (candidates.isEmpty()) return;

        HttpResponse durableResponse;
        try {
            durableResponse = response.copyToTempFile();
        } catch (Throwable t) {
            durableResponse = response;
        }

        String method = safe(initiating::method, "?");
        String url = safe(initiating::url, "");
        String host = hostOf(url);
        String path = pathOf(url);
        String operation = RequestClassifier.operation(initiating);
        boolean changed = false;
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            EmailCandidate candidate = candidates.get(candidateIndex);
            String slot = discoverySlot(candidates, candidateIndex);
            EmailDiscovery discovery = discoveriesByKey.get(discoveryKey(method, host, path, operation, slot, candidate));
            if (discovery != null) {
                discovery.observeResponse(durableResponse);
                changed = true;
            }
        }
        if (changed) fireDiscoveriesChanged();
    }

    public void clearDiscoveries() {
        discoveries.clear();
        discoveriesByKey.clear();
        fireDiscoveriesChanged();
    }

    public void sendToBuilder(HttpRequest request) {
        AtomizerPanel p = panel;
        if (p == null || request == null) return;
        List<EmailCandidate> candidates = EmailDetector.detect(request);
        if (candidates.isEmpty()) {
            String configured = p.canonicalEmail();
            if (configured != null && !configured.isBlank() && requestContainsCanonical(request.toString(), configured)) {
                candidates = List.of(new EmailCandidate(configured, "configured match", "", "supported encoding"));
            }
        }
        if (candidates.isEmpty()) {
            p.setStatus("No email address detected in the selected request.");
            return;
        }
        HttpRequest durable;
        try {
            durable = request.copyToTempFile();
        } catch (Throwable t) {
            durable = request;
        }
        p.loadIntoBuilder(durable, candidates);
    }

    public void sendDiscoveryToBuilder(EmailDiscovery discovery) {
        if (discovery == null || panel == null) return;
        List<EmailCandidate> all = EmailDetector.detect(discovery.request);
        if (all.isEmpty()) all = List.of(discovery.candidate);
        panel.loadIntoBuilder(discovery.request, all, discovery.occurrenceIndex);
    }

    public void sendRequestToRepeater(HttpRequest request) {
        if (request == null) return;
        try { api.repeater().sendToRepeater(request); }
        catch (Throwable t) { feedbackError("Send to Repeater failed", t); }
    }

    public void sendRequestToIntruder(HttpRequest request) {
        if (request == null) return;
        try { api.intruder().sendToIntruder(request); }
        catch (Throwable t) { feedbackError("Send to Intruder failed", t); }
    }

    public void sendRequestToOrganizer(HttpRequest request) {
        if (request == null) return;
        try { api.organizer().sendToOrganizer(request); }
        catch (Throwable t) { feedbackError("Send to Organizer failed", t); }
    }

    public void clearResults() {
        results.clear();
        resultsByCorrelation.clear();
        correlationRoles.clear();
        persistentRunBindings.clear();
        clearResumeCheckpoint();
        fireResultsChanged();
    }

    public void fireResultsChanged() {
        AtomizerPanel p = panel;
        if (p != null) SwingUtilities.invokeLater(p::refreshResults);
    }

    public void fireDiscoveriesChanged() {
        AtomizerPanel p = panel;
        if (p != null) SwingUtilities.invokeLater(p::refreshDiscoveries);
    }

    private void feedback(String message) {
        api.logging().logToOutput("Email Atomizer: " + message);
        AtomizerPanel p = panel;
        if (p != null) {
            p.setStatus(message);
            p.appendActivity(message);
        }
    }

    private void feedbackError(String message, Throwable t) {
        String detail = message + (t == null || t.getMessage() == null ? "" : ": " + t.getMessage());
        api.logging().logToError("Email Atomizer: " + detail);
        AtomizerPanel p = panel;
        if (p != null) {
            p.setStatus(detail);
            p.appendActivity("ERROR: " + detail);
        }
    }

    public void runMatrix(HttpRequest original) {
        AtomizerPanel p = panel;
        if (p == null) return;
        runMatrixInternal(original, p.canonicalEmail(), null, null, "", false, p.selectedMatrixMutations(), p.matrixRunConfig());
    }

    public void runMatrix(HttpRequest original, EmailCandidate target, List<MutationCase> selected, MatrixRunConfig config) {
        runMatrix(original, target, null, "", false, selected, config);
    }

    public void runMatrix(HttpRequest original, EmailCandidate target,
                          EmailCandidate secondaryReceiverTarget, String receiverOverride,
                          List<MutationCase> selected, MatrixRunConfig config) {
        runMatrix(original, target, secondaryReceiverTarget, receiverOverride, false, selected, config);
    }

    public void runMatrix(HttpRequest original, EmailCandidate target,
                          EmailCandidate secondaryReceiverTarget, String receiverOverride,
                          boolean useControlledReceiverForPrimary,
                          List<MutationCase> selected, MatrixRunConfig config) {
        if (target == null) {
            if (panel != null) panel.setStatus("Choose an email candidate first.");
            return;
        }
        runMatrixInternal(original, target.email(), target, secondaryReceiverTarget,
                receiverOverride == null ? "" : receiverOverride.trim(), useControlledReceiverForPrimary, selected, config);
    }

    public void runMatrix(HttpRequest original, String canonical, List<MutationCase> selected, MatrixRunConfig config) {
        runMatrixInternal(original, canonical, null, null, "", false, selected, config);
    }

    private void runMatrixInternal(HttpRequest original, String canonical, EmailCandidate target,
                                   EmailCandidate secondaryReceiverTarget, String receiverOverride,
                                   boolean useControlledReceiverForPrimary,
                                   List<MutationCase> selected, MatrixRunConfig config) {
        AtomizerPanel p = panel;
        if (p == null || original == null) {
            feedback("Matrix not started: no request is loaded.");
            return;
        }
        if (canonical == null || canonical.isBlank()) {
            feedback("Matrix not started: choose a detected email first.");
            return;
        }
        if (p.scopeOnly() && !original.isInScope()) {
            feedback("Matrix not started: request is outside Burp Target scope while scope-only sending is enabled. Add the host to scope or uncheck the scope-only option.");
            return;
        }
        if (target == null && !requestContainsCanonical(original.toString(), canonical)) {
            feedback("Matrix not started: selected request no longer contains that email in a supported representation.");
            return;
        }
        if (target != null && !targetAvailable(original.toString(), target)) {
            feedback("Matrix not started: the selected email occurrence is no longer present in the stored request.");
            return;
        }
        if (secondaryReceiverTarget != null) {
            if (!targetAvailable(original.toString(), secondaryReceiverTarget)) {
                feedback("Matrix not started: the selected second/receiver email occurrence is no longer present in the stored request.");
                return;
            }
            if (target != null && sameOccurrence(target, secondaryReceiverTarget)) {
                feedback("Matrix not started: identity and receiver must be different email occurrences.");
                return;
            }
        }
        if (selected == null || selected.isEmpty()) {
            feedback("Matrix not started: select at least one mutation matrix.");
            return;
        }
        if (!matrixSending.compareAndSet(false, true)) {
            feedback("Matrix not started: another mutation matrix is already running.");
            return;
        }
        cancelRequested.set(false);
        if (panel != null) panel.setRunActive(true);
        clearResumeCheckpoint();

        List<MutationCase> runList = normalizedRunList(selected);
        if (config.maxTests() > 0 && runList.size() > config.maxTests()) {
            runList = new ArrayList<>(runList.subList(0, config.maxTests()));
        }
        runList = withDeliverySentinels(runList, config);
        final List<MutationCase> finalRunList = List.copyOf(runList);
        String roleNote = secondaryReceiverTarget == null ? "" : " Two-occurrence identity/receiver mode enabled.";
        String sentinelNote = config.deliverySentinelsEnabled()
                ? " Delivery sentinels enabled (start/end" +
                    (config.deliverySentinelEvery() > 0 ? " + every " + config.deliverySentinelEvery() + " mutation tests" : "") + ")."
                : "";
        feedback("Matrix starting: " + finalRunList.size() + " requests, ≥" + config.delayMs() +
                " ms spacing, OAST collection window=" + config.collaboratorCollectionWindowMs() +
                " ms, stop codes=" + describeStopCodes(config) +
                ", stop text=" + describeStopText(config) + "." + roleNote + sentinelNote);
        submitMatrixExecution(original, canonical, target, secondaryReceiverTarget, receiverOverride,
                useControlledReceiverForPrimary,
                finalRunList, config, null, -1, 0, finalRunList.size(), false);
    }

    /** Resume from the next unsent mutation after a configured stop condition, cancellation, or interruption. */
    public void resumeMatrix() {
        ResumeCheckpoint checkpoint = resumeCheckpoint;
        AtomizerPanel p = panel;
        if (p == null || checkpoint == null || checkpoint.remaining().isEmpty()) {
            feedback("No stopped matrix has remaining tests to resume.");
            return;
        }
        if (p.scopeOnly() && !checkpoint.original().isInScope()) {
            feedback("Resume blocked: stored request is outside Burp Target scope while scope-only sending is enabled.");
            return;
        }
        long cooldownMs = Math.max(0L, checkpoint.notBeforeEpochMs() - System.currentTimeMillis());
        if (cooldownMs > 0 && p.respectRetryAfterSelected()) {
            feedback("Resume deferred by Retry-After cooldown; approximately " + ((cooldownMs + 999) / 1000) +
                    " seconds remain. Uncheck 'Respect Retry-After if continuing' to manage the cooldown manually.");
            return;
        }
        if (!matrixSending.compareAndSet(false, true)) {
            feedback("Resume not started: another mutation matrix is already running.");
            return;
        }
        MatrixRunConfig resumeConfig = withRetryAfterSetting(checkpoint.config(), p.respectRetryAfterSelected());
        cancelRequested.set(false);
        if (panel != null) panel.setRunActive(true);
        resumeCheckpoint = null;
        p.setResumeAvailable(false, 0, "");

        feedback("Matrix resuming: " + checkpoint.remaining().size() + " remaining tests (" +
                checkpoint.completedCount() + "/" + checkpoint.totalCount() + " already processed), ≥" +
                resumeConfig.delayMs() + " ms spacing, OAST window=" + resumeConfig.collaboratorCollectionWindowMs() +
                " ms, stop codes=" + describeStopCodes(resumeConfig) +
                ", stop text=" + describeStopText(resumeConfig) +
                ", Retry-After enforcement=" + (resumeConfig.respectRetryAfter() ? "on" : "off") + ".");
        submitMatrixExecution(checkpoint.original(), checkpoint.canonical(), checkpoint.target(),
                checkpoint.secondaryReceiverTarget(), checkpoint.receiverOverride(),
                checkpoint.useControlledReceiverForPrimary(),
                checkpoint.remaining(), resumeConfig, checkpoint.baselineStatus(), checkpoint.baselineLength(),
                checkpoint.completedCount(), checkpoint.totalCount(), true);
    }

    /** Retry the mutation that triggered a configured stop condition, then continue with the unsent tail. */
    public void retryStoppedCaseAndResume() {
        ResumeCheckpoint checkpoint = resumeCheckpoint;
        AtomizerPanel p = panel;
        if (p == null || checkpoint == null || checkpoint.stoppedMutation() == null) {
            feedback("No stopped mutation is available to retry.");
            return;
        }
        if (p.scopeOnly() && !checkpoint.original().isInScope()) {
            feedback("Retry blocked: stored request is outside Burp Target scope while scope-only sending is enabled.");
            return;
        }
        long cooldownMs = Math.max(0L, checkpoint.notBeforeEpochMs() - System.currentTimeMillis());
        if (cooldownMs > 0 && p.respectRetryAfterSelected()) {
            feedback("Retry deferred by Retry-After cooldown; approximately " + ((cooldownMs + 999) / 1000) +
                    " seconds remain. Uncheck 'Respect Retry-After if continuing' to manage the cooldown manually.");
            return;
        }
        if (!matrixSending.compareAndSet(false, true)) {
            feedback("Retry not started: another mutation matrix is already running.");
            return;
        }
        MatrixRunConfig resumeConfig = withRetryAfterSetting(checkpoint.config(), p.respectRetryAfterSelected());
        cancelRequested.set(false);
        if (panel != null) panel.setRunActive(true);
        resumeCheckpoint = null;
        p.setResumeAvailable(false, 0, "");
        p.setRetryStopAvailable(false, "", "");

        List<MutationCase> retryList = new ArrayList<>();
        retryList.add(checkpoint.stoppedMutation());
        retryList.addAll(checkpoint.remaining());
        int completedBeforeRetry = Math.max(0, checkpoint.completedCount() - 1);

        feedback("Retrying stopped case " + checkpoint.stoppedMutation().id() + " and then continuing with " +
                checkpoint.remaining().size() + " unsent test" + (checkpoint.remaining().size() == 1 ? "" : "s") +
                ", ≥" + resumeConfig.delayMs() + " ms spacing, Retry-After enforcement=" +
                (resumeConfig.respectRetryAfter() ? "on" : "off") + ".");
        submitMatrixExecution(checkpoint.original(), checkpoint.canonical(), checkpoint.target(),
                checkpoint.secondaryReceiverTarget(), checkpoint.receiverOverride(),
                checkpoint.useControlledReceiverForPrimary(),
                List.copyOf(retryList), resumeConfig, checkpoint.baselineStatus(), checkpoint.baselineLength(),
                completedBeforeRetry, checkpoint.totalCount(), true);
    }

    private static MatrixRunConfig withRetryAfterSetting(MatrixRunConfig config, boolean respectRetryAfter) {
        if (config == null || config.respectRetryAfter() == respectRetryAfter) return config;
        return new MatrixRunConfig(
                config.delayMs(),
                config.collaboratorCollectionWindowMs(),
                config.maxTests(),
                config.stopStatusCodes(),
                config.stopResponseText(),
                respectRetryAfter,
                config.fallbackRateLimitDelayMs(),
                config.deliverySentinelsEnabled(),
                config.deliverySentinelEvery());
    }

    private String describeStopCodes(MatrixRunConfig config) {
        java.util.Set<Integer> codes = parseStatusCodes(config.stopStatusCodes());
        return codes.isEmpty() ? "none" : codes.toString();
    }

    private String describeStopText(MatrixRunConfig config) {
        List<String> patterns = parseStopResponseText(config.stopResponseText());
        if (patterns.isEmpty()) return "none";
        if (patterns.size() == 1) return "'" + abbreviate(patterns.get(0), 60) + "'";
        return patterns.size() + " patterns";
    }

    private void submitMatrixExecution(HttpRequest original, String canonical, EmailCandidate target,
                                       EmailCandidate secondaryReceiverTarget, String receiverOverride,
                                       boolean useControlledReceiverForPrimary,
                                       List<MutationCase> runList, MatrixRunConfig config,
                                       String initialBaselineStatus, int initialBaselineLength,
                                       int completedBefore, int totalCount, boolean resumed) {
        final java.util.Set<Integer> stopCodes = parseStatusCodes(config.stopStatusCodes());
        final List<String> stopTexts = parseStopResponseText(config.stopResponseText());
        matrixExecutor.submit(() -> {
            int sent = 0;
            int skipped = 0;
            int nextIndex = 0;
            String baselineStatus = initialBaselineStatus;
            int baselineLength = initialBaselineLength;
            String stopReason = "";
            MutationCase stoppedMutation = null;
            long stopNotBeforeEpochMs = 0L;
            try {
                for (int i = 0; i < runList.size(); i++) {
                    if (cancelRequested.get()) {
                        stopReason = "cancelled";
                        nextIndex = i;
                        break;
                    }
                    MutationCase mutation = runList.get(i);
                    long requestStartedNanos = System.nanoTime();
                    try {
                        boolean deliverySentinel = "Delivery sentinel".equals(mutation.family());
                        RenderedMutation rendered = render(mutation, canonical, deliverySentinel, receiverOverride,
                                useControlledReceiverForPrimary && !deliverySentinel);
                        SecondaryReceiver secondary = secondaryReceiverTarget == null || deliverySentinel
                                ? null
                                : createSecondaryReceiver(rendered.correlationId(), receiverOverride);

                        // Generators that target a specific character/sequence return the canonical address when
                        // that source is absent from the first label. Skip those STANDARD no-op cases rather than
                        // sending a fake baseline or mutating the persistent Collaborator suffix. JSON wire syntax
                        // probes are allowed to keep the same logical email because their transport is the mutation.
                        if (!deliverySentinel &&
                                mutation.wireMode() == MutationCase.WireMode.STANDARD &&
                                !"BASE-001".equals(mutation.id()) &&
                                rendered.email().equals(canonical) &&
                                secondaryReceiverTarget == null) {
                            skipped++;
                            nextIndex = i + 1;
                            feedback("Matrix case " + mutation.id() + " skipped: source character/sequence is not present in the selected identity label.");
                            continue;
                        }

                        RequestMutator.Replacement repl;
                        // BASE-001 is intentionally byte-for-byte unchanged in single-occurrence mode.
                        // Keep it as the HTTP control now that RequestMutator reports true no-ops as changed=false.
                        if ("BASE-001".equals(mutation.id()) && secondaryReceiverTarget == null) {
                            repl = new RequestMutator.Replacement(original.toString(), true);
                        } else if (secondaryReceiverTarget != null && deliverySentinel) {
                            // In two-occurrence mode a delivery sentinel keeps the identity occurrence canonical
                            // and places the fresh direct Collaborator address only in the selected receiver occurrence.
                            repl = RequestMutator.replaceTwoTargeted(original.toString(),
                                    target, canonical, secondaryReceiverTarget, rendered.email());
                            rendered = new RenderedMutation(rendered.mutation(), rendered.correlationId(),
                                    rendered.collaboratorHost(), canonical, rendered.email(), rendered.collaboratorExpected());
                        } else if (secondaryReceiverTarget != null) {
                            repl = RequestMutator.replaceTwoTargeted(original.toString(),
                                    target, rendered.email(), secondaryReceiverTarget, secondary.email());
                        } else {
                            repl = target == null
                                    ? RequestMutator.replaceCanonical(original.toString(), canonical, rendered.email())
                                    : RequestMutator.replaceTargeted(original.toString(), target, rendered.email(), mutation, canonical);
                        }

                        if (!repl.changed()) {
                            skipped++;
                            nextIndex = i + 1;
                            if (!deliverySentinel && rendered.email().equals(canonical) &&
                                    mutation.wireMode() != MutationCase.WireMode.STANDARD) {
                                feedback("Matrix case " + mutation.id() + " skipped: mutation produced no wire-level change for the selected email (source character/sequence is absent or the requested transport escape is not applicable).");
                            } else {
                                feedback("Matrix case " + mutation.id() + " skipped: selected occurrence(s) could not be replaced safely.");
                            }
                            continue;
                        }

                        RequestWireBuilder.BuildResult wireBuild = RequestWireBuilder.buildVerified(original, repl.text());
                        HttpRequest modified = wireBuild.request();

                        AtomResult result = record(rendered, original.method(), original.url());
                        if (deliverySentinel && secondaryReceiverTarget != null) {
                            result.secondaryReceiverEmail = rendered.receiverEmail();
                            correlationRoles.put(rendered.correlationId(), "delivery sentinel receiver");
                        } else if (secondary != null) {
                            result.secondaryReceiverEmail = secondary.email();
                            bindSecondaryReceiver(secondary, result);
                        } else if (mutation.id().startsWith("DUAL-")) {
                            result.secondaryReceiverEmail = rendered.receiverEmail();
                        }
                        result.requestText = modified.toString();
                        result.requestBodyHex = wireBuild.actualBodyHex();
                        result.intendedRequestBodyHex = wireBuild.intendedBodyHex();
                        result.wireVerification = wireBuild.verified() ? "PASS" : "BLOCKED";
                        result.logicalEmailUtf8Hex = RequestWireBuilder.utf8Hex(rendered.email());
                        if (target != null) {
                            result.targetLocation = target.location() == null ? "" : target.location();
                            result.targetParameter = target.parameter() == null ? "" : target.parameter();
                            result.targetRawOffset = target.hasExactRawTarget() ? Integer.toString(target.rawOffset()) : "";
                        }
                        try { result.request = modified.copyToTempFile(); } catch (Throwable ignored) { result.request = modified; }

                        if (!wireBuild.verified()) {
                            result.signal = "BLOCKED: wire verification failed";
                            result.notes.set(wireBuild.detail());
                            skipped++;
                            nextIndex = i + 1;
                            fireResultsChanged();
                            feedback("Matrix case " + mutation.id() + " NOT SENT: " + wireBuild.detail());
                            continue;
                        }

                        // Bind persistent accounts only when their root is actually part of a value inserted into
                        // this request. Do not bind the original canonical or an unused receiver override: doing so
                        // falsely attributed Account A/B SMTP to unrelated rows in v0.3.11.
                        bindPersistentCollaboratorAccountsForRunResult(result,
                                rendered.email(),
                                result.secondaryReceiverEmail);

                        HttpRequestResponse rr = sendTrackedMatrixRequest(modified);
                        boolean autoRetriedNoResponse = false;
                        if (rr == null || rr.response() == null) {
                            result.notes.set("No HTTP response on first attempt; automatically retrying once");
                            fireResultsChanged();
                            feedback("No HTTP response for " + mutation.id() + "; retrying this mutation once.");
                            sleepInterruptibly(250);
                            rr = sendTrackedMatrixRequest(modified);
                            autoRetriedNoResponse = true;
                        }
                        if (rr != null && rr.response() != null) {
                            HttpResponse response = rr.response();
                            result.responseText = response.toString();
                            try { result.response = response.copyToTempFile(); } catch (Throwable ignored) { result.response = response; }
                            result.httpStatus = Short.toString(response.statusCode());
                            if (autoRetriedNoResponse) {
                                result.notes.set("Automatic retry after no response succeeded with HTTP " + response.statusCode());
                            }
                            int responseLength = response.bodyToString().getBytes(StandardCharsets.UTF_8).length;
                            result.responseLength = Integer.toString(responseLength);
                            if (mutation.id().equals("BASE-001")) {
                                baselineStatus = result.httpStatus;
                                baselineLength = responseLength;
                                result.differential = "baseline";
                            } else if (baselineStatus != null) {
                                result.differential = describeDifferential(baselineStatus, baselineLength, result.httpStatus, responseLength);
                            }

                            int responseCode = response.statusCode();
                            if (stopCodes.contains(responseCode)) {
                                result.signal = "RATE LIMITED / STOP CODE";
                                String retry = response.headerValue("Retry-After");
                                long retryMs = (responseCode == 429 && config.respectRetryAfter())
                                        ? parseRetryAfterMillis(retry, config.fallbackRateLimitDelayMs()) : 0L;
                                if (retryMs > 0) stopNotBeforeEpochMs = System.currentTimeMillis() + retryMs;
                                result.notes.set("Matrix stopped on HTTP " + responseCode +
                                        (retry == null ? "" : "; Retry-After=" + retry) +
                                        (retryMs > 0 ? "; resume-after-ms=" + retryMs : ""));
                                stopReason = "HTTP " + responseCode;
                                stoppedMutation = mutation;
                                fireResultsChanged();
                                collectCollaboratorForCurrentCase(result, rendered.collaboratorExpected(), mutation, secondary, config);
                                fireResultsChanged();
                                feedback("Stop code received: HTTP " + responseCode +
                                        (retry == null ? "" : " (Retry-After=" + retry + ")") +
                                        ". Matrix stopping; remaining tests can be resumed.");
                                sent++;
                                nextIndex = i + 1;
                                break;
                            }

                            String matchedStopText = firstMatchingStopText(response.bodyToString(), stopTexts);
                            if (matchedStopText != null) {
                                result.signal = "STOP TEXT MATCH";
                                result.notes.set("Matrix stopped because response body contained: " + matchedStopText);
                                stopReason = "response text '" + abbreviate(matchedStopText, 80) + "'";
                                stoppedMutation = mutation;
                                fireResultsChanged();
                                collectCollaboratorForCurrentCase(result, rendered.collaboratorExpected(), mutation, secondary, config);
                                fireResultsChanged();
                                feedback("Stop text matched for " + mutation.id() + ": " + abbreviate(matchedStopText, 120) +
                                        ". Matrix stopping; remaining tests can be resumed or this case can be retried.");
                                sent++;
                                nextIndex = i + 1;
                                break;
                            }

                            if (responseCode == 429 && config.respectRetryAfter()) {
                                String retry = response.headerValue("Retry-After");
                                long retryMs = parseRetryAfterMillis(retry, config.fallbackRateLimitDelayMs());
                                result.signal = "RATE LIMITED";
                                result.notes.set("HTTP 429; pausing " + retryMs + " ms before continuing");
                                fireResultsChanged();
                                feedback("HTTP 429 received; pausing " + retryMs + " ms before continuing.");
                                sleepInterruptibly(retryMs);
                            }
                            result.refreshSignal();
                        } else {
                            result.responseText = "[No HTTP response after one automatic retry]";
                            result.notes.set("No HTTP response after one automatic retry");
                        }
                        fireResultsChanged();
                        collectCollaboratorForCurrentCase(result, rendered.collaboratorExpected(), mutation, secondary, config);
                        assessDeliverySentinel(result, mutation);
                        fireResultsChanged();
                        sent++;
                        nextIndex = i + 1;
                        int overallProcessed = completedBefore + nextIndex;
                        String progressMessage = "Matrix running: " + overallProcessed + "/" + totalCount +
                                " processed; last=" + mutation.id() + " HTTP=" +
                                (result.httpStatus.isBlank() ? "?" : result.httpStatus);
                        SwingUtilities.invokeLater(() -> {
                            AtomizerPanel p = panel;
                            if (p != null) {
                                p.setStatus(progressMessage);
                                p.appendActivity(progressMessage);
                            }
                        });
                        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartedNanos);
                        long remainingDelayMs = Math.max(0L, (long) config.delayMs() - elapsedMs);
                        sleepInterruptibly(remainingDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        stopReason = "interrupted";
                        break;
                    } catch (Throwable t) {
                        nextIndex = i + 1;
                        feedbackError("Matrix case " + mutation.id() + " failed", t);
                    }
                }
            } finally {
                clearAllPersistentRunBindings();
                matrixSending.set(false);
                cancelRequested.set(false);
                AtomizerPanel currentPanel = panel;
                if (currentPanel != null) currentPanel.setRunActive(false);

                int remainingCount = Math.max(0, runList.size() - nextIndex);
                if (!stopReason.isBlank() && (remainingCount > 0 || stoppedMutation != null)) {
                    List<MutationCase> remaining = List.copyOf(runList.subList(nextIndex, runList.size()));
                    ResumeCheckpoint checkpoint = new ResumeCheckpoint(
                            original, canonical, target, secondaryReceiverTarget, receiverOverride,
                            useControlledReceiverForPrimary,
                            remaining, config, baselineStatus, baselineLength,
                            completedBefore + nextIndex, totalCount, stopReason, stoppedMutation,
                            stopNotBeforeEpochMs);
                    setResumeCheckpoint(checkpoint);
                } else {
                    clearResumeCheckpoint();
                }

                String suffix = stopReason.isBlank() ? "" : " Stopped: " + stopReason + ".";
                String resumeSuffix = remainingCount > 0 && !stopReason.isBlank()
                        ? " Remaining: " + remainingCount + ". Use Resume remaining tests when ready."
                        : "";
                String retrySuffix = stoppedMutation != null
                        ? " You can also retry " + stoppedMutation.id() + " (the stopped case) and then continue."
                        : "";
                feedback("Matrix " + (resumed ? "resume " : "") + "complete: sent " + sent + ", skipped " + skipped + "." +
                        suffix + resumeSuffix + retrySuffix + " Collaborator polling continues.");
            }
        });
    }


    private HttpRequestResponse sendTrackedMatrixRequest(HttpRequest request) {
        String marker = request == null ? "" : request.toString();
        if (!marker.isBlank()) activeMatrixRequests.add(marker);
        try {
            return api.http().sendRequest(request);
        } finally {
            if (!marker.isBlank()) activeMatrixRequests.remove(marker);
        }
    }

    private void collectCollaboratorForCurrentCase(AtomResult result, boolean primaryCollaboratorExpected,
                                                    MutationCase mutation,
                                                    SecondaryReceiver secondary, MatrixRunConfig config)
            throws InterruptedException {
        boolean persistentBound = resultHasPersistentRunBinding(result);
        try {
            CollaboratorPoller poller = collaboratorPoller;
            if (poller == null || collaboratorClient == null || config.collaboratorCollectionWindowMs() <= 0) return;
            boolean collaboratorExpected = primaryCollaboratorExpected ||
                    (secondary != null && secondary.correlationId() != null && !secondary.correlationId().isBlank()) ||
                    persistentBound;
            if (!collaboratorExpected) return;

            int before = result.interactionCount();
            int added = poller.collectForWindow(result, config.collaboratorCollectionWindowMs());
            int total = result.interactionCount();
            if (added > 0) {
                api.logging().logToOutput("Email Atomizer: collected " + added + " Collaborator interaction" +
                        (added == 1 ? "" : "s") + " for " + mutation.id() +
                        " during the post-request window; total=" + total + " (" + result.interactionSummary() + ").");
            } else if (before == 0) {
                api.logging().logToOutput("Email Atomizer: no Collaborator interaction arrived for " + mutation.id() +
                        " during the " + config.collaboratorCollectionWindowMs() + " ms collection window; background polling continues." +
                        (persistentBound ? " Delayed persistent-account events will remain in the Collaborator inbox without forced row attribution." : ""));
            }
        } finally {
            // Do not carry a shared persistent-account correlation into the next mutation. This makes Results-row
            // attribution conservative: prompt events are linked/exported; delayed ambiguous events remain inbox-only.
            if (persistentBound) clearPersistentRunBindingsForResult(result);
        }
    }

    private void setResumeCheckpoint(ResumeCheckpoint checkpoint) {
        resumeCheckpoint = checkpoint;
        AtomizerPanel p = panel;
        if (p != null) {
            p.setResumeAvailable(!checkpoint.remaining().isEmpty(), checkpoint.remaining().size(), checkpoint.stopReason());
            p.setRetryStopAvailable(checkpoint.stoppedMutation() != null,
                    checkpoint.stoppedMutation() == null ? "" : checkpoint.stoppedMutation().id(), checkpoint.stopReason());
        }
    }

    private void clearResumeCheckpoint() {
        resumeCheckpoint = null;
        AtomizerPanel p = panel;
        if (p != null) {
            p.setResumeAvailable(false, 0, "");
            p.setRetryStopAvailable(false, "", "");
        }
    }

    public void cancelMatrix() {
        if (!matrixSending.get()) {
            feedback("No matrix is currently running.");
            return;
        }
        cancelRequested.set(true);
        feedback("Cancelling matrix after the current request...");
    }

    static List<MutationCase> withDeliverySentinels(List<MutationCase> runList, MatrixRunConfig config) {
        if (!config.deliverySentinelsEnabled() || runList == null || runList.isEmpty()) {
            return runList == null ? List.of() : new ArrayList<>(runList);
        }

        ArrayList<MutationCase> out = new ArrayList<>();
        int startIndex = 0;
        if (!runList.isEmpty() && runList.get(0).id().equals("BASE-001")) {
            out.add(runList.get(0));
            startIndex = 1;
        }

        out.add(deliverySentinel("SENTINEL-START", "Delivery sentinel — start"));
        int mutationTestsSinceSentinel = 0;
        int midNumber = 1;
        int interval = Math.max(0, config.deliverySentinelEvery());

        for (int i = startIndex; i < runList.size(); i++) {
            MutationCase mutation = runList.get(i);
            out.add(mutation);
            mutationTestsSinceSentinel++;

            boolean hasMoreMutationTests = i < runList.size() - 1;
            if (interval > 0 && mutationTestsSinceSentinel >= interval && hasMoreMutationTests) {
                out.add(deliverySentinel(String.format(Locale.ROOT, "SENTINEL-%03d", midNumber++),
                        "Delivery sentinel — periodic"));
                mutationTestsSinceSentinel = 0;
            }
        }

        out.add(deliverySentinel("SENTINEL-FINAL", "Delivery sentinel — final"));
        return out;
    }

    private static MutationCase deliverySentinel(String id, String label) {
        return new MutationCase(id, "Delivery sentinel", label,
                "Direct Collaborator delivery-path control inserted by the matrix runner.",
                true, c -> "atom@" + c.collaboratorHost());
    }

    private void assessDeliverySentinel(AtomResult result, MutationCase mutation) {
        if (result == null || mutation == null || !"Delivery sentinel".equals(mutation.family())) return;
        if (!result.smtpRecipient.isBlank()) {
            result.signal = "SENTINEL OK: SMTP observed";
            appendNote(result, "Delivery sentinel reached SMTP");
        } else if (!result.collaboratorEvidence.isEmpty()) {
            result.signal = "SENTINEL PARTIAL: " + result.interactionSummary();
            appendNote(result, "Delivery sentinel produced OAST but no SMTP during collection window");
        } else {
            result.signal = "SENTINEL WARNING: no OAST in window";
            appendNote(result, "Delivery sentinel produced no Collaborator interaction during collection window; later background polling may still update this result");
        }
    }

    private static void appendNote(AtomResult result, String value) {
        if (result == null || value == null || value.isBlank()) return;
        String existing = result.notes.get();
        if (existing == null || existing.isBlank()) result.notes.set(value);
        else if (!existing.contains(value)) result.notes.set(existing + "; " + value);
    }

    private List<MutationCase> normalizedRunList(List<MutationCase> selected) {
        Map<String, MutationCase> byId = new LinkedHashMap<>();
        MutationCase baseline = mutations.stream().filter(m -> m.id().equals("BASE-001")).findFirst().orElse(null);
        if (baseline != null) byId.put(baseline.id(), baseline);
        for (MutationCase mutation : selected) {
            if (!mutation.id().equals("BASE-001")) byId.putIfAbsent(mutation.id(), mutation);
        }
        return new ArrayList<>(byId.values());
    }

    private boolean targetAvailable(String requestText, EmailCandidate target) {
        if (target == null) return false;
        if (!target.hasExactRawTarget()) return requestContainsCanonical(requestText, target.email());
        int start = target.rawOffset();
        String token = target.rawToken();
        return start >= 0 && start + token.length() <= requestText.length() &&
                requestText.regionMatches(start, token, 0, token.length());
    }

    private static boolean sameOccurrence(EmailCandidate a, EmailCandidate b) {
        if (a == null || b == null) return false;
        if (a.hasExactRawTarget() && b.hasExactRawTarget()) return a.rawOffset() == b.rawOffset();
        return a == b || (a.email().equalsIgnoreCase(b.email()) &&
                java.util.Objects.equals(a.location(), b.location()) &&
                java.util.Objects.equals(a.parameter(), b.parameter()));
    }

    public boolean requestContainsCanonical(String requestText, String canonical) {
        return RequestMutator.replaceCanonical(requestText, canonical, "__EMAIL_ATOMIZER_SENTINEL__").changed();
    }

    private static String describeDifferential(String baseStatus, int baseLength, String status, int length) {
        List<String> changes = new ArrayList<>();
        if (!baseStatus.equals(status)) changes.add("HTTP " + baseStatus + "→" + status);
        int delta = length - baseLength;
        if (delta != 0) changes.add("len " + (delta > 0 ? "+" : "") + delta);
        return changes.isEmpty() ? "same" : String.join("; ", changes);
    }

    static List<String> parseStopResponseText(String value) {
        ArrayList<String> out = new ArrayList<>();
        if (value == null || value.isBlank()) return out;
        for (String line : value.split("\\R")) {
            String pattern = line.trim();
            if (!pattern.isBlank()) out.add(pattern);
        }
        return out;
    }

    static String firstMatchingStopText(String responseBody, List<String> patterns) {
        if (responseBody == null || responseBody.isEmpty() || patterns == null || patterns.isEmpty()) return null;
        String haystack = responseBody.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            if (haystack.contains(pattern.toLowerCase(Locale.ROOT))) return pattern;
        }
        return null;
    }

    private static String abbreviate(String value, int max) {
        if (value == null) return "";
        String oneLine = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (oneLine.length() <= max) return oneLine;
        return oneLine.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static java.util.Set<Integer> parseStatusCodes(String value) {
        java.util.LinkedHashSet<Integer> out = new java.util.LinkedHashSet<>();
        if (value == null || value.isBlank()) return out;
        for (String part : value.split("[,\\s]+")) {
            if (part.isBlank()) continue;
            try {
                int code = Integer.parseInt(part.trim());
                if (code >= 100 && code <= 599) out.add(code);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static long parseRetryAfterMillis(String value, int fallbackMs) {
        if (value == null || value.isBlank()) return Math.max(0, fallbackMs);
        try {
            long seconds = Long.parseLong(value.trim());
            return Math.max(0, seconds * 1000L);
        } catch (NumberFormatException ignored) {
        }
        try {
            Instant until = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Math.max(0, until.toEpochMilli() - Instant.now().toEpochMilli());
        } catch (DateTimeParseException ignored) {
            return Math.max(0, fallbackMs);
        }
    }

    private static void sleepInterruptibly(long millis) throws InterruptedException {
        if (millis > 0) Thread.sleep(millis);
    }

    private static String discoveryKey(String method, String host, String path, String operation, String slot, EmailCandidate c) {
        // Distinct GraphQL/RPC operations commonly share one HTTP path. Include the logical operation
        // so SendTempPassword and LOGIN_MUTATION do not overwrite one another in passive discovery.
        // The slot is structural (location/parameter/representation + ordinal), not an absolute raw
        // byte offset, so changing cookies/header lengths does not create fake new discoveries.
        return method + "|" + host + "|" + path + "|" + (operation == null ? "" : operation) + "|" +
                slot + "|" + c.email().toLowerCase(Locale.ROOT);
    }

    static String discoverySlot(List<EmailCandidate> candidates, int index) {
        if (candidates == null || index < 0 || index >= candidates.size()) return "unknown";
        EmailCandidate current = candidates.get(index);
        int ordinal = 1;
        for (int i = 0; i < index; i++) {
            EmailCandidate prior = candidates.get(i);
            if (java.util.Objects.equals(prior.location(), current.location()) &&
                    java.util.Objects.equals(prior.parameter(), current.parameter()) &&
                    java.util.Objects.equals(prior.representation(), current.representation())) {
                ordinal++;
            }
        }
        return current.location() + "|" + current.parameter() + "|" + current.representation() + "|" + ordinal;
    }

    private static String hostOf(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h;
        } catch (Throwable t) {
            return "";
        }
    }

    private static String pathOf(String url) {
        try {
            URI uri = URI.create(url);
            String p = uri.getPath();
            return p == null || p.isBlank() ? "/" : p;
        } catch (Throwable t) {
            return url;
        }
    }

    private interface StringSupplier { String get() throws Throwable; }
    private static String safe(StringSupplier supplier, String fallback) {
        try { return supplier.get(); } catch (Throwable t) { return fallback; }
    }

    public void shutdown() {
        cancelRequested.set(true);
        matrixExecutor.shutdownNow();
    }

    private record ResumeCheckpoint(HttpRequest original, String canonical, EmailCandidate target,
                                    EmailCandidate secondaryReceiverTarget, String receiverOverride,
                                    boolean useControlledReceiverForPrimary,
                                    List<MutationCase> remaining, MatrixRunConfig config,
                                    String baselineStatus, int baselineLength,
                                    int completedCount, int totalCount, String stopReason,
                                    MutationCase stoppedMutation, long notBeforeEpochMs) {}

    public record StandaloneCollaboratorTarget(String correlationId, String source, String label,
                                               String generatedAddress, String collaboratorRoot) {}

    private record SecondaryReceiver(String correlationId, String collaboratorHost, String email) {}

    public record RenderedMutation(MutationCase mutation, String correlationId, String collaboratorHost,
                                   String email, String receiverEmail, boolean collaboratorExpected) {}
}
