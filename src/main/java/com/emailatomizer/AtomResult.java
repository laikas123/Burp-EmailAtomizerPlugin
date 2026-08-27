package com.emailatomizer;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class AtomResult {
    public final Instant created = Instant.now();
    public final String correlationId;
    public final String mutationId;
    public final String family;
    public final String label;
    public final String method;
    public final String url;
    public final String mutatedEmail;
    public final String wireMode;
    public final String collaboratorHost;

    public volatile String httpStatus = "";
    public volatile String responseLength = "";
    public volatile String differential = "";
    public volatile String interaction = "";
    public volatile String smtpRecipient = "";
    public volatile String smtpMessage = "";
    public volatile String smtpBody = "";
    public volatile String secondaryReceiverEmail = "";
    public final CopyOnWriteArrayList<CollaboratorEvidence> collaboratorEvidence = new CopyOnWriteArrayList<>();
    public volatile String signal = "";
    /** Full mutated request used for this result, suitable for UI inspection. */
    public volatile String requestText = "";
    /** Hex of the exact request body bytes handed to Burp after mutation. */
    public volatile String requestBodyHex = "";
    /** Hex of the body bytes Atomizer intended to put on the wire before Montoya reconstruction. */
    public volatile String intendedRequestBodyHex = "";
    /** PASS/BLOCKED audit marker for the post-construction wire verification. */
    public volatile String wireVerification = "";
    /** Selected target metadata captured so CSV evidence can identify the exact occurrence. */
    public volatile String targetLocation = "";
    public volatile String targetParameter = "";
    public volatile String targetRawOffset = "";
    /** UTF-8 hex of the logical mutated email, useful for auditing raw-Unicode cases. */
    public volatile String logicalEmailUtf8Hex = "";
    /** Native Burp request object for message-editor/context-menu integration. */
    public volatile HttpRequest request;
    /** Full HTTP response associated with this result, when available. */
    public volatile String responseText = "";
    /** Native Burp response object for message-editor/context-menu integration. */
    public volatile HttpResponse response;
    public final AtomicReference<String> notes = new AtomicReference<>("");

    public AtomResult(String correlationId, MutationCase mutation, String method, String url,
                      String mutatedEmail, String collaboratorHost) {
        this.correlationId = correlationId;
        this.mutationId = mutation.id();
        this.family = mutation.family();
        this.label = mutation.label();
        this.method = method;
        this.url = url;
        this.mutatedEmail = mutatedEmail;
        this.wireMode = mutation.wireMode().name();
        this.collaboratorHost = collaboratorHost;
    }

    public String collaboratorTranscript() {
        if (collaboratorEvidence.isEmpty()) return "[No Collaborator interaction received for this result yet]";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < collaboratorEvidence.size(); i++) {
            if (i > 0) sb.append("\n============================================================\n\n");
            sb.append(collaboratorEvidence.get(i).summary());
        }
        return sb.toString();
    }


    public int interactionCount() {
        return collaboratorEvidence.size();
    }

    /** Human-friendly aggregate that preserves multiplicity, e.g. DNS×2 + SMTP. */
    public String interactionSummary() {
        if (collaboratorEvidence.isEmpty()) return interaction == null ? "" : interaction;
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (CollaboratorEvidence evidence : collaboratorEvidence) {
            String type = evidence.type() == null || evidence.type().isBlank() ? "UNKNOWN" : evidence.type();
            counts.merge(type, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : counts.entrySet()) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(entry.getKey());
            if (entry.getValue() > 1) sb.append('×').append(entry.getValue());
        }
        return sb.toString();
    }

    /** Ordered protocol sequence, useful for seeing DNS → DNS → SMTP behavior. */
    public String interactionSequence() {
        if (collaboratorEvidence.isEmpty()) return interaction == null ? "" : interaction.replace("+", " → ");
        StringBuilder sb = new StringBuilder();
        for (CollaboratorEvidence evidence : collaboratorEvidence) {
            if (sb.length() > 0) sb.append(" → ");
            sb.append(evidence.type() == null || evidence.type().isBlank() ? "UNKNOWN" : evidence.type());
        }
        return sb.toString();
    }

    public void refreshSignal() {
        if ("Delivery sentinel".equals(family)) {
            if (!smtpRecipient.isBlank()) {
                signal = "SENTINEL OK: SMTP observed";
            } else if (!collaboratorEvidence.isEmpty() || !interaction.isBlank()) {
                signal = "SENTINEL PARTIAL: " + interactionSummary();
            } else if (signal.isBlank() || signal.startsWith("SENTINEL")) {
                signal = "SENTINEL WARNING: no OAST yet";
            }
        } else if (!smtpRecipient.isBlank()) {
            signal = "HIGH: SMTP observed";
        } else if (!collaboratorEvidence.isEmpty() || !interaction.isBlank()) {
            signal = "OAST: " + interactionSummary();
        } else if (mutationId.equals("BASE-001") && differential.equals("baseline")) {
            signal = "control";
        } else if (!differential.isBlank() && !differential.equals("same") && !differential.equals("baseline")) {
            signal = "HTTP differential";
        } else if (signal.isBlank()) {
            signal = "";
        }
    }
}
