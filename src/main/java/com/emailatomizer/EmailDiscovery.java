package com.emailatomizer;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public final class EmailDiscovery {
    public final String id;
    public final Instant firstSeen = Instant.now();
    public volatile Instant lastSeen = firstSeen;
    public final AtomicInteger seenCount = new AtomicInteger(1);
    public final String method;
    public final String url;
    public final String host;
    public final String path;
    public final String operation;
    public final int occurrenceIndex;
    public final EmailCandidate candidate;
    public volatile HttpRequest request;
    public volatile HttpResponse response;

    public EmailDiscovery(String id, HttpRequest request, EmailCandidate candidate,
                          String method, String url, String host, String path, String operation, int occurrenceIndex) {
        this.id = id;
        this.request = request;
        this.candidate = candidate;
        this.method = method;
        this.url = url;
        this.host = host;
        this.path = path;
        this.operation = operation == null ? "" : operation;
        this.occurrenceIndex = occurrenceIndex;
    }

    public void observeAgain(HttpRequest latestRequest) {
        lastSeen = Instant.now();
        seenCount.incrementAndGet();
        request = latestRequest;
    }

    public void observeResponse(HttpResponse latestResponse) {
        this.response = latestResponse;
    }
}
