package com.civia.ai.llm;

import java.util.List;
import java.util.Optional;

/** Proveedor de lenguaje intercambiable. Sin proveedor configurado, CIVIA usa su motor local. */
public interface LlmProvider {

    /** Turno previo de la conversación. {@code role} es "user" o "assistant". */
    record Turn(String role, String text) {}

    /** Archivo que acompaña a la pregunta (imagen de un plano, PDF…). */
    record Attachment(String mediaType, byte[] data, String title) {
        public boolean isImage() {
            return mediaType != null && mediaType.startsWith("image/");
        }

        public boolean isPdf() {
            return "application/pdf".equals(mediaType);
        }
    }

    record Request(String system, List<Turn> history, String prompt, List<Attachment> attachments) {
        public Request {
            history = history == null ? List.of() : List.copyOf(history);
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    boolean isAvailable();

    String name();

    /** Respuesta del modelo, o vacío si no hubo respuesta utilizable (error, rechazo…). */
    Optional<String> complete(Request request);
}
