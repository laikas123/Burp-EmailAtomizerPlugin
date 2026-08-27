package com.emailatomizer;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MutationCatalogTest {
    @Test
    void idsAreUniqueAndGeneratorsReturnValues() {
        Set<String> ids = new HashSet<>();
        MutationContext c = new MutationContext(
                "tester+1@example.com", "tester+1", "example.com", "abc.oastify.com", "atom00000001", "receiver@abc.oastify.com");

        assertTrue(MutationCatalog.all().size() >= 40);
        for (MutationCase mutation : MutationCatalog.all()) {
            assertTrue(ids.add(mutation.id()), "duplicate id " + mutation.id());
            String value = mutation.generator().generate(c);
            assertNotNull(value);
            assertFalse(value.isBlank());
        }
    }

    @Test
    void passiveDetectorHandlesLiteralAndEncodedForms() {
        String json = "POST / HTTP/1.1\r\nHost: example.test\r\n\r\n{\"email\":\"tester+1@example.com\"}";
        assertTrue(EmailDetector.detect(json).stream().anyMatch(c -> c.email().equals("tester+1@example.com") && c.parameter().equals("email")));

        String form = "POST / HTTP/1.1\r\nHost: example.test\r\n\r\nemail=tester%2B1%40example.com";
        assertTrue(EmailDetector.detect(form).stream().anyMatch(c -> c.email().equals("tester+1@example.com")));
    }

    @Test
    void targetedMutatorChangesOnlySelectedOccurrence() {
        String raw = "POST / HTTP/1.1\r\nHost: example.test\r\nCookie: owner=tester@example.com\r\n\r\n{\"email\":\"tester@example.com\"}";
        EmailCandidate body = EmailDetector.detect(raw).stream().filter(c -> c.location().contains("body")).findFirst().orElseThrow();
        RequestMutator.Replacement replacement = RequestMutator.replaceTargeted(raw, body, "atom@abc.oastify.com");
        assertTrue(replacement.changed());
        assertTrue(replacement.text().contains("Cookie: owner=tester@example.com"));
        assertTrue(replacement.text().contains("{\"email\":\"atom@abc.oastify.com\"}"));
    }

    @Test
    void passiveDetectorFindsGraphqlIdentifierEmail() {
        String raw = "POST /graphql?operationName=SendVerificationCode HTTP/2\r\nHost: www.instacart.com\r\nContent-Type: application/json\r\n\r\n" +
                "{\"operationName\":\"SendVerificationCode\",\"variables\":{\"identifier\":\"laikas+202@wearehackerone.com\",\"identifier_type\":\"email\"}}";
        assertTrue(EmailDetector.detect(raw).stream().anyMatch(c ->
                c.email().equals("laikas+202@wearehackerone.com") && c.parameter().equals("identifier")));
    }

    @Test
    void targetedJsonMutationEscapesQuotesAndBackslashes() {
        String raw = "POST / HTTP/1.1\r\nHost: example.test\r\nContent-Type: application/json\r\n\r\n{\"email\":\"tester@example.com\"}";
        EmailCandidate body = EmailDetector.detect(raw).stream().filter(c -> c.parameter().equals("email")).findFirst().orElseThrow();
        RequestMutator.Replacement replacement = RequestMutator.replaceTargeted(raw, body, "\"atom\\\\@oast.test\"@example.com");
        assertTrue(replacement.changed());
        assertTrue(replacement.text().contains("\\\"atom\\\\\\\\@oast.test\\\"@example.com"));
    }

    @Test
    void receiverAwareMutationsCanTargetControlledMailbox() {
        MutationContext c = new MutationContext(
                "identity@example.com", "identity", "example.com", "unused.oastify.com",
                "atom00000099", "receiver+2@controlled.test");

        MutationCase direct = MutationCatalog.all().stream().filter(m -> m.id().equals("BASE-002")).findFirst().orElseThrow();
        MutationCase percent = MutationCatalog.all().stream().filter(m -> m.id().equals("ROUTE-003")).findFirst().orElseThrow();
        MutationCase encoded = MutationCatalog.all().stream().filter(m -> m.id().equals("EW-PROBE-001")).findFirst().orElseThrow();

        assertEquals("receiver+2@controlled.test", direct.generator().generate(c));
        assertEquals("receiver+2%controlled.test@example.com", percent.generator().generate(c));
        assertTrue(encoded.generator().generate(c).endsWith("@controlled.test"));
    }

    @Test
    void passiveDetectorPreservesRepeatedOccurrencesAndClassifiesGraphqlOperations() {
        String repeated = "POST /x HTTP/1.1\r\nHost: target.test\r\nContent-Type: application/json\r\n\r\n" +
                "{\"emails\":[\"same@example.com\",\"same@example.com\"]}";
        var candidates = EmailDetector.detect(repeated);
        assertEquals(2, candidates.size());
        assertNotEquals(AtomizerState.discoverySlot(candidates, 0), AtomizerState.discoverySlot(candidates, 1));

        String temp = "POST /api/bff/customer HTTP/2\r\nHost: www.abercrombie.com\r\nX-Operation-Name: SendTempPassword\r\n\r\n" +
                "[{\"operationName\":\"SendTempPassword\",\"variables\":{\"email\":\"same@example.com\"}}]";
        String login = "POST /api/bff/customer HTTP/2\r\nHost: www.abercrombie.com\r\nX-Operation-Name: LOGIN_MUTATION\r\n\r\n" +
                "[{\"operationName\":\"LOGIN_MUTATION\",\"variables\":{\"email\":\"same@example.com\"}}]";
        assertEquals("SendTempPassword", RequestClassifier.operation("https://www.abercrombie.com/api/bff/customer", temp));
        assertEquals("LOGIN_MUTATION", RequestClassifier.operation("https://www.abercrombie.com/api/bff/customer", login));
    }

    @Test
    void targetedJsonMutationEscapesRawNul() {
        String raw = "POST / HTTP/1.1\r\nHost: example.test\r\nContent-Type: application/json\r\n\r\n{\"email\":\"tester@example.com\"}";
        EmailCandidate body = EmailDetector.detect(raw).stream().filter(c -> c.parameter().equals("email")).findFirst().orElseThrow();
        RequestMutator.Replacement replacement = RequestMutator.replaceTargeted(raw, body, "atom\u0000@oast.test@example.com");
        assertTrue(replacement.changed());
        assertFalse(replacement.text().contains("atom" + '\u0000' + "@"));
        assertTrue(replacement.text().contains("atom\\u0000@oast.test@example.com"));
    }

    @Test
    void confirmedCompatibilityDifferentialsAreInCatalog() {
        MutationContext c = new MutationContext(
                "poc@gmail.example.com", "poc", "gmail.example.com", "unused.oastify.com",
                "atom00000123", "receiver@controlled.test");

        MutationCase modifierA = MutationCatalog.all().stream().filter(m -> m.id().equals("NORM-001")).findFirst().orElseThrow();
        MutationCase subscriptA = MutationCatalog.all().stream().filter(m -> m.id().equals("NORM-002")).findFirst().orElseThrow();

        assertEquals("poc@gmᵃil.example.com", modifierA.generator().generate(c));
        assertEquals("poc@gmₐil.example.com", subscriptA.generator().generate(c));
    }

    @Test
    void customMutationSetSupportsUnicodeEscapesCommentsAndPlaceholders() {
        String pasted = "# high priority\n" +
                "poc@gm\\u1d43il.example.com\n" +
                "{LOCAL}@gm\\u2090il.{DOMAIN}\n" +
                "{RECEIVER}\n";
        var custom = CustomMutationParser.parse(pasted);
        assertEquals(3, custom.size());

        MutationContext c = new MutationContext(
                "poc@example.com", "poc", "example.com", "abc.oastify.com",
                "atom00000124", "receiver@controlled.test");
        assertEquals("poc@gmᵃil.example.com", custom.get(0).generator().generate(c));
        assertEquals("poc@gmₐil.example.com", custom.get(1).generator().generate(c));
        assertEquals("receiver@controlled.test", custom.get(2).generator().generate(c));
        assertFalse(custom.get(0).collaboratorCapable());
        assertTrue(custom.get(2).collaboratorCapable());
    }

    @Test
    void rawPunycodeCounterpartsMatchKnownCompatibilityLabels() {
        assertEquals("gmil-6b4a", RawPunycode.encodeLabel("gmᵃil"));
        assertEquals("gmil-wr7a", RawPunycode.encodeLabel("gmₐil"));
        assertEquals("werehackerone-jf8f", RawPunycode.encodeLabel("weᵃrehackerone"));
        assertEquals("werehackerone-j27g", RawPunycode.encodeLabel("weₐrehackerone"));
    }

    @Test
    void jsonUnicodeWireMutationPreservesBackslashUOnRequestWire() {
        String raw = "POST /reset HTTP/1.1\r\nHost: example.test\r\nContent-Type: application/json\r\n\r\n{\"email\":\"poc@gmail.example.com\"}";
        EmailCandidate body = EmailDetector.detect(raw).stream().filter(c -> c.parameter().equals("email")).findFirst().orElseThrow();
        MutationCase mutation = MutationCatalog.all().stream().filter(m -> m.id().equals("JSON-WIRE-001")).findFirst().orElseThrow();
        MutationContext c = new MutationContext("poc@gmail.example.com", "poc", "gmail.example.com", "unused.oastify.com", "atomx", "receiver@controlled.test");
        String logical = mutation.generator().generate(c);
        RequestMutator.Replacement replacement = RequestMutator.replaceTargeted(raw, body, logical, mutation, c.canonicalEmail());
        assertTrue(replacement.changed());
        assertTrue(replacement.text().contains("poc@gm\\u1d43il.example.com"));
        assertFalse(replacement.text().contains("poc@gmᵃil.example.com"));
    }

    @Test
    void astralJsonWireMutationUsesSurrogatePair() {
        String encoded = RequestMutator.jsonEscapeUnicodeWire("x" + new String(Character.toChars(0x10040)) + "y", false, false, false);
        assertEquals("x\\ud800\\udc40y", encoded);
    }

    @Test
    void duplicateAndEscapedEquivalentJsonKeysAreRealWireMutations() {
        String raw = "POST /reset HTTP/1.1\r\nHost: example.test\r\nContent-Type: application/json\r\n\r\n{\"email\":\"victim@example.com\",\"locale\":\"en\"}";
        EmailCandidate body = EmailDetector.detect(raw).stream().filter(c -> c.parameter().equals("email")).findFirst().orElseThrow();

        MutationCase duplicate = MutationCatalog.all().stream().filter(m -> m.id().equals("JSON-KEY-001")).findFirst().orElseThrow();
        RequestMutator.Replacement d = RequestMutator.replaceTargeted(raw, body, "atom@oast.test", duplicate, "victim@example.com");
        assertTrue(d.changed());
        assertTrue(d.text().contains("\"email\":\"victim@example.com\",\"email\":\"atom@oast.test\""));

        MutationCase escaped = MutationCatalog.all().stream().filter(m -> m.id().equals("JSON-KEY-003")).findFirst().orElseThrow();
        RequestMutator.Replacement e = RequestMutator.replaceTargeted(raw, body, "atom@oast.test", escaped, "victim@example.com");
        assertTrue(e.changed());
        assertTrue(e.text().contains("\"email\":\"victim@example.com\",\"e\\u006dail\":\"atom@oast.test\""));
    }

    @Test
    void jsonWireOnlyCasesSkipNonJsonTargets() {
        String raw = "POST /reset HTTP/1.1\r\nHost: example.test\r\nContent-Type: application/x-www-form-urlencoded\r\n\r\nemail=poc%40gmail.example.com";
        EmailCandidate form = EmailDetector.detect(raw).stream().findFirst().orElseThrow();
        MutationCase mutation = MutationCatalog.all().stream().filter(m -> m.id().equals("JSON-WIRE-001")).findFirst().orElseThrow();
        RequestMutator.Replacement replacement = RequestMutator.replaceTargeted(raw, form, "poc@gmᵃil.example.com", mutation, "poc@gmail.example.com");
        assertFalse(replacement.changed());
    }

    @Test
    void rawUnicodeWireBuilderUsesUtf8Bytes() {
        assertEquals("78efbd81f0908180", RequestWireBuilder.utf8Hex("xａ" + new String(Character.toChars(0x10040))));
    }

    @Test
    void domainMutationsNeverTouchCollaboratorSuffix() {
        MutationContext c = new MutationContext(
                "laikas@yahoo.payloadabc.oastify.com", "laikas", "yahoo.payloadabc.oastify.com",
                "payloadabc.oastify.com", "atomx", "receiver@payloadabc.oastify.com");

        MutationCase absent = MutationCatalog.all().stream().filter(m -> m.id().equals("NORM-008")).findFirst().orElseThrow();
        MutationCase present = MutationCatalog.all().stream().filter(m -> m.id().equals("NORM-003")).findFirst().orElseThrow();

        assertEquals(c.canonicalEmail(), absent.generator().generate(c));
        assertTrue(present.generator().generate(c).startsWith("laikas@yａhoo."));
        assertTrue(present.generator().generate(c).endsWith(".payloadabc.oastify.com"));
    }

    @Test
    void idna2008PvalidFamiliesIncludeUnicodeJsonAndAlabelForms() {
        MutationContext c = new MutationContext(
                "laikas@yahoo.payloadabc.oastify.com", "laikas", "yahoo.payloadabc.oastify.com",
                "payloadabc.oastify.com", "atomx", "receiver@payloadabc.oastify.com");

        MutationCase raw = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-LATIN-002")).findFirst().orElseThrow();
        MutationCase json = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-LATIN-J-002")).findFirst().orElseThrow();
        MutationCase alabel = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-LATIN-A-002")).findFirst().orElseThrow();

        assertTrue(raw.generator().generate(c).startsWith("laikas@yáhoo."));
        assertEquals(MutationCase.WireMode.JSON_UNICODE_ESCAPED, json.wireMode());
        assertTrue(alabel.generator().generate(c).contains("@xn--"));
    }

    @Test
    void pvalidSmallOoDigraphHasRawJsonAndAlabelForms() {
        MutationContext c = new MutationContext(
                "laikas@yahoo.poc.example", "laikas", "yahoo.poc.example",
                "poc.example", "atomx", "receiver@poc.example");

        MutationCase raw = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-DIGRAPH-001")).findFirst().orElseThrow();
        MutationCase json = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-DIGRAPH-J-001")).findFirst().orElseThrow();
        MutationCase alabel = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-DIGRAPH-A-001")).findFirst().orElseThrow();

        assertEquals("laikas@yahꝏ.poc.example", raw.generator().generate(c));
        assertEquals(MutationCase.WireMode.JSON_UNICODE_ESCAPED, json.wireMode());
        assertEquals("laikas@yahꝏ.poc.example", json.generator().generate(c));
        assertEquals("laikas@xn--yah-pp3l.poc.example", alabel.generator().generate(c));
    }

    @Test
    void smtpParserPreservesHeadersAndCssStartingWithCOrS() {
        String transcript = "S: 220 collab\r\nC: EHLO mail\r\nS: 250 ok\r\n" +
                "C: RCPT TO:<atom@abc.oastify.com>\r\nS: 250 ok\r\nC: DATA\r\nS: 354 go\r\n" +
                "C: Subject: hello\r\nC: Content-Type: text/plain\r\nC: \r\n" +
                "C: color: red\r\nC: cursor: pointer\r\nC: .\r\nS: 250 queued";
        String message = CollaboratorPoller.extractSmtpMessage(transcript);
        assertTrue(message.contains("Subject: hello"));
        assertTrue(message.contains("Content-Type: text/plain"));
        assertTrue(message.contains("color: red"));
        assertTrue(message.contains("cursor: pointer"));
    }

    @Test
    void binaryCollaboratorDataAndCsvControlsAreEscaped() {
        assertEquals("\\x01\\x00A\\xFF", CollaboratorPoller.escapeBinary(new byte[]{0x01, 0x00, 0x41, (byte)0xff}));
        assertEquals("a\\x00b\\x01c", AtomizerPanel.sanitizeCsvText("a\u0000b\u0001c"));
    }

    @Test
    void absentSourceJsonUnicodeMutationDoesNotReplayCanonicalEmail() {
        String raw = "POST /api/bff/customer HTTP/1.1\r\nHost: www.abercrombie.com\r\nContent-Type: application/json\r\n\r\n" +
                "[{\"operationName\":\"SendTempPassword\",\"variables\":{\"email\":\"laikas@gmail.com\"}}]";
        EmailCandidate body = EmailDetector.detect(raw).stream().filter(c -> c.parameter().equals("email")).findFirst().orElseThrow();
        MutationCase mutation = MutationCatalog.all().stream().filter(m -> m.id().equals("PVALID-LATIN-J-006")).findFirst().orElseThrow();
        MutationContext c = new MutationContext("laikas@gmail.com", "laikas", "gmail.com", "unused.oastify.com", "atomx", "receiver@controlled.test");

        String logical = mutation.generator().generate(c);
        assertEquals(c.canonicalEmail(), logical); // gmail has no 'y', so this case is not applicable.
        RequestMutator.Replacement replacement = RequestMutator.replaceTargeted(raw, body, logical, mutation, c.canonicalEmail());
        assertFalse(replacement.changed());
        assertEquals(raw, replacement.text());
    }

    @Test
    void jsonSyntaxEscapeOnlyCountsWhenItChangesTheWire() {
        String raw = "POST /reset HTTP/1.1\r\nHost: example.test\r\nContent-Type: application/json\r\n\r\n{\"email\":\"laikas@gmail.com\"}";
        EmailCandidate body = EmailDetector.detect(raw).stream().filter(c -> c.parameter().equals("email")).findFirst().orElseThrow();
        MutationContext c = new MutationContext("laikas@gmail.com", "laikas", "gmail.com", "unused.oastify.com", "atomx", "receiver@controlled.test");

        MutationCase plus = MutationCatalog.all().stream().filter(m -> m.id().equals("JSON-WIRE-009")).findFirst().orElseThrow();
        RequestMutator.Replacement plusReplacement = RequestMutator.replaceTargeted(raw, body, c.canonicalEmail(), plus, c.canonicalEmail());
        assertFalse(plusReplacement.changed()); // no plus sign exists to escape

        MutationCase at = MutationCatalog.all().stream().filter(m -> m.id().equals("JSON-WIRE-007")).findFirst().orElseThrow();
        RequestMutator.Replacement atReplacement = RequestMutator.replaceTargeted(raw, body, c.canonicalEmail(), at, c.canonicalEmail());
        assertTrue(atReplacement.changed());
        assertTrue(atReplacement.text().contains("laikas\\u0040gmail.com"));
    }

    @Test
    void mutationStringsHonorsJsonUnicodeWireModeForLatinFold() {
        MutationContext c = new MutationContext(
                "laikas@yahooz.poc.example", "laikas", "yahooz.poc.example",
                "poc.example", "atomx", "receiver@poc.example");

        MutationCase json = MutationCatalog.all().stream()
                .filter(m -> m.id().equals("PVALID-LATIN-J-003"))
                .findFirst().orElseThrow();

        String logical = json.generator().generate(c);
        assertEquals("laikas@yähooz.poc.example", logical);
        assertEquals("laikas@y\\u00e4hooz.poc.example",
                MutationStringsPanel.manualWireValue(json, logical));
    }

    @Test
    void mutationStringsHonorsJsonUnicodeWireModeForOoDigraph() {
        MutationContext c = new MutationContext(
                "laikas@yahooz.poc.example", "laikas", "yahooz.poc.example",
                "poc.example", "atomx", "receiver@poc.example");

        MutationCase json = MutationCatalog.all().stream()
                .filter(m -> m.id().equals("PVALID-DIGRAPH-J-001"))
                .findFirst().orElseThrow();

        String logical = json.generator().generate(c);
        assertEquals("laikas@yahꝏz.poc.example", logical);
        assertEquals("laikas@yah\\ua74fz.poc.example",
                MutationStringsPanel.manualWireValue(json, logical));
    }

    @Test
    void mutationStringsHonorsJsonSyntaxEscapeWireModes() {
        String logical = "laikas+tag@gmail.com";
        MutationCase at = MutationCatalog.all().stream()
                .filter(m -> m.id().equals("JSON-WIRE-007"))
                .findFirst().orElseThrow();
        MutationCase dot = MutationCatalog.all().stream()
                .filter(m -> m.id().equals("JSON-WIRE-008"))
                .findFirst().orElseThrow();
        MutationCase plus = MutationCatalog.all().stream()
                .filter(m -> m.id().equals("JSON-WIRE-009"))
                .findFirst().orElseThrow();

        assertEquals("laikas+tag\\u0040gmail.com",
                MutationStringsPanel.manualWireValue(at, logical));
        assertEquals("laikas+tag@gmail\\u002ecom",
                MutationStringsPanel.manualWireValue(dot, logical));
        assertEquals("laikas\\u002btag@gmail.com",
                MutationStringsPanel.manualWireValue(plus, logical));
    }


}
