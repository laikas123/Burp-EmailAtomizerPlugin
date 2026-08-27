package com.emailatomizer;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a stable logical operation label so passive discovery does not collapse distinct RPCs sharing one path. */
public final class RequestClassifier {
    private RequestClassifier() {}

    private static final Pattern HEADER_OPERATION = Pattern.compile("(?im)^X-Operation-Name\\s*:\\s*([^\\r\\n]+)");
    private static final Pattern BODY_OPERATION = Pattern.compile("(?i)\\\"operationName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern QUERY_OPERATION = Pattern.compile("(?i)(?:[?&])operationName=([^&#\\s]+)");
    private static final Pattern GRAPHQL_NAMED = Pattern.compile("(?i)\\b(?:mutation|query)\\s+([A-Za-z_][A-Za-z0-9_]*)");

    public static String operation(HttpRequest request) {
        if (request == null) return "";
        String url;
        try { url = request.url(); } catch (Throwable t) { url = ""; }
        return operation(url, request.toString());
    }

    static String operation(String url, String rawRequest) {
        LinkedHashSet<String> values = new LinkedHashSet<>();

        if (url != null) {
            Matcher q = QUERY_OPERATION.matcher(url);
            while (q.find()) add(values, decode(q.group(1)));
        }

        String raw = rawRequest == null ? "" : rawRequest;
        Matcher h = HEADER_OPERATION.matcher(raw);
        while (h.find()) add(values, h.group(1));

        Matcher b = BODY_OPERATION.matcher(raw);
        while (b.find()) add(values, b.group(1));

        // Fallback for GraphQL clients that omit operationName but include a named query/mutation.
        if (values.isEmpty()) {
            Matcher named = GRAPHQL_NAMED.matcher(raw);
            if (named.find()) add(values, named.group(1));
        }

        return String.join(" / ", values);
    }

    private static void add(LinkedHashSet<String> values, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (!trimmed.isBlank()) values.add(trimmed);
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { return value; }
    }
}
