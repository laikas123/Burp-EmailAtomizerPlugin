package com.emailatomizer;

import java.time.ZonedDateTime;

/** Collaborator interaction that is not attached to a matrix result (persistent account or Mutation Strings payload). */
public record CollaboratorInboxEvent(
        String correlationId,
        String source,
        String label,
        String generatedAddress,
        String collaboratorRoot,
        CollaboratorEvidence evidence) {

    public ZonedDateTime timestamp() {
        return evidence == null ? null : evidence.timestamp();
    }

    public String type() {
        return evidence == null ? "" : evidence.type();
    }

    public String smtpRecipient() {
        return evidence == null ? "" : evidence.smtpRecipient();
    }

    public String details() {
        StringBuilder sb = new StringBuilder();
        sb.append("Correlation: ").append(correlationId == null ? "" : correlationId).append('\n');
        sb.append("Source: ").append(source == null ? "" : source).append('\n');
        if (label != null && !label.isBlank()) sb.append("Label: ").append(label).append('\n');
        if (generatedAddress != null && !generatedAddress.isBlank()) sb.append("Generated address: ").append(generatedAddress).append('\n');
        if (collaboratorRoot != null && !collaboratorRoot.isBlank()) sb.append("Collaborator root: ").append(collaboratorRoot).append('\n');
        if (evidence != null) sb.append('\n').append(evidence.summary());
        return sb.toString();
    }
}
