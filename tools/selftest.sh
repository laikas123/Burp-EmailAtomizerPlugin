#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cat > "$TMP/SelfTest.java" <<'JAVA'
package com.emailatomizer;
import java.util.*;
public class SelfTest {
  public static void main(String[] args) throws Exception {
    var ctx = new MutationContext("tester+1@example.com", "tester+1", "example.com", "atom123.oastify.com", "atom00000001", "receiver@atom123.oastify.com");
    var seen = new HashSet<String>();
    for (var m : MutationCatalog.all()) {
      if (!seen.add(m.id())) throw new AssertionError("duplicate mutation id: " + m.id());
      var value = m.generator().generate(ctx);
      if (value == null || value.isBlank()) throw new AssertionError("blank mutation: " + m.id());
    }

    var raw = "POST /signup HTTP/1.1\r\nHost: target.test\r\nContent-Type: application/x-www-form-urlencoded\r\n\r\nemail=tester%2B1%40example.com";
    var detected = EmailDetector.detect(raw);
    if (detected.size() != 1 || !detected.get(0).email().equals("tester+1@example.com"))
      throw new AssertionError("URL-encoded passive discovery failed: " + detected);
    var r = RequestMutator.replaceTargeted(raw, detected.get(0), "atom@atom123.oastify.com");
    if (!r.changed() || !r.text().contains("atom%40atom123.oastify.com"))
      throw new AssertionError("targeted encoded replacement failed");

    var duplicate = "POST /x HTTP/1.1\r\nHost: target.test\r\nCookie: owner=tester@example.com\r\nContent-Type: application/json\r\n\r\n{\"email\":\"tester@example.com\"}";
    var candidates = EmailDetector.detect(duplicate);
    if (candidates.size() != 2) throw new AssertionError("expected header+body discoveries: " + candidates);
    var body = candidates.stream().filter(c -> c.location().contains("body")).findFirst().orElseThrow();
    var targeted = RequestMutator.replaceTargeted(duplicate, body, "mutant@oast.test");
    if (!targeted.text().contains("Cookie: owner=tester@example.com")) throw new AssertionError("targeted replacement changed cookie");
    if (!targeted.text().contains("{\"email\":\"mutant@oast.test\"}")) throw new AssertionError("targeted replacement missed body");



    var graphql = "POST /graphql?operationName=SendVerificationCode HTTP/2\r\nHost: www.instacart.com\r\nContent-Type: application/json\r\n\r\n" +
      "{\"operationName\":\"SendVerificationCode\",\"variables\":{\"identifier\":\"laikas+202@wearehackerone.com\",\"identifier_type\":\"email\"}}";
    var gqlCandidates = EmailDetector.detect(graphql);
    if (gqlCandidates.stream().noneMatch(c -> c.email().equals("laikas+202@wearehackerone.com") && c.parameter().equals("identifier")))
      throw new AssertionError("GraphQL identifier passive discovery failed: " + gqlCandidates);
    if (!RequestClassifier.operation("https://www.instacart.com/graphql?operationName=SendVerificationCode", graphql).equals("SendVerificationCode"))
      throw new AssertionError("GraphQL query operation classification failed");

    var anfTemp = "POST /api/bff/customer HTTP/2\r\nHost: www.abercrombie.com\r\nX-Operation-Name: SendTempPassword\r\nContent-Type: application/json\r\n\r\n" +
      "[{\"operationName\":\"SendTempPassword\",\"variables\":{\"email\":\"same@example.com\"}}]";
    var anfLogin = "POST /api/bff/customer HTTP/2\r\nHost: www.abercrombie.com\r\nX-Operation-Name: LOGIN_MUTATION\r\nContent-Type: application/json\r\n\r\n" +
      "[{\"operationName\":\"LOGIN_MUTATION\",\"variables\":{\"email\":\"same@example.com\"}}]";
    if (RequestClassifier.operation("https://www.abercrombie.com/api/bff/customer", anfTemp).equals(
        RequestClassifier.operation("https://www.abercrombie.com/api/bff/customer", anfLogin)))
      throw new AssertionError("distinct GraphQL operations collapsed to one passive identity");

    var repeatedFields = "POST /x HTTP/1.1\r\nHost: target.test\r\nContent-Type: application/json\r\n\r\n" +
      "{\"emails\":[\"same@example.com\",\"same@example.com\"]}";
    var repeatedCandidates = EmailDetector.detect(repeatedFields);
    if (repeatedCandidates.size() != 2)
      throw new AssertionError("same email in two request occurrences was collapsed: " + repeatedCandidates);
    if (AtomizerState.discoverySlot(repeatedCandidates, 0).equals(AtomizerState.discoverySlot(repeatedCandidates, 1)))
      throw new AssertionError("repeated request occurrences received the same structural slot");

    var jsonQuoted = "POST /x HTTP/1.1\r\nHost: target.test\r\nContent-Type: application/json\r\n\r\n{\"email\":\"tester@example.com\"}";
    var jsonCandidate = EmailDetector.detect(jsonQuoted).stream().filter(c -> c.parameter().equals("email")).findFirst().orElseThrow();
    var escaped = RequestMutator.replaceTargeted(jsonQuoted, jsonCandidate, "\"atom\\\\@oast.test\"@example.com");
    if (!escaped.changed() || !escaped.text().contains("\\\"atom\\\\\\\\@oast.test\\\"@example.com"))
      throw new AssertionError("JSON-safe mutation escaping failed: " + escaped.text());
    var nulEscaped = RequestMutator.replaceTargeted(jsonQuoted, jsonCandidate, "atom\u0000@oast.test@example.com");
    if (!nulEscaped.changed() || nulEscaped.text().indexOf('\u0000') >= 0 || !nulEscaped.text().contains("atom\\u0000@oast.test@example.com"))
      throw new AssertionError("JSON NUL transport escaping failed: " + nulEscaped.text());

    var unicode = "POST /x HTTP/1.1\r\nHost: target.test\r\n\r\n{\"email\":\"tester\\u0040example.com\"}";
    if (EmailDetector.detect(unicode).stream().noneMatch(c -> c.email().equals("tester@example.com")))
      throw new AssertionError("JSON unicode escaped discovery failed");

    var two = "POST /x HTTP/1.1\r\nHost: target.test\r\nContent-Type: application/json\r\n\r\n{\"identity\":\"id@example.com\",\"receiver\":\"old@example.net\"}";
    var twoc = EmailDetector.detect(two);
    var id = twoc.stream().filter(c -> c.email().equals("id@example.com")).findFirst().orElseThrow();
    var recv = twoc.stream().filter(c -> c.email().equals("old@example.net")).findFirst().orElseThrow();
    var both = RequestMutator.replaceTwoTargeted(two, id, "id@example.com", recv, "atom@abc.oastify.com");
    if (!both.changed() || !both.text().contains("\"receiver\":\"atom@abc.oastify.com\""))
      throw new AssertionError("two-occurrence role replacement failed: " + both.text());

    var dual = MutationCatalog.all().stream().filter(m -> m.id().equals("DUAL-001")).findFirst().orElseThrow();
    if (!dual.generator().generate(ctx).contains("receiver@atom123.oastify.com"))
      throw new AssertionError("dual-address receiver not embedded");

    var manualState = new AtomizerState(null);
    var manualBase = MutationCatalog.all().stream().filter(m -> m.id().equals("BASE-002")).findFirst().orElseThrow();
    var manualOast = manualState.renderForMutationStrings(manualBase, "identity@example.com", false, "", "manual.oastify.com");
    if (!manualOast.email().equals("atom@manual.oastify.com"))
      throw new AssertionError("manual Mutation Strings Collaborator/fallback rendering failed: " + manualOast.email());
    var manualControlled = manualState.renderForMutationStrings(manualBase, "identity@example.com", true, "receiver@controlled.test", "manual.oastify.com");
    if (!manualControlled.email().equals("receiver@controlled.test"))
      throw new AssertionError("manual Mutation Strings controlled-receiver rendering failed: " + manualControlled.email());
    if (!RequestMutator.jsonEscape("a\"b\\c").equals("a\\\"b\\\\c"))
      throw new AssertionError("manual JSON copy/paste escaping failed");
    if (!RequestMutator.urlEncode("a+b@example.com").contains("%40"))
      throw new AssertionError("manual URL copy/paste encoding failed");

    var controlledCtx = new MutationContext("identity@example.com", "identity", "example.com", "unused.oastify.com", "atom00000099", "receiver+2@controlled.test");
    var directControlled = MutationCatalog.all().stream().filter(m -> m.id().equals("BASE-002")).findFirst().orElseThrow();
    var percentControlled = MutationCatalog.all().stream().filter(m -> m.id().equals("ROUTE-003")).findFirst().orElseThrow();
    var encodedControlled = MutationCatalog.all().stream().filter(m -> m.id().equals("EW-PROBE-001")).findFirst().orElseThrow();
    if (!directControlled.generator().generate(controlledCtx).equals("receiver+2@controlled.test"))
      throw new AssertionError("controlled receiver direct probe failed");
    if (!percentControlled.generator().generate(controlledCtx).equals("receiver+2%controlled.test@example.com"))
      throw new AssertionError("controlled receiver percent-routing probe failed");
    if (!encodedControlled.generator().generate(controlledCtx).endsWith("@controlled.test"))
      throw new AssertionError("controlled receiver encoded-word probe failed");

    var smtp = "220 collab\r\nEHLO mail\r\n250 ok\r\nRCPT TO:<atom@abc.oastify.com>\r\n250 ok\r\nDATA\r\n354 go\r\nSubject: hello\r\nTo: atom@abc.oastify.com\r\n\r\nOTP: 123456\r\n.\r\n250 queued";
    if (!CollaboratorPoller.extractRecipients(smtp).equals("atom@abc.oastify.com"))
      throw new AssertionError("SMTP RCPT parse failed");
    if (!CollaboratorPoller.extractMessageBody(CollaboratorPoller.extractSmtpMessage(smtp)).contains("OTP: 123456"))
      throw new AssertionError("SMTP DATA/body parse failed");

    var now = java.time.ZonedDateTime.now();
    String fpDns1 = CollaboratorPoller.eventFingerprint("samepayload", "atom00000001", "DNS", now, "1.2.3.4", 1111, "A", "query-one");
    String fpDns2 = CollaboratorPoller.eventFingerprint("samepayload", "atom00000001", "DNS", now.plusNanos(1), "1.2.3.4", 1112, "A", "query-two");
    String fpSmtp = CollaboratorPoller.eventFingerprint("samepayload", "atom00000001", "SMTP", now.plusNanos(2), "1.2.3.4", 2525, "SMTP", "conversation");
    if (fpDns1.equals(fpDns2) || fpDns1.equals(fpSmtp) || fpDns2.equals(fpSmtp))
      throw new AssertionError("event-level Collaborator fingerprint collapsed distinct interactions");

    var stopPatterns = AtomizerState.parseStopResponseText("tooManyAttempts\nblocked by policy");
    if (!"tooManyAttempts".equals(AtomizerState.firstMatchingStopText(
        "{\"data\":{\"sendVerificationCode\":{\"errorTypes\":[\"tooManyAttempts\"]}}}", stopPatterns)))
      throw new AssertionError("response-body stop-text matching failed");
    if (AtomizerState.firstMatchingStopText("{\"ok\":true}", stopPatterns) != null)
      throw new AssertionError("response-body stop-text false positive");

    var baseRun = java.util.List.of(
      MutationCatalog.all().stream().filter(m -> m.id().equals("BASE-001")).findFirst().orElseThrow(),
      MutationCatalog.all().stream().filter(m -> m.id().equals("BASE-002")).findFirst().orElseThrow(),
      MutationCatalog.all().stream().filter(m -> m.id().equals("RFC-001")).findFirst().orElseThrow(),
      MutationCatalog.all().stream().filter(m -> m.id().equals("RFC-002")).findFirst().orElseThrow(),
      MutationCatalog.all().stream().filter(m -> m.id().equals("RFC-003")).findFirst().orElseThrow(),
      MutationCatalog.all().stream().filter(m -> m.id().equals("RFC-004")).findFirst().orElseThrow()
    );
    var sentinelConfig = new MatrixRunConfig(1000, 3000, 0, "420,429", "tooManyAttempts", true, 10000, true, 2);
    var withSentinels = AtomizerState.withDeliverySentinels(baseRun, sentinelConfig);
    long sentinelCount = withSentinels.stream().filter(m -> m.family().equals("Delivery sentinel")).count();
    if (sentinelCount != 4 || !withSentinels.get(1).id().equals("SENTINEL-START") ||
        !withSentinels.get(withSentinels.size()-1).id().equals("SENTINEL-FINAL"))
      throw new AssertionError("delivery sentinel scheduling failed: " + withSentinels);

    // v0.3.8 regression (retained from v0.3.6): Collaborator Accounts must replace the constructor-time stale
    // "unavailable" state once a client exists, and generation controls must follow real state.
    var clientField = AtomizerState.class.getDeclaredField("collaboratorClient");
    clientField.setAccessible(true);
    burp.api.montoya.collaborator.CollaboratorClient fakeClient = new burp.api.montoya.collaborator.CollaboratorClient() {
      @Override public burp.api.montoya.collaborator.CollaboratorPayload generatePayload(String customData, burp.api.montoya.collaborator.PayloadOption... options) {
        return new burp.api.montoya.collaborator.CollaboratorPayload() { @Override public String toString() { return customData + ".example.oastify.com"; } };
      }
      @Override public java.util.List<burp.api.montoya.collaborator.Interaction> getAllInteractions() { return java.util.List.of(); }
    };
    clientField.set(manualState, fakeClient);
    var accountsPanel = new CollaboratorAccountsPanel(manualState, null, null);
    javax.swing.SwingUtilities.invokeAndWait(accountsPanel::refresh);
    javax.swing.SwingUtilities.invokeAndWait(() -> {});
    var connectionStatusField = CollaboratorAccountsPanel.class.getDeclaredField("connectionStatus");
    connectionStatusField.setAccessible(true);
    var reconnectButtonField = CollaboratorAccountsPanel.class.getDeclaredField("reconnectButton");
    reconnectButtonField.setAccessible(true);
    var generateAButtonField = CollaboratorAccountsPanel.class.getDeclaredField("generateAButton");
    generateAButtonField.setAccessible(true);
    var connectionStatus = (javax.swing.JLabel) connectionStatusField.get(accountsPanel);
    var reconnectButton = (javax.swing.JButton) reconnectButtonField.get(accountsPanel);
    var generateAButton = (javax.swing.JButton) generateAButtonField.get(accountsPanel);
    if (!connectionStatus.getText().startsWith("CONNECTED") || reconnectButton.isEnabled() || !generateAButton.isEnabled())
      throw new AssertionError("Collaborator Accounts connected UI state is stale: " + connectionStatus.getText());
    clientField.set(manualState, null);
    javax.swing.SwingUtilities.invokeAndWait(accountsPanel::refresh);
    javax.swing.SwingUtilities.invokeAndWait(() -> {});
    if (!connectionStatus.getText().equals("NOT CONNECTED") || !reconnectButton.isEnabled() || generateAButton.isEnabled())
      throw new AssertionError("Collaborator Accounts disconnected UI controls did not refresh: " + connectionStatus.getText());
    clientField.set(manualState, fakeClient);

    manualState.registerStandaloneCollaboratorTarget("acctatest", "Persistent account A", "test account A", "laikas@gmail.abc.oastify.com", "abc.oastify.com");
    var standaloneEvidence = new CollaboratorEvidence("iid-standalone", "Persistent account A", "SMTP", now, "5.6.7.8", 2525, "SMTP",
      "laikas@gmail.abc.oastify.com", "Subject: reset", "reset body", "smtp conversation");
    manualState.recordStandaloneCollaboratorEvidence("acctatest", standaloneEvidence);
    if (manualState.collaboratorInbox().size() != 1 ||
        !manualState.collaboratorInbox().get(0).smtpRecipient().equals("laikas@gmail.abc.oastify.com"))
      throw new AssertionError("standalone/persistent Collaborator inbox correlation failed");

    // v0.3.8 regression: a persistent Account B interaction must be dual-written into the active
    // matrix Result row (for normal Results CSV export) AND retained in the standalone inbox.
    manualState.clearCollaboratorInbox();
    var accountBField = AtomizerState.class.getDeclaredField("collaboratorAccountB");
    accountBField.setAccessible(true);
    var persistentB = new PersistentCollaboratorAccount("B", "acctbtest", "broot.oastify.com",
      "laikas", "yahoo", "laikas@yahoo.broot.oastify.com", java.time.Instant.now());
    accountBField.set(manualState, persistentB);
    manualState.registerStandaloneCollaboratorTarget("acctbtest", "Persistent account B", "test account B",
      persistentB.email(), persistentB.collaboratorRoot());
    var linkedResult = new AtomResult("atom-linked", MutationCatalog.all().stream().filter(m -> m.id().equals("NORM-001")).findFirst().orElseThrow(),
      "POST", "https://example.test/reset", "laikas@yᵃhoo.broot.oastify.com", "mutation.oastify.com");
    linkedResult.notes.set("HTTP 204 accepted");
    manualState.bindPersistentCollaboratorAccountsForRunResult(linkedResult,
      persistentB.email(), "laikas@yᵃhoo.broot.oastify.com");
    if (manualState.resultForPersistentRunCorrelation("acctbtest") != linkedResult ||
        !manualState.resultHasPersistentRunBinding(linkedResult))
      throw new AssertionError("persistent account did not bind to active matrix result");

    burp.api.montoya.logging.Logging fakeLogging = (burp.api.montoya.logging.Logging) java.lang.reflect.Proxy.newProxyInstance(
      SelfTest.class.getClassLoader(), new Class[]{burp.api.montoya.logging.Logging.class}, (proxy, method, argv) -> null);
    burp.api.montoya.MontoyaApi fakeApi = (burp.api.montoya.MontoyaApi) java.lang.reflect.Proxy.newProxyInstance(
      SelfTest.class.getClassLoader(), new Class[]{burp.api.montoya.MontoyaApi.class}, (proxy, method, argv) ->
        method.getName().equals("logging") ? fakeLogging : null);
    var linkedPoller = new CollaboratorPoller(fakeApi, manualState);
    burp.api.montoya.collaborator.Interaction linkedSmtp = new burp.api.montoya.collaborator.Interaction() {
      @Override public burp.api.montoya.collaborator.InteractionId id() {
        return new burp.api.montoya.collaborator.InteractionId() { @Override public String toString() { return "iid-linked"; } };
      }
      @Override public burp.api.montoya.collaborator.InteractionType type() { return burp.api.montoya.collaborator.InteractionType.SMTP; }
      @Override public java.time.ZonedDateTime timeStamp() { return now.plusSeconds(1); }
      @Override public java.net.InetAddress clientIp() { try { return java.net.InetAddress.getByName("54.240.7.49"); } catch (Exception e) { return null; } }
      @Override public int clientPort() { return 47399; }
      @Override public java.util.Optional<burp.api.montoya.collaborator.DnsDetails> dnsDetails() { return java.util.Optional.empty(); }
      @Override public java.util.Optional<burp.api.montoya.collaborator.HttpDetails> httpDetails() { return java.util.Optional.empty(); }
      @Override public java.util.Optional<burp.api.montoya.collaborator.SmtpDetails> smtpDetails() {
        return java.util.Optional.of(new burp.api.montoya.collaborator.SmtpDetails() {
          @Override public burp.api.montoya.collaborator.SmtpProtocol protocol() { return burp.api.montoya.collaborator.SmtpProtocol.SMTP; }
          @Override public String conversation() {
            return "220 collab\r\nRCPT TO:<laikas@xn--yhoo-5b4a.broot.oastify.com>\r\n250 ok\r\nDATA\r\n354 go\r\nSubject: reset\r\nTo: laikas@xn--yhoo-5b4a.broot.oastify.com\r\n\r\nreset token\r\n.\r\n250 queued";
          }
        });
      }
      @Override public java.util.Optional<String> customData() { return java.util.Optional.of("acctbtest"); }
    };
    var processInteraction = CollaboratorPoller.class.getDeclaredMethod("processInteraction", burp.api.montoya.collaborator.Interaction.class);
    processInteraction.setAccessible(true);
    processInteraction.invoke(linkedPoller, linkedSmtp);
    if (linkedResult.interactionCount() != 1 ||
        !linkedResult.smtpRecipient.contains("xn--yhoo-5b4a.broot.oastify.com") ||
        !linkedResult.notes.get().contains("HTTP 204 accepted") ||
        !linkedResult.notes.get().contains("Persistent-account SMTP interaction"))
      throw new AssertionError("persistent SMTP evidence was not attached to Results without overwriting existing notes");
    if (manualState.collaboratorInbox().size() != 1 ||
        !manualState.collaboratorInbox().get(0).smtpRecipient().contains("xn--yhoo-5b4a.broot.oastify.com"))
      throw new AssertionError("persistent SMTP evidence was not retained in Collaborator inbox");
    manualState.clearPersistentRunBindingsForResult(linkedResult);
    if (manualState.resultForPersistentRunCorrelation("acctbtest") != null)
      throw new AssertionError("persistent row binding survived beyond its attribution window");
    linkedPoller.shutdown();

    var aggregate = new AtomResult("atom00000001", MutationCatalog.all().get(0), "POST", "https://example.test/", "x@example.test", "abc.oastify.com");
    aggregate.collaboratorEvidence.add(new CollaboratorEvidence("samepayload", "mutation payload", "DNS", now, "1.2.3.4", 1111, "A", "", "", "", "q1"));
    aggregate.collaboratorEvidence.add(new CollaboratorEvidence("samepayload", "mutation payload", "DNS", now.plusNanos(1), "1.2.3.4", 1112, "A", "", "", "", "q2"));
    aggregate.collaboratorEvidence.add(new CollaboratorEvidence("samepayload", "mutation payload", "SMTP", now.plusNanos(2), "1.2.3.4", 2525, "SMTP", "atom@abc.oastify.com", "Subject: x", "body", "smtp"));
    if (!aggregate.interactionSummary().equals("DNS×2 + SMTP")) throw new AssertionError("aggregate OAST summary failed: " + aggregate.interactionSummary());
    if (!aggregate.interactionSequence().equals("DNS → DNS → SMTP")) throw new AssertionError("aggregate OAST sequence failed: " + aggregate.interactionSequence());


    // v0.3.12: exact-target no-op must not masquerade as a successful mutation.
    var noOpReq = "POST /x HTTP/1.1\r\nHost: target.test\r\nContent-Type: application/json\r\n\r\n{\"email\":\"a@example.com\"}";
    var noOpCandidate = EmailDetector.detect(noOpReq).stream().filter(c -> c.parameter().equals("email")).findFirst().orElseThrow();
    var noOpReplacement = RequestMutator.replaceTargeted(noOpReq, noOpCandidate, "a@example.com");
    if (noOpReplacement.changed())
      throw new AssertionError("exact no-op was falsely reported as changed");

    // v0.3.12: body-only mutations must use the intended UTF-8 body and wire verification must pass.
    class FakeRequest implements burp.api.montoya.http.message.requests.HttpRequest {
      private final String head;
      private final byte[] bodyBytes;
      FakeRequest(String text) {
        int cut = text.indexOf("\r\n\r\n");
        this.head = cut < 0 ? text : text.substring(0, cut + 4);
        this.bodyBytes = (cut < 0 ? "" : text.substring(cut + 4)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
      }
      FakeRequest(String head, byte[] bodyBytes) { this.head = head; this.bodyBytes = java.util.Arrays.copyOf(bodyBytes, bodyBytes.length); }
      public boolean isInScope() { return true; }
      public String method() { return "POST"; }
      public String url() { return "https://target.test/x"; }
      public String bodyToString() { return new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8); }
      public burp.api.montoya.core.ByteArray body() { return burp.api.montoya.core.ByteArray.byteArray(bodyBytes); }
      public burp.api.montoya.http.HttpService httpService() { return null; }
      public burp.api.montoya.http.message.requests.HttpRequest withBody(String body) { return withBody(burp.api.montoya.core.ByteArray.byteArray(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
      public burp.api.montoya.http.message.requests.HttpRequest withBody(burp.api.montoya.core.ByteArray body) { return new FakeRequest(head, body.getBytes()); }
      public burp.api.montoya.http.message.requests.HttpRequest copyToTempFile() { return this; }
      public String toString() { return head + bodyToString(); }
    }
    var fakeOriginal = new FakeRequest(noOpReq);
    var changedBodyText = noOpReq.replace("a@example.com", "b@example.net");
    var verifiedBuild = RequestWireBuilder.buildVerified(fakeOriginal, changedBodyText);
    if (!verifiedBuild.verified() || !verifiedBuild.request().bodyToString().contains("b@example.net") ||
        verifiedBuild.request().bodyToString().contains("a@example.com"))
      throw new AssertionError("body-preserving request construction regression: " + verifiedBuild.detail());

    // v0.3.8: raw Unicode bytes must be explicit UTF-8, never low-byte-narrowed Java chars.
    String fw = "xａ" + new String(Character.toChars(0x10040));
    String fwHex = RequestWireBuilder.utf8Hex(fw);
    if (!fwHex.equals("78efbd81f0908180"))
      throw new AssertionError("UTF-8 wire encoding regression: " + fwHex);

    // Domain replacement must never search/mutate a persistent Collaborator suffix.
    var yahooCtx = new MutationContext(
      "laikas@yahoo.payloadabc.oastify.com", "laikas", "yahoo.payloadabc.oastify.com",
      "payloadabc.oastify.com", "atom-persist", "receiver@payloadabc.oastify.com");
    MutationCase normL = MutationCatalog.all().stream().filter(m -> m.id().equals("NORM-008")).findFirst().orElseThrow();
    if (!normL.generator().generate(yahooCtx).equals(yahooCtx.canonicalEmail()))
      throw new AssertionError("absent first-label source mutated Collaborator suffix: " + normL.generator().generate(yahooCtx));
    MutationCase normA = MutationCatalog.all().stream().filter(m -> m.id().equals("NORM-003")).findFirst().orElseThrow();
    if (!normA.generator().generate(yahooCtx).startsWith("laikas@yａhoo."))
      throw new AssertionError("first-label-only domain mutation failed: " + normA.generator().generate(yahooCtx));

    // Curated modern-IDNA cases should be present in raw, JSON-wire, and literal A-label forms.
    MutationCase pvalidLatin = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-LATIN-002")).findFirst().orElseThrow();
    MutationCase pvalidLatinJson = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-LATIN-J-002")).findFirst().orElseThrow();
    MutationCase pvalidLatinA = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-LATIN-A-002")).findFirst().orElseThrow();
    if (!pvalidLatin.generator().generate(yahooCtx).startsWith("laikas@yáhoo."))
      throw new AssertionError("PVALID Latin mutation failed: " + pvalidLatin.generator().generate(yahooCtx));
    if (pvalidLatinJson.wireMode() != MutationCase.WireMode.JSON_UNICODE_ESCAPED)
      throw new AssertionError("PVALID JSON-wire mode missing");
    if (!pvalidLatinA.generator().generate(yahooCtx).contains("@xn--"))
      throw new AssertionError("PVALID literal A-label control missing: " + pvalidLatinA.generator().generate(yahooCtx));

    // v0.3.11: Mutation Strings must show the actual JSON wire spelling, not duplicate the logical Unicode row.
    MutationCase pvalidDiaJson = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-LATIN-J-003")).findFirst().orElseThrow();
    String diaLogical = pvalidDiaJson.generator().generate(yahooCtx);
    if (!diaLogical.startsWith("laikas@yähoo.") ||
        !MutationStringsPanel.manualWireValue(pvalidDiaJson, diaLogical).startsWith("laikas@y\\u00e4hoo."))
      throw new AssertionError("Mutation Strings JSON Unicode rendering failed: " +
        MutationStringsPanel.manualWireValue(pvalidDiaJson, diaLogical));

    MutationCase digraphJson = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-DIGRAPH-J-001")).findFirst().orElseThrow();
    String digraphLogical = digraphJson.generator().generate(yahooCtx);
    if (!digraphLogical.startsWith("laikas@yahꝏ.") ||
        !MutationStringsPanel.manualWireValue(digraphJson, digraphLogical).startsWith("laikas@yah\\ua74f."))
      throw new AssertionError("Mutation Strings OO JSON Unicode rendering failed: " +
        MutationStringsPanel.manualWireValue(digraphJson, digraphLogical));

    // UTS46 transitional pair ß/ss is only applicable when the source sequence is actually present.
    var ssCtx = new MutationContext("poc@pass.example", "poc", "pass.example", "unused.oastify.com", "atomx", "receiver@controlled.test");
    MutationCase sharpS = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-MAP-001")).findFirst().orElseThrow();
    if (!sharpS.generator().generate(ssCtx).equals("poc@paß.example"))
      throw new AssertionError("sharp-s transitional candidate failed: " + sharpS.generator().generate(ssCtx));

    // SMTP DATA parser must not strip legitimate lines beginning in C or S.
    var prefixed = "S: 220 collab\r\nC: EHLO mail\r\nS: 250 ok\r\nC: RCPT TO:<atom@abc.oastify.com>\r\nS: 250 ok\r\nC: DATA\r\nS: 354 go\r\n" +
      "C: Subject: hello\r\nC: Content-Type: text/plain\r\nC: \r\nC: color: red\r\nC: cursor: pointer\r\nC: .\r\nS: 250 queued";
    String parsedPrefixed = CollaboratorPoller.extractSmtpMessage(prefixed);
    if (!parsedPrefixed.contains("Subject: hello") || !parsedPrefixed.contains("Content-Type: text/plain") ||
        !parsedPrefixed.contains("color: red") || !parsedPrefixed.contains("cursor: pointer"))
      throw new AssertionError("SMTP C/S prefix parser ate message content: " + parsedPrefixed);

    // DNS/binary details and CSV output must never contain literal NUL/control bytes.
    String escapedBinary = CollaboratorPoller.escapeBinary(new byte[]{0x01, 0x00, 0x41, (byte)0xff});
    if (!escapedBinary.equals("\\x01\\x00A\\xFF") || escapedBinary.indexOf('\u0000') >= 0)
      throw new AssertionError("binary escaping failed: " + escapedBinary);
    String csvSafe = AtomizerPanel.sanitizeCsvText("a\u0000b\u0001c");
    if (!csvSafe.equals("a\\x00b\\x01c"))
      throw new AssertionError("CSV control-byte sanitization failed: " + csvSafe);

    System.out.println("PASS: " + MutationCatalog.all().size() + " unique mutations; manual Mutation Strings rendering/encoding, indexed multi-occurrence discovery, GraphQL operation separation, controlled-receiver generation, JSON-safe control-byte transport, stop-text matching, delivery sentinels, two-email roles, SMTP evidence parsing, and persistent/standalone Collaborator inbox correlation, persistent-account evidence dual-write into Results + inbox, note preservation, Collaborator Accounts connected/disconnected UI state refresh, and multi-interaction Collaborator aggregation verified.");
  }
}
JAVA
mkdir -p "$TMP/stubs" "$TMP/classes"
javac --release 21 -d "$TMP/stubs" $(find "$ROOT/compile-stubs/src" -name '*.java')
javac --release 21 -cp "$TMP/stubs" -d "$TMP/classes" \
  $(find "$ROOT/src/main/java" -name '*.java') \
  "$TMP/SelfTest.java"
java -Djava.awt.headless=true -cp "$TMP/classes:$TMP/stubs" com.emailatomizer.SelfTest
