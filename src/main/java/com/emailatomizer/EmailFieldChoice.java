package com.emailatomizer;

/** UI wrapper that gives each detected email occurrence a stable, human-readable 1-based index. */
public record EmailFieldChoice(int index, EmailCandidate candidate) {
    public static EmailFieldChoice none() {
        return new EmailFieldChoice(0, null);
    }

    public boolean isNone() {
        return candidate == null;
    }

    @Override
    public String toString() {
        if (isNone()) return "None — only the primary occurrence is changed";
        return "#" + index + " — " + candidate;
    }
}
