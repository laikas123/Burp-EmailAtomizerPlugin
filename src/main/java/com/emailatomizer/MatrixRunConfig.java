package com.emailatomizer;

public record MatrixRunConfig(
        int delayMs,
        int collaboratorCollectionWindowMs,
        int maxTests,
        String stopStatusCodes,
        String stopResponseText,
        boolean respectRetryAfter,
        int fallbackRateLimitDelayMs,
        boolean deliverySentinelsEnabled,
        int deliverySentinelEvery) {
}
