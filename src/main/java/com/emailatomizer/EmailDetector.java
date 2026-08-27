package com.emailatomizer;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailDetector {
    private EmailDetector() {}

    // Intentionally practical rather than a complete RFC 5322 grammar. Passive discovery should
    // find normal application email values; the mutation engine is where unusual syntax belongs.
    private static final Pattern LITERAL = Pattern.compile(
            "(?i)(?<![A-Z0-9.!#$%'*+/^_`{|}~-])" +
            "([A-Z0-9.!#$%'*+/^_`{|}~-]{1,96}@(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+[A-Z]{2,63})" +
            "(?![A-Z0-9-])");

    private static final Pattern URL_ENCODED = Pattern.compile(
            "(?i)([A-Z0-9.!$'*+/^_`{|}~%+-]{1,180}%40(?:[A-Z0-9%_-]{1,63}\\.)+[A-Z0-9%_-]{2,80})");

    private static final Pattern JSON_NAME = Pattern.compile("(?s).*?[\\\"']([A-Za-z0-9_.-]{1,80})[\\\"']\\s*:\\s*[\\\"'][^\\\"']*$");
    private static final Pattern FORM_NAME = Pattern.compile("(?s)(?:^|[?&;])([^?&;=\\s]{1,80})=[^?&;]*$");

    public static List<EmailCandidate> detect(HttpRequest request) {
        if (request == null) return List.of();
        return detect(request.toString());
    }

    public static List<EmailCandidate> detect(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        Map<String, EmailCandidate> unique = new LinkedHashMap<>();

        Matcher literal = LITERAL.matcher(raw);
        while (literal.find()) {
            String email = literal.group(1);
            add(unique, candidate(raw, literal.start(1), email, "literal", literal.group(1)));
        }

        Matcher encoded = URL_ENCODED.matcher(raw);
        while (encoded.find()) {
            String token = encoded.group(1);
            String decoded = decodeTwiceSafely(token);
            Matcher dm = LITERAL.matcher(decoded);
            if (dm.find()) {
                add(unique, candidate(raw, encoded.start(1), dm.group(1), "url-encoded", token));
            }
        }

        String unicodeNormalized = raw
                .replace("\\u0040", "@").replace("\\u0040".toUpperCase(Locale.ROOT), "@")
                .replace("\\u002b", "+").replace("\\u002B", "+");
        if (!unicodeNormalized.equals(raw)) {
            Matcher um = LITERAL.matcher(unicodeNormalized);
            while (um.find()) {
                EmailCandidate c = candidate(unicodeNormalized, um.start(1), um.group(1), "json-unicode-escaped", "");
                add(unique, c);
            }
        }

        return new ArrayList<>(unique.values());
    }

    private static void add(Map<String, EmailCandidate> out, EmailCandidate c) {
        // Preserve distinct occurrences inside the same request. Two GraphQL variables, array items,
        // or repeated fields may contain the same address and still need to be selectable independently.
        String occurrence = c.hasExactRawTarget() ? Integer.toString(c.rawOffset()) :
                c.location() + "|" + c.parameter() + "|" + c.representation();
        String key = occurrence + "|" + c.email().toLowerCase(Locale.ROOT);
        out.putIfAbsent(key, c);
    }

    private static EmailCandidate candidate(String raw, int offset, String email, String representation, String rawToken) {
        int firstLineEnd = indexOfLineEnd(raw);
        int headersEnd = headerEnd(raw);
        String location;
        String parameter = "";

        if (offset <= firstLineEnd) {
            location = "query/path";
            parameter = inferName(raw.substring(0, Math.min(offset, raw.length())));
        } else if (headersEnd >= 0 && offset < headersEnd) {
            location = "header";
            int lineStart = Math.max(raw.lastIndexOf('\n', Math.max(0, offset - 1)), raw.lastIndexOf('\r', Math.max(0, offset - 1))) + 1;
            int colon = raw.indexOf(':', lineStart);
            if (colon > lineStart && colon < offset) parameter = raw.substring(lineStart, colon).trim();
        } else {
            location = "body";
            int start = headersEnd >= 0 ? headersEnd : Math.max(0, offset - 240);
            String prefix = raw.substring(Math.max(start, offset - 240), Math.min(offset, raw.length()));
            parameter = inferName(prefix);
            if (looksJson(prefix)) location = "JSON body";
            else if (prefix.contains("=") || prefix.contains("&")) location = "form body";
        }

        return new EmailCandidate(email, location, parameter, representation, rawToken == null || rawToken.isBlank() ? -1 : offset, rawToken);
    }

    private static String inferName(String prefix) {
        Matcher jm = JSON_NAME.matcher(prefix);
        if (jm.matches()) return jm.group(1);
        Matcher fm = FORM_NAME.matcher(prefix);
        if (fm.find()) return safeDecode(fm.group(1));
        return "";
    }

    private static boolean looksJson(String prefix) {
        int brace = Math.max(prefix.lastIndexOf('{'), prefix.lastIndexOf('['));
        int eq = prefix.lastIndexOf('=');
        return brace >= 0 && brace > eq;
    }

    private static int indexOfLineEnd(String raw) {
        int rn = raw.indexOf("\r\n");
        int n = raw.indexOf('\n');
        if (rn >= 0) return rn;
        return n >= 0 ? n : raw.length();
    }

    private static int headerEnd(String raw) {
        int p = raw.indexOf("\r\n\r\n");
        if (p >= 0) return p + 4;
        p = raw.indexOf("\n\n");
        return p >= 0 ? p + 2 : -1;
    }

    private static String decodeTwiceSafely(String s) {
        String once = safeDecode(s);
        // URLDecoder treats literal + as a space, so only perform a second pass when
        // the first pass still contains percent-encoded bytes.
        if (!once.matches(".*%[0-9A-Fa-f]{2}.*")) return once;
        return safeDecode(once);
    }

    private static String safeDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }
}
