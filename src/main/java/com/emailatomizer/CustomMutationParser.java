package com.emailatomizer;

import java.util.ArrayList;
import java.util.List;

/** Parses one-payload-per-line custom mutation sets pasted into Test Builder. */
public final class CustomMutationParser {
    private CustomMutationParser() {}

    public static List<MutationCase> parse(String text) {
        List<MutationCase> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        int n = 1;
        for (String rawLine : text.split("\\R", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String template = decodeUnicodeEscapes(line);
            String id = String.format("CUSTOM-%03d", n++);
            out.add(new MutationCase(id, "Custom pasted mutations", "Custom pasted payload " + (n - 1),
                    "User-supplied one-line mutation template. Supports {EMAIL}, {LOCAL}, {DOMAIN}, {ALLOWED_DOMAIN}, " +
                            "{RECEIVER}, {RECEIVER_LOCAL}, {RECEIVER_DOMAIN}, and \\uXXXX escapes.",
                    containsReceiverToken(template), c -> render(template, c)));
        }
        return out;
    }

    static String render(String template, MutationContext c) {
        return template
                .replace("{EMAIL}", c.canonicalEmail())
                .replace("{LOCAL}", c.localPart())
                .replace("{DOMAIN}", c.allowedDomain())
                .replace("{ALLOWED_DOMAIN}", c.allowedDomain())
                .replace("{RECEIVER}", c.receiverEmail())
                .replace("{RECEIVER_LOCAL}", c.receiverLocalPart())
                .replace("{RECEIVER_DOMAIN}", c.receiverDomain())
                .replace("{COLLAB_HOST}", c.collaboratorHost())
                .replace("{CORRELATION}", c.correlationId());
    }

    private static boolean containsReceiverToken(String s) {
        return s.contains("{RECEIVER}") || s.contains("{RECEIVER_LOCAL}") ||
                s.contains("{RECEIVER_DOMAIN}") || s.contains("{COLLAB_HOST}");
    }

    /** Decode Java-style Unicode escapes so copied strings such as gm\\u1d43il work directly. */
    static String decodeUnicodeEscapes(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '\\' && i + 5 < input.length() && input.charAt(i + 1) == 'u') {
                int j = i + 2;
                while (j < input.length() && input.charAt(j) == 'u') j++;
                if (j + 4 <= input.length()) {
                    String hex = input.substring(j, j + 4);
                    try {
                        out.append((char) Integer.parseInt(hex, 16));
                        i = j + 3;
                        continue;
                    } catch (NumberFormatException ignored) {
                        // Preserve invalid escape literally.
                    }
                }
            }
            out.append(input.charAt(i));
        }
        return out.toString();
    }
}
