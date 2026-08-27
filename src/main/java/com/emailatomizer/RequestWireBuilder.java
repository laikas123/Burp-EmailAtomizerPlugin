package com.emailatomizer;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Builds mutated requests from explicit UTF-8 bytes instead of Montoya's String request constructor.
 *
 * v0.3.12 additionally treats the body as an authoritative byte sequence. When only the body changed,
 * it applies those bytes directly with withBody(ByteArray) instead of reparsing the complete textual
 * request. When request-line/headers also changed, the request is reparsed and then the intended body
 * bytes are re-applied. A separate verification step lets callers fail closed before any request is sent.
 */
public final class RequestWireBuilder {
    private RequestWireBuilder() {}

    public record BuildResult(HttpRequest request, boolean verified, String detail,
                              String intendedBodyHex, String actualBodyHex) {}

    private record MessageParts(String head, String body, boolean hasSeparator) {}

    public static HttpRequest fromUtf8Text(HttpRequest original, String requestText) {
        if (original == null) throw new IllegalArgumentException("original request is required");
        if (requestText == null) requestText = "";

        String originalText = original.toString();
        MessageParts before = splitMessage(originalText);
        MessageParts intended = splitMessage(requestText);

        // The overwhelmingly common Atomizer path is a JSON/form body mutation. Avoid reparsing the
        // entire message in that case: it can cause a Montoya runtime to retain/recover the old body.
        // withBody(ByteArray) is the authoritative body replacement API and recalculates Content-Length.
        if (before.hasSeparator() && intended.hasSeparator() && before.head().equals(intended.head())) {
            return original.withBody(ByteArray.byteArray(utf8Bytes(intended.body())));
        }

        // Query/header mutations still require reconstructing the message. Re-apply the intended body
        // afterwards so a parser/reconstruction quirk cannot silently restore the original body.
        HttpRequest modified = HttpRequest.httpRequest(original.httpService(), ByteArray.byteArray(utf8Bytes(requestText)));
        if (modified == null) {
            throw new IllegalStateException("Montoya returned null while rebuilding the mutated request");
        }
        if (intended.hasSeparator()) {
            modified = modified.withBody(ByteArray.byteArray(utf8Bytes(intended.body())));
        }
        return modified;
    }

    /** Build the request and prove that the bytes/changed header fragment survived reconstruction. */
    public static BuildResult buildVerified(HttpRequest original, String requestText) {
        HttpRequest modified = fromUtf8Text(original, requestText);
        MessageParts before = splitMessage(original == null ? "" : original.toString());
        MessageParts intended = splitMessage(requestText == null ? "" : requestText);

        String intendedHex = intended.hasSeparator() ? utf8Hex(intended.body()) : "";
        String actualHex = bytesHex(modified == null ? null : modified.body());

        if (intended.hasSeparator()) {
            byte[] expectedBody = utf8Bytes(intended.body());
            byte[] actualBody = modified.body() == null ? new byte[0] : modified.body().getBytes();
            if (!Arrays.equals(expectedBody, actualBody)) {
                return new BuildResult(modified, false,
                        "wire verification failed: constructed request body bytes differ from the intended mutated body",
                        intendedHex, actualHex);
            }
        }

        // If the request line or headers were intentionally changed, confirm that the inserted/replaced
        // fragment survived Montoya reconstruction as well. Content-Length changes do not matter here.
        if (!before.head().equals(intended.head())) {
            String fragment = changedInsertion(before.head(), intended.head());
            String actualHead = splitMessage(modified.toString()).head();
            if (!fragment.isEmpty() && !actualHead.contains(fragment)) {
                return new BuildResult(modified, false,
                        "wire verification failed: mutated request-line/header fragment was lost during reconstruction",
                        intendedHex, actualHex);
            }
        }

        return new BuildResult(modified, true, "verified", intendedHex, actualHex);
    }

    private static String changedInsertion(String before, String after) {
        if (before == null) before = "";
        if (after == null) after = "";
        if (before.equals(after)) return "";
        int prefix = 0;
        int maxPrefix = Math.min(before.length(), after.length());
        while (prefix < maxPrefix && before.charAt(prefix) == after.charAt(prefix)) prefix++;

        int suffix = 0;
        int beforeRemaining = before.length() - prefix;
        int afterRemaining = after.length() - prefix;
        int maxSuffix = Math.min(beforeRemaining, afterRemaining);
        while (suffix < maxSuffix &&
                before.charAt(before.length() - 1 - suffix) == after.charAt(after.length() - 1 - suffix)) {
            suffix++;
        }
        return after.substring(prefix, after.length() - suffix);
    }

    private static MessageParts splitMessage(String text) {
        if (text == null) return new MessageParts("", "", false);
        int p = text.indexOf("\r\n\r\n");
        int sepLen = 4;
        if (p < 0) {
            p = text.indexOf("\n\n");
            sepLen = 2;
        }
        if (p < 0) return new MessageParts(text, "", false);
        return new MessageParts(text.substring(0, p + sepLen), text.substring(p + sepLen), true);
    }

    static byte[] utf8Bytes(String text) {
        return (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
    }

    static String utf8Hex(String text) {
        return HexFormat.of().formatHex(utf8Bytes(text));
    }

    static String bytesHex(ByteArray bytes) {
        if (bytes == null) return "";
        return HexFormat.of().formatHex(bytes.getBytes());
    }
}
