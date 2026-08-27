package com.emailatomizer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RequestMutator {
    private RequestMutator() {}

    public record Replacement(String text, boolean changed) {}
    private record ExactReplacement(int start, int end, String rendered) {}

    public static Replacement replaceCanonical(String requestText, String canonical, String mutated) {
        if (requestText == null || canonical == null || canonical.isBlank()) {
            return new Replacement(requestText, false);
        }

        String result = requestText;
        boolean changed = false;

        String[][] forms = new String[][] {
                {canonical, mutated},
                {urlEncode(canonical), urlEncode(mutated)},
                {doubleUrlEncode(canonical), doubleUrlEncode(mutated)},
                {canonical.replace("@", "%40"), mutated.replace("@", "%40")},
                {canonical.replace("@", "%2540"), mutated.replace("@", "%2540")},
                {canonical.replace("@", "\\u0040"), mutated.replace("@", "\\u0040")},
                {canonical.replace("@", "\\u0040").replace("+", "\\u002b"),
                        mutated.replace("@", "\\u0040").replace("+", "\\u002b")}
        };

        for (String[] form : forms) {
            if (!form[0].equals(form[1]) && result.contains(form[0])) {
                result = result.replace(form[0], form[1]);
                changed = true;
            }
        }

        return new Replacement(result, changed);
    }

    public static Replacement replaceTargeted(String requestText, EmailCandidate candidate, String mutated) {
        return replaceTargeted(requestText, candidate, mutated, null, candidate == null ? "" : candidate.email());
    }

    /** Apply a mutation with transport-aware semantics. */
    public static Replacement replaceTargeted(String requestText, EmailCandidate candidate, String mutated,
                                              MutationCase mutation, String canonical) {
        if (candidate == null) return new Replacement(requestText, false);
        MutationCase.WireMode mode = mutation == null ? MutationCase.WireMode.STANDARD : mutation.wireMode();
        if (mode == MutationCase.WireMode.STANDARD) {
            ExactReplacement exact = exactReplacement(requestText, candidate, mutated);
            if (exact != null) {
                String result = requestText.substring(0, exact.start()) + exact.rendered() + requestText.substring(exact.end());
                return new Replacement(result, !result.equals(requestText));
            }
            return replaceCanonical(requestText, candidate.email(), mutated);
        }
        return replaceJsonWireTargeted(requestText, candidate, mutated, canonical, mode);
    }

    /**
     * Replace two independently detected email occurrences without allowing the first replacement
     * to invalidate the raw offset of the second. Exact candidates are replaced back-to-front.
     */
    public static Replacement replaceTwoTargeted(String requestText,
                                                 EmailCandidate first, String firstValue,
                                                 EmailCandidate second, String secondValue) {
        if (requestText == null || first == null || second == null) return new Replacement(requestText, false);

        ExactReplacement a = exactReplacement(requestText, first, firstValue);
        ExactReplacement b = exactReplacement(requestText, second, secondValue);
        if (a != null && b != null && a.start() != b.start()) {
            List<ExactReplacement> rs = new ArrayList<>(List.of(a, b));
            rs.sort(Comparator.comparingInt(ExactReplacement::start).reversed());
            String result = requestText;
            for (ExactReplacement r : rs) {
                result = result.substring(0, r.start()) + r.rendered() + result.substring(r.end());
            }
            return new Replacement(result, !result.equals(requestText));
        }

        // Fallback is safe when the two canonical values differ. If they are identical and Burp
        // did not preserve exact offsets, replacing globally would destroy role separation.
        if (!first.email().equalsIgnoreCase(second.email())) {
            Replacement one = replaceTargeted(requestText, first, firstValue);
            Replacement two = replaceCanonical(one.text(), second.email(), secondValue);
            return new Replacement(two.text(), one.changed() || two.changed());
        }
        return new Replacement(requestText, false);
    }

    private static Replacement replaceJsonWireTargeted(String requestText, EmailCandidate candidate,
                                                       String mutated, String canonical,
                                                       MutationCase.WireMode mode) {
        if (requestText == null || candidate == null || !candidate.hasExactRawTarget()) {
            return new Replacement(requestText, false);
        }
        String location = candidate.location() == null ? "" : candidate.location();
        if (!location.startsWith("JSON")) return new Replacement(requestText, false);

        int start = candidate.rawOffset();
        String rawToken = candidate.rawToken();
        if (start < 0 || rawToken == null || start + rawToken.length() > requestText.length() ||
                !requestText.regionMatches(start, rawToken, 0, rawToken.length())) {
            return new Replacement(requestText, false);
        }

        String rendered;
        switch (mode) {
            case JSON_UNICODE_ESCAPED -> rendered = jsonEscapeUnicodeWire(mutated, false, false, false);
            case JSON_DOUBLE_UNICODE_ESCAPED -> rendered = doubleJsonUnicodeEscapes(mutated);
            case JSON_ESCAPE_AT -> rendered = jsonEscapeUnicodeWire(mutated, true, false, false);
            case JSON_ESCAPE_DOT -> rendered = jsonEscapeUnicodeWire(mutated, false, true, false);
            case JSON_ESCAPE_PLUS -> rendered = jsonEscapeUnicodeWire(mutated, false, false, true);
            case JSON_DUPLICATE_KEY_IDENTITY_FIRST,
                 JSON_DUPLICATE_KEY_MUTATION_FIRST,
                 JSON_ESCAPED_KEY_IDENTITY_FIRST,
                 JSON_ESCAPED_KEY_MUTATION_FIRST -> {
                String key = candidate.parameter() == null ? "" : candidate.parameter();
                if (key.isBlank() || canonical == null || canonical.isBlank()) return new Replacement(requestText, false);
                String secondKey = switch (mode) {
                    case JSON_ESCAPED_KEY_IDENTITY_FIRST, JSON_ESCAPED_KEY_MUTATION_FIRST -> jsonEscapedEquivalentKey(key);
                    default -> jsonEscape(key);
                };
                String identity = jsonEscape(canonical);
                String alternate = jsonEscape(mutated);
                boolean identityFirst = mode == MutationCase.WireMode.JSON_DUPLICATE_KEY_IDENTITY_FIRST ||
                        mode == MutationCase.WireMode.JSON_ESCAPED_KEY_IDENTITY_FIRST;
                rendered = identityFirst
                        ? identity + "\",\"" + secondKey + "\":\"" + alternate
                        : alternate + "\",\"" + secondKey + "\":\"" + identity;
            }
            default -> { return new Replacement(requestText, false); }
        }

        String result = requestText.substring(0, start) + rendered + requestText.substring(start + rawToken.length());
        // A transport mutation only counts as a mutation when the actual outbound request text changes.
        // Character-targeted JSON-wire generators deliberately return the canonical address when the
        // source character/sequence is absent; treating that as changed caused Atomizer to replay the
        // real account address and label it as a mutation.
        return new Replacement(result, !result.equals(requestText));
    }

    /** JSON string-content encoder that deliberately preserves Unicode escapes on the HTTP wire. */
    static String jsonEscapeUnicodeWire(String value, boolean escapeAt, boolean escapeDot, boolean escapePlus) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 24);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((escapeAt && ch == '@') || (escapeDot && ch == '.') || (escapePlus && ch == '+') || ch >= 0x80) {
                out.append(String.format("\\u%04x", (int) ch));
                continue;
            }
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.toString();
    }

    static String doubleJsonUnicodeEscapes(String value) {
        String once = jsonEscapeUnicodeWire(value, false, false, false);
        return once.replace("\\", "\\\\");
    }

    /** Produce a JSON key that decodes to the same ASCII key, e.g. email -> e\\u006dail. */
    static String jsonEscapedEquivalentKey(String key) {
        if (key == null || key.isBlank()) return "";
        int idx = key.length() > 1 ? 1 : 0;
        char ch = key.charAt(idx);
        return jsonEscape(key.substring(0, idx)) + String.format("\\u%04x", (int) ch) + jsonEscape(key.substring(idx + 1));
    }

    private static ExactReplacement exactReplacement(String requestText, EmailCandidate candidate, String mutated) {
        if (candidate == null || !candidate.hasExactRawTarget()) return null;
        int start = candidate.rawOffset();
        String rawToken = candidate.rawToken();
        if (start < 0 || start + rawToken.length() > requestText.length() ||
                !requestText.regionMatches(start, rawToken, 0, rawToken.length())) return null;
        return new ExactReplacement(start, start + rawToken.length(), renderForCandidate(candidate, mutated));
    }

    private static String renderForCandidate(EmailCandidate candidate, String mutated) {
        return switch (candidate.representation()) {
            case "url-encoded" -> urlEncode(mutated);
            case "json-unicode-escaped" -> jsonEscape(mutated).replace("@", "\\u0040").replace("+", "\\u002b");
            default -> {
                String location = candidate.location() == null ? "" : candidate.location();
                if (location.startsWith("JSON")) yield jsonEscape(mutated);
                if (location.equals("form body")) yield urlEncode(mutated);
                yield mutated;
            }
        };
    }

    /**
     * Encode a Java string for insertion inside an existing JSON string value. This preserves
     * the intended email parser payload while keeping the surrounding HTTP request valid JSON.
     */
    public static String jsonEscape(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.toString();
    }

    public static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String doubleUrlEncode(String value) {
        return urlEncode(urlEncode(value));
    }
}
