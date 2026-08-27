package com.emailatomizer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record MutationContext(
        String canonicalEmail,
        String localPart,
        String allowedDomain,
        String collaboratorHost,
        String correlationId,
        String receiverEmail) {

    public String receiverLocalPart() {
        int at = receiverEmail == null ? -1 : receiverEmail.lastIndexOf('@');
        return at > 0 ? receiverEmail.substring(0, at) : "atom";
    }

    public String receiverDomain() {
        int at = receiverEmail == null ? -1 : receiverEmail.lastIndexOf('@');
        return at > 0 && at < receiverEmail.length() - 1
                ? receiverEmail.substring(at + 1)
                : collaboratorHost;
    }

    public String receiverLocalPartQEncoded() {
        byte[] bytes = receiverLocalPart().getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) out.append(String.format("=%02x", b & 0xff));
        return out.toString();
    }

    public String receiverLocalPartBase64() {
        return Base64.getEncoder().encodeToString(receiverLocalPart().getBytes(StandardCharsets.UTF_8));
    }
}
