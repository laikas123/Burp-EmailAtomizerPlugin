package com.emailatomizer;

public record EmailCandidate(
        String email,
        String location,
        String parameter,
        String representation,
        int rawOffset,
        String rawToken) {

    public EmailCandidate(String email, String location, String parameter, String representation) {
        this(email, location, parameter, representation, -1, "");
    }

    public boolean hasExactRawTarget() {
        return rawOffset >= 0 && rawToken != null && !rawToken.isBlank();
    }

    @Override
    public String toString() {
        String where = parameter == null || parameter.isBlank() ? location : location + " / " + parameter;
        return email + "  [" + where + (representation == null || representation.isBlank() ? "" : ", " + representation) + "]";
    }
}
