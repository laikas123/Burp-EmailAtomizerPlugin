package com.emailatomizer;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.http.message.HttpRequestResponse;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CollaboratorPoller {
    // A bare leading C/S is NOT a transcript marker: it would eat legitimate SMTP DATA lines such
    // as Content-Type, Subject, color, or cursor. Single-letter markers are accepted only as C:/S:.
    private static final Pattern RCPT = Pattern.compile(
            "(?im)^(?:\\s*(?:(?:CLIENT\\s*:?)|(?:C\\s*:)|>>?|-->)\\s*)?RCPT\\s+TO:\\s*<?([^>\\r\\n]+)>?");
    private static final Pattern DATA = Pattern.compile(
            "(?i)^(?:\\s*(?:(?:CLIENT\\s*:?)|(?:C\\s*:)|>>?|-->)\\s*)?DATA\\s*$");
    private static final Pattern SERVER_PREFIX = Pattern.compile(
            "(?i)^\\s*(?:(?:SERVER\\s*:?)|(?:S\\s*:)|<<|<--)\\s*(.*)$");
    private static final Pattern CLIENT_PREFIX = Pattern.compile(
            "(?i)^\\s*(?:(?:CLIENT\\s*:?)|(?:C\\s*:)|>>|-->)\\s?(.*)$");

    private final MontoyaApi api;
    private final AtomizerState state;
    /** Event-level fingerprints. Interaction.id() identifies a Collaborator payload and may be shared by DNS/SMTP events. */
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final Object pollLock = new Object();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "email-atomizer-collaborator");
        t.setDaemon(true);
        return t;
    });

    public CollaboratorPoller(MontoyaApi api, AtomizerState state) {
        this.api = api;
        this.state = state;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::poll, 2, 5, TimeUnit.SECONDS);
    }

    public void poll() {
        synchronized (pollLock) {
            CollaboratorClient client = state.collaboratorClient();
            if (client == null) return;
            List<Interaction> interactions;
            try {
                interactions = client.getAllInteractions();
            } catch (Throwable t) {
                api.logging().logToError("Email Atomizer Collaborator poll failed while retrieving interactions: " + describeThrowable(t));
                return;
            }

            for (Interaction interaction : interactions) {
                processInteraction(interaction);
            }
        }
    }

    /**
     * Actively poll for a short post-request collection window. Background polling still continues later,
     * so delayed SMTP interactions are not lost if they arrive after the window.
     */
    public int collectForWindow(AtomResult result, int windowMs) throws InterruptedException {
        if (result == null || windowMs <= 0 || state.collaboratorClient() == null) return 0;
        int before = result.collaboratorEvidence.size();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(windowMs);
        do {
            poll();
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) break;
            long sleepMs = Math.min(250L, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            Thread.sleep(sleepMs);
        } while (true);
        poll();
        return Math.max(0, result.collaboratorEvidence.size() - before);
    }

    private void processInteraction(Interaction interaction) {
        String iid = safeString(() -> interaction.id().toString(), "unknown-interaction");
        String custom = safeString(() -> interaction.customData().orElse(""), "");
        AtomResult result = state.resultForCorrelation(custom);
        AtomizerState.StandaloneCollaboratorTarget standalone = state.standaloneCollaboratorTarget(custom);
        AtomResult persistentRunResult = result == null ? state.resultForPersistentRunCorrelation(custom) : null;
        AtomResult targetResult = result != null ? result : persistentRunResult;
        // A matrix result can briefly be absent if Collaborator answers extremely quickly. Do not mark it
        // seen unless the correlation is already registered as a persistent account / Mutation Strings target
        // or is currently linked to a matrix row through a persistent test-account root.
        if (targetResult == null && standalone == null) return;

        String type = safeString(() -> interaction.type().toString(), "UNKNOWN");
        String sourceRole;
        if (result != null) {
            sourceRole = state.roleForCorrelation(custom);
        } else if (persistentRunResult != null && standalone != null) {
            sourceRole = standalone.source() + " / linked to run " + persistentRunResult.mutationId;
        } else if (persistentRunResult != null) {
            sourceRole = "persistent account / linked to run " + persistentRunResult.mutationId;
        } else {
            sourceRole = standalone.source();
        }
        String protocol = "";
        String rawDetails = "";
        String smtpRecipient = "";
        String smtpMessage = "";
        String smtpBody = "";
        String detailError = "";

        try {
            if (interaction.smtpDetails().isPresent()) {
                var smtp = interaction.smtpDetails().get();
                protocol = safeString(() -> smtp.protocol().toString(), "SMTP");
                String conversation = safeString(smtp::conversation, "");
                smtpRecipient = extractRecipients(conversation);
                smtpMessage = extractSmtpMessage(conversation);
                smtpBody = extractMessageBody(smtpMessage);
                rawDetails = conversation;
            } else if (interaction.httpDetails().isPresent()) {
                var http = interaction.httpDetails().get();
                protocol = safeString(() -> http.protocol().toString(), "HTTP");
                HttpRequestResponse rr = http.requestResponse();
                StringBuilder raw = new StringBuilder();
                raw.append("--- Collaborator HTTP request ---\n");
                if (rr != null && rr.request() != null) raw.append(rr.request()).append('\n');
                else raw.append("[No request captured]\n");
                raw.append("\n--- Collaborator HTTP response ---\n");
                if (rr != null && rr.response() != null) raw.append(rr.response()).append('\n');
                else raw.append("[No response captured]\n");
                rawDetails = raw.toString();
            } else if (interaction.dnsDetails().isPresent()) {
                var dns = interaction.dnsDetails().get();
                protocol = safeString(() -> dns.queryType().toString(), "DNS");
                byte[] queryBytes = safeValue(() -> dns.query().getBytes(), new byte[0]);
                String queryHex = queryBytes.length == 0 ? "[DNS query unavailable]" : HexFormat.of().formatHex(queryBytes);
                String queryEscaped = queryBytes.length == 0 ? "" : escapeBinary(queryBytes);
                rawDetails = "DNS query type: " + protocol +
                        "\n\nRaw DNS query (hex):\n" + queryHex +
                        (queryEscaped.isBlank() ? "" : "\n\nRaw DNS query (escaped ASCII):\n" + queryEscaped);
            } else {
                rawDetails = "[Interaction type reported as " + type + " but no protocol-specific detail object was available.]";
            }
        } catch (Throwable t) {
            detailError = describeThrowable(t);
            rawDetails = "[Collaborator interaction was received, but protocol detail extraction failed: " + detailError + "]";
            api.logging().logToError("Email Atomizer Collaborator detail extraction failed for " + custom +
                    " / " + type + ": " + detailError);
        }

        ZonedDateTime timestamp = safeValue(interaction::timeStamp, null);
        String clientIp = safeString(() -> interaction.clientIp() == null ? "" : interaction.clientIp().getHostAddress(), "");
        int clientPort = safeInt(interaction::clientPort, -1);

        String eventKey = eventFingerprint(iid, custom, type, timestamp, clientIp, clientPort, protocol, rawDetails);
        if (seen.contains(eventKey)) return;

        // Persist the evidence BEFORE marking the interaction as seen. This avoids a state where
        // an OAST event is acknowledged but its raw details are lost after an extraction error.
        CollaboratorEvidence evidence = new CollaboratorEvidence(
                iid,
                sourceRole,
                type,
                timestamp,
                clientIp,
                clientPort,
                protocol,
                smtpRecipient,
                smtpMessage,
                smtpBody,
                rawDetails);

        if (targetResult != null) {
            targetResult.interaction = appendEvent(targetResult.interaction, type);
            targetResult.collaboratorEvidence.add(evidence);

            if (!smtpRecipient.isBlank()) targetResult.smtpRecipient = appendUnique(targetResult.smtpRecipient, smtpRecipient);
            if (!smtpMessage.isBlank()) targetResult.smtpMessage = smtpMessage;
            if (!smtpBody.isBlank()) targetResult.smtpBody = smtpBody;

            if (!smtpRecipient.isBlank()) {
                appendResultNote(targetResult,
                        (persistentRunResult != null ? "Persistent-account " : "") +
                        "SMTP interaction" + (clientIp.isBlank() ? "" : " from " + clientIp) + "; RCPT=" + smtpRecipient);
            } else if (!detailError.isBlank()) {
                appendResultNote(targetResult, "Collaborator " + type + " received; detail extraction error: " + detailError);
            }

            targetResult.refreshSignal();
            state.fireResultsChanged();
        }

        // Persistent accounts and Mutation Strings remain visible in the standalone inbox even when a
        // persistent account event was also linked into the current Results row for CSV export.
        if (standalone != null) {
            state.recordStandaloneCollaboratorEvidence(custom, evidence);
        }

        seen.add(eventKey);
        api.logging().logToOutput("Email Atomizer interaction: " + custom + " / " + sourceRole + " / " + type +
                (smtpRecipient.isBlank() ? "" : " / RCPT=" + smtpRecipient));
    }

    static String extractRecipients(String conversation) {
        if (conversation == null || conversation.isBlank()) return "";
        Matcher m = RCPT.matcher(conversation);
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        while (m.find()) {
            String value = m.group(1).trim();
            int space = value.indexOf(' ');
            if (space > 0) value = value.substring(0, space);
            if (!value.isBlank()) recipients.add(value);
        }
        return String.join(", ", recipients);
    }

    /** Best-effort extraction of the client-supplied SMTP DATA payload. Raw conversation is always preserved separately. */
    static String extractSmtpMessage(String conversation) {
        if (conversation == null || conversation.isBlank()) return "";
        String[] lines = conversation.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean sawData = false;
        boolean collecting = false;
        List<String> message = new ArrayList<>();

        for (String originalLine : lines) {
            String line = originalLine;
            if (!sawData) {
                if (DATA.matcher(line.trim()).matches()) sawData = true;
                continue;
            }

            Matcher server = SERVER_PREFIX.matcher(line);
            if (server.matches()) {
                String payload = server.group(1).trim();
                if (!collecting && payload.startsWith("354")) collecting = true;
                continue;
            }

            Matcher client = CLIENT_PREFIX.matcher(line);
            if (client.matches()) line = client.group(1);

            if (!collecting) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.matches("354(?:\\s|$).*") || trimmed.matches("\\d{3}(?:[-\\s]).*")) continue;
                collecting = true;
            }

            if (line.equals(".")) break;
            if (line.startsWith("..")) line = line.substring(1);
            message.add(line);
        }

        while (!message.isEmpty() && message.get(message.size() - 1).isEmpty()) message.remove(message.size() - 1);
        return String.join("\n", message).trim();
    }

    static String extractMessageBody(String message) {
        if (message == null || message.isBlank()) return "";
        String normalized = message.replace("\r\n", "\n").replace('\r', '\n');
        int split = normalized.indexOf("\n\n");
        return split >= 0 ? normalized.substring(split + 2).trim() : normalized.trim();
    }


    static String escapeBinary(byte[] data) {
        if (data == null || data.length == 0) return "";
        StringBuilder out = new StringBuilder(data.length * 2);
        for (byte b : data) {
            int v = b & 0xff;
            if (v >= 0x20 && v <= 0x7e && v != '\\') {
                out.append((char) v);
            } else if (v == '\\') {
                out.append("\\\\");
            } else {
                out.append(String.format("\\x%02X", v));
            }
        }
        return out.toString();
    }

    static String eventFingerprint(String iid, String custom, String type, ZonedDateTime timestamp,
                                   String clientIp, int clientPort, String protocol, String rawDetails) {
        String raw = rawDetails == null ? "" : rawDetails;
        return String.join("|",
                iid == null ? "" : iid,
                custom == null ? "" : custom,
                type == null ? "" : type,
                timestamp == null ? "" : timestamp.toInstant().toString(),
                clientIp == null ? "" : clientIp,
                Integer.toString(clientPort),
                protocol == null ? "" : protocol,
                Integer.toHexString(raw.hashCode()));
    }

    private static void appendResultNote(AtomResult result, String value) {
        if (result == null || value == null || value.isBlank()) return;
        result.notes.updateAndGet(existing -> {
            if (existing == null || existing.isBlank()) return value;
            if (existing.contains(value)) return existing;
            return existing + "; " + value;
        });
    }

    private static String appendEvent(String existing, String value) {
        if (value == null || value.isBlank()) return existing == null ? "" : existing;
        if (existing == null || existing.isBlank()) return value;
        return existing + "+" + value;
    }

    private static String appendUnique(String existing, String value) {
        if (value == null || value.isBlank()) return existing == null ? "" : existing;
        if (existing == null || existing.isBlank()) return value;
        for (String part : existing.split("\\s*\\+\\s*|\\s*,\\s*")) {
            if (part.equalsIgnoreCase(value)) return existing;
        }
        return existing + "+" + value;
    }

    private interface ThrowingSupplier<T> { T get() throws Throwable; }
    private interface ThrowingIntSupplier { int get() throws Throwable; }

    private static <T> T safeValue(ThrowingSupplier<T> supplier, T fallback) {
        try { return supplier.get(); } catch (Throwable t) { return fallback; }
    }

    private static String safeString(ThrowingSupplier<String> supplier, String fallback) {
        String value = safeValue(supplier, fallback);
        return value == null ? fallback : value;
    }

    private static int safeInt(ThrowingIntSupplier supplier, int fallback) {
        try { return supplier.get(); } catch (Throwable t) { return fallback; }
    }

    private static String describeThrowable(Throwable t) {
        if (t == null) return "unknown error";
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
