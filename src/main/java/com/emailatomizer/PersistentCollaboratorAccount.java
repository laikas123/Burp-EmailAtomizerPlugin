package com.emailatomizer;

import java.time.Instant;

/** One user-generated Collaborator-backed test-account address kept stable until explicitly regenerated. */
public record PersistentCollaboratorAccount(
        String slot,
        String correlationId,
        String collaboratorRoot,
        String localPart,
        String domainLabel,
        String email,
        Instant created) {
}
