package com.emailatomizer;

import java.time.ZonedDateTime;

/** Immutable snapshot of one Burp Collaborator interaction tied to a mutation result. */
public record CollaboratorEvidence(
        String interactionId,
        String sourceRole,
        String type,
        ZonedDateTime timestamp,
        String clientIp,
        int clientPort,
        String protocol,
        String smtpRecipient,
        String smtpMessage,
        String smtpBody,
        String rawDetails) {

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Interaction ID: ").append(interactionId).append('\n');
        if (sourceRole != null && !sourceRole.isBlank()) sb.append("Correlation role: ").append(sourceRole).append('\n');
        sb.append("Type: ").append(type).append('\n');
        if (timestamp != null) sb.append("Timestamp: ").append(timestamp).append('\n');
        if (clientIp != null && !clientIp.isBlank()) {
            sb.append("Client: ").append(clientIp);
            if (clientPort > 0) sb.append(':').append(clientPort);
            sb.append('\n');
        }
        if (protocol != null && !protocol.isBlank()) sb.append("Protocol: ").append(protocol).append('\n');
        if (smtpRecipient != null && !smtpRecipient.isBlank()) sb.append("SMTP envelope recipient: ").append(smtpRecipient).append('\n');

        if (smtpMessage != null && !smtpMessage.isBlank()) {
            sb.append("\n--- SMTP DATA / message ---\n").append(smtpMessage.trim()).append('\n');
        }
        if (smtpBody != null && !smtpBody.isBlank() && !smtpBody.trim().equals(smtpMessage == null ? "" : smtpMessage.trim())) {
            sb.append("\n--- Parsed message body ---\n").append(smtpBody.trim()).append('\n');
        }
        if (rawDetails != null && !rawDetails.isBlank()) {
            sb.append("\n--- Raw Collaborator details ---\n").append(rawDetails.trim()).append('\n');
        }
        return sb.toString();
    }
}
