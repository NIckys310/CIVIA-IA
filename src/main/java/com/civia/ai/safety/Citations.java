package com.civia.ai.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citas obligatorias en las respuestas del asistente (RAG).
 *
 * <ul>
 *   <li>Cada afirmación se apoya en fragmentos recuperados y los cita con marcadores {@code [n]}.</li>
 *   <li>Un marcador que no corresponde a ningún fragmento es una cita inventada: se rechaza.</li>
 *   <li>Sin soporte, la respuesta lo dice explícitamente ({@link #NO_SUPPORT_MESSAGE}).</li>
 * </ul>
 */
public final class Citations {

    public static final String NO_SUPPORT_MESSAGE =
            "No encontré soporte en las fuentes disponibles para responder con seguridad. "
                    + "Indica la normativa o el documento que debo consultar.";

    private static final Pattern MARKER = Pattern.compile("\\[(\\d{1,3})]");

    private Citations() {}

    /** Fragmento de la biblioteca con su procedencia. */
    public record SourceChunk(String chunkId, String title, String section, String text) {
        public String label() {
            return section == null || section.isBlank() ? title : title + " · " + section;
        }
    }

    public record Citation(int marker, String chunkId, String label) {}

    public record CheckedAnswer(String text, List<Citation> citations, boolean supported) {}

    public static final class CitationException extends IllegalArgumentException {
        public CitationException(String message) {
            super(message);
        }
    }

    /** Texto para el prompt: cada fragmento con su número de cita [n] y su procedencia. */
    public static String numberSources(List<SourceChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) sb.append("\n\n");
            SourceChunk c = chunks.get(i);
            sb.append('[').append(i + 1).append("] ").append(c.label()).append('\n').append(c.text());
        }
        return sb.toString();
    }

    /** Valida las citas de una respuesta y añade el aviso de revisión profesional. */
    public static CheckedAnswer check(String answer, List<SourceChunk> chunks) {
        String text = answer.strip();
        if (text.startsWith(NO_SUPPORT_MESSAGE.substring(0, 40))) {
            return new CheckedAnswer(text + "\n\n" + PrudentLanguage.DISCLAIMER, List.of(), false);
        }
        List<Integer> markers = new ArrayList<>();
        Matcher m = MARKER.matcher(text);
        while (m.find()) {
            markers.add(Integer.parseInt(m.group(1)));
        }
        if (markers.isEmpty()) {
            throw new CitationException("La respuesta no cita ninguna fuente");
        }
        TreeSet<Integer> invalid = new TreeSet<>();
        for (int n : markers) {
            if (n < 1 || n > chunks.size()) invalid.add(n);
        }
        if (!invalid.isEmpty()) {
            throw new CitationException("Citas inexistentes: " + invalid);
        }
        List<Citation> citations = new ArrayList<>();
        for (int n : new TreeSet<>(markers)) {
            SourceChunk c = chunks.get(n - 1);
            citations.add(new Citation(n, c.chunkId(), c.label()));
        }
        return new CheckedAnswer(text + "\n\n" + PrudentLanguage.DISCLAIMER, citations, true);
    }
}
