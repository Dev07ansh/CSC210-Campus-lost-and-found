package com.csc210.backend.model;

/**
 * The only staffed collection locations supported by the campus workflow.
 * The labels, rather than enum identifiers, are persisted so the database is
 * understandable to staff without an application-specific lookup.
 */
public enum CollectionDesk {
    UNIVERSITY_CENTRE_RECEPTION("University Centre Reception"),
    GATE_12("Gate 12");

    private final String label;

    CollectionDesk(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Returns the canonical label, or {@code null} when the value is unsupported. */
    public static String normalize(String value) {
        if (value == null) return null;
        String candidate = value.trim();
        for (CollectionDesk desk : values()) {
            if (desk.label.equals(candidate)) return desk.label;
        }
        return null;
    }

    public static boolean isSupported(String value) {
        return normalize(value) != null;
    }
}
