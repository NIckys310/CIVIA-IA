package com.civia.ai;

/**
 * Recomendación accionable. {@code priority}: "alta", "media" o "baja".
 * {@code action} es una pista para la interfaz (p. ej. "upload-plan", "open-library:escaleras").
 */
public record Recommendation(String priority, String title, String detail, String action) {

    public static Recommendation high(String title, String detail, String action) {
        return new Recommendation("alta", title, detail, action);
    }

    public static Recommendation medium(String title, String detail, String action) {
        return new Recommendation("media", title, detail, action);
    }

    public static Recommendation low(String title, String detail, String action) {
        return new Recommendation("baja", title, detail, action);
    }
}
