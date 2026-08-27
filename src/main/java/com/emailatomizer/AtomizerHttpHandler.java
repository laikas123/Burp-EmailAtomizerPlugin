package com.emailatomizer;

import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.requests.HttpRequest;

import static burp.api.montoya.http.handler.RequestToBeSentAction.continueWith;
import static burp.api.montoya.http.handler.ResponseReceivedAction.continueWith;

public final class AtomizerHttpHandler implements HttpHandler {
    private final AtomizerState state;
    private final AtomizerPanel panel;

    public AtomizerHttpHandler(AtomizerState state, AtomizerPanel panel) {
        this.state = state;
        this.panel = panel;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent request) {
        // Always allow passive discovery of genuine user traffic, even while a matrix is running.
        // Atomizer-generated matrix requests are explicitly marked/suppressed inside observeRequest().
        state.observeRequest(request);

        if (state.isMatrixSending() || !panel.liveEnabled()) {
            return continueWith(request);
        }
        if (panel.scopeOnly() && !request.isInScope()) {
            return continueWith(request);
        }
        if (panel.skipGet() && (request.method().equalsIgnoreCase("GET") || request.method().equalsIgnoreCase("HEAD"))) {
            return continueWith(request);
        }

        String canonical = panel.canonicalEmail();
        MutationCase mutation = panel.selectedMutation();
        if (canonical.isBlank() || mutation == null || !state.requestContainsCanonical(request.toString(), canonical)) {
            return continueWith(request);
        }

        try {
            AtomizerState.RenderedMutation rendered = state.render(mutation, canonical);
            RequestMutator.Replacement replacement = RequestMutator.replaceCanonical(request.toString(), canonical, rendered.email());
            if (!replacement.changed()) return continueWith(request);

            RequestWireBuilder.BuildResult wireBuild = RequestWireBuilder.buildVerified(request, replacement.text());
            HttpRequest modified = wireBuild.request();

            AtomResult result = state.record(rendered, request.method(), request.url());
            result.requestText = modified.toString();
            result.requestBodyHex = wireBuild.actualBodyHex();
            result.intendedRequestBodyHex = wireBuild.intendedBodyHex();
            result.wireVerification = wireBuild.verified() ? "PASS" : "BLOCKED";
            result.logicalEmailUtf8Hex = RequestWireBuilder.utf8Hex(rendered.email());
            if (!wireBuild.verified()) {
                result.signal = "BLOCKED: wire verification failed";
                result.notes.set(wireBuild.detail());
                state.fireResultsChanged();
                panel.setStatus("Live mutation NOT SENT: " + wireBuild.detail());
                return continueWith(request);
            }
            try { result.request = modified.copyToTempFile(); } catch (Throwable ignored) { result.request = modified; }
            Annotations annotations = request.annotations().withNotes(
                    "Email Atomizer " + mutation.id() + " -> " + rendered.email() + " [" + result.correlationId + "]");
            panel.setStatus("Live mutation: " + mutation.id() + " -> " + rendered.email());
            return continueWith(modified, annotations);
        } catch (Throwable t) {
            panel.setStatus("Live mutation failed: " + t.getMessage());
            return continueWith(request);
        }
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived response) {
        // Capture request/response pairs for passive discoveries as well as matrix/live results.
        state.observeResponse(response);
        AtomResult result = state.matchResultForRequest(response.initiatingRequest().toString());
        if (result != null) {
            result.responseText = response.toString();
            try { result.response = response.copyToTempFile(); } catch (Throwable ignored) { result.response = response; }
            result.httpStatus = Short.toString(response.statusCode());
            result.responseLength = Integer.toString(response.bodyToString().length());
            result.refreshSignal();
            state.fireResultsChanged();
        }
        return continueWith(response);
    }
}
