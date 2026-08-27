package com.emailatomizer;

public record MutationCase(
        String id,
        String family,
        String label,
        String intent,
        boolean collaboratorCapable,
        WireMode wireMode,
        Generator generator) {

    /**
     * Describes request-transport behavior that cannot be represented by a decoded Java string alone.
     * STANDARD keeps the historical behavior. JSON modes are applied only to an exact JSON value target;
     * otherwise the case is skipped instead of silently degenerating to another payload.
     */
    public enum WireMode {
        STANDARD,
        JSON_UNICODE_ESCAPED,
        JSON_DOUBLE_UNICODE_ESCAPED,
        JSON_ESCAPE_AT,
        JSON_ESCAPE_DOT,
        JSON_ESCAPE_PLUS,
        JSON_DUPLICATE_KEY_IDENTITY_FIRST,
        JSON_DUPLICATE_KEY_MUTATION_FIRST,
        JSON_ESCAPED_KEY_IDENTITY_FIRST,
        JSON_ESCAPED_KEY_MUTATION_FIRST
    }

    public MutationCase(String id, String family, String label, String intent,
                        boolean collaboratorCapable, Generator generator) {
        this(id, family, label, intent, collaboratorCapable, WireMode.STANDARD, generator);
    }

    @FunctionalInterface
    public interface Generator {
        String generate(MutationContext context);
    }

    @Override
    public String toString() {
        return family + " / " + label;
    }
}
