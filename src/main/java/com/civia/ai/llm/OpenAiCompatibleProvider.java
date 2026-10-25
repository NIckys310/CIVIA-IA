package com.civia.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Proveedor para cualquier API con el formato de OpenAI (chat completions): Groq, Cerebras,
 * OpenRouter… Sirve para usar modelos abiertos con planes gratuitos.
 *
 * <p>Solo texto: si la pregunta trae imágenes o PDF (análisis de planos), responde vacío y CIVIA
 * usa el motor local, que sí los procesa. Ante límites de uso (429), clave inválida o falta de
 * conexión también responde vacío, sin lanzar errores.
 */
public final class OpenAiCompatibleProvider implements LlmProvider {

    private static final Logger LOG = Logger.getLogger(OpenAiCompatibleProvider.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Turnos previos que se envían: los planes gratuitos limitan los tokens por minuto. */
    static final int MAX_HISTORY_TURNS = 8;
    /** Largo máximo de cada turno previo (caracteres). */
    static final int MAX_TURN_CHARS = 2_000;

    private final String label;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final String reasoningEffort;
    private final HttpClient http;

    public OpenAiCompatibleProvider(String label, String baseUrl, String apiKey, String model, int maxTokens, String reasoningEffort) {
        this.label = label;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.reasoningEffort = reasoningEffort;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /** Groq: {@code GROQ_API_KEY}, modelo {@code CIVIA_GROQ_MODEL} (por defecto gpt-oss-120b). */
    public static Optional<LlmProvider> groq() {
        return fromEnv("Groq", "GROQ_API_KEY", "https://api.groq.com/openai/v1", "CIVIA_GROQ_MODEL", "openai/gpt-oss-120b");
    }

    /** Cerebras: {@code CEREBRAS_API_KEY}, modelo {@code CIVIA_CEREBRAS_MODEL}. */
    public static Optional<LlmProvider> cerebras() {
        return fromEnv("Cerebras", "CEREBRAS_API_KEY", "https://api.cerebras.ai/v1", "CIVIA_CEREBRAS_MODEL", "gpt-oss-120b");
    }

    /** OpenRouter (modelos «:free»): {@code OPENROUTER_API_KEY}, modelo {@code CIVIA_OPENROUTER_MODEL}. */
    public static Optional<LlmProvider> openRouter() {
        return fromEnv("OpenRouter", "OPENROUTER_API_KEY", "https://openrouter.ai/api/v1", "CIVIA_OPENROUTER_MODEL", "openai/gpt-oss-120b:free");
    }

    private static Optional<LlmProvider> fromEnv(String label, String keyVar, String baseUrl, String modelVar, String defaultModel) {
        String key = System.getenv(keyVar);
        if (key == null || key.isBlank()) return Optional.empty();
        String model = Optional.ofNullable(System.getenv(modelVar)).filter(s -> !s.isBlank()).orElse(defaultModel);
        int maxTokens = parseInt(System.getenv("CIVIA_LLM_MAX_TOKENS"), 2_048);
        return Optional.of(new OpenAiCompatibleProvider(label, baseUrl, key.strip(), model, maxTokens, effort(System.getenv("CIVIA_AI_EFFORT"))));
    }

    /** Los modelos gpt-oss aceptan low, medium o high; xhigh y max se tratan como high. */
    static String effort(String value) {
        if (value == null || value.isBlank()) return "medium";
        return switch (value.strip().toLowerCase()) {
            case "low" -> "low";
            case "medium" -> "medium";
            default -> "high";
        };
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Math.max(256, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String name() {
        return label + " (" + model + ")";
    }

    @Override
    public Optional<String> complete(Request request) {
        if (!request.attachments().isEmpty()) return Optional.empty();
        try {
            HttpResponse<String> res = http.send(httpRequest(body(request, false)), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) return failed(res.statusCode(), res.body());
            JsonNode content = JSON.readTree(res.body()).path("choices").path(0).path("message").path("content");
            return clean(content.asText(""));
        } catch (IOException e) {
            return offline(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Entrega el texto a medida que llega (Server-Sent Events del formato de OpenAI). */
    @Override
    public Optional<String> stream(Request request, Consumer<String> onDelta) {
        if (!request.attachments().isEmpty()) return Optional.empty();
        try {
            HttpResponse<java.io.InputStream> res = http.send(httpRequest(body(request, true)), HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() / 100 != 2) {
                try (var in = res.body()) {
                    return failed(res.statusCode(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).strip();
                    if (data.equals("[DONE]")) break;
                    if (data.isEmpty()) continue;
                    JsonNode event = JSON.readTree(data);
                    if (event.has("error")) {
                        LOG.warning(label + " cortó la respuesta: " + event.path("error").path("message").asText());
                        break;
                    }
                    // Solo el texto de la respuesta; el razonamiento interno (delta.reasoning) no se muestra.
                    String delta = event.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        text.append(delta);
                        onDelta.accept(delta);
                    }
                }
            }
            return clean(text.toString());
        } catch (IOException e) {
            return offline(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    String body(Request request, boolean stream) throws IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("stream", stream);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.4);
        if (model.contains("gpt-oss") && reasoningEffort != null) body.put("reasoning_effort", reasoningEffort);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.system());
        List<Turn> history = request.history();
        for (Turn turn : history.subList(Math.max(0, history.size() - MAX_HISTORY_TURNS), history.size())) {
            String t = turn.text() == null ? "" : turn.text();
            if (t.length() > MAX_TURN_CHARS) t = t.substring(0, MAX_TURN_CHARS) + "…";
            messages.addObject().put("role", "assistant".equals(turn.role()) ? "assistant" : "user").put("content", t);
        }
        messages.addObject().put("role", "user").put("content", request.prompt());
        return JSON.writeValueAsString(body);
    }

    private HttpRequest httpRequest(String body) {
        return HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("X-Title", "CIVIA AI")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private static Optional<String> clean(String text) {
        String t = text == null ? "" : text.strip();
        return t.isEmpty() ? Optional.empty() : Optional.of(t);
    }

    private Optional<String> failed(int status, String body) {
        String detail = body == null ? "" : body.substring(0, Math.min(200, body.length()));
        switch (status) {
            case 401, 403 -> LOG.warning("La clave de " + label + " no es válida; se usa el siguiente motor.");
            case 429 -> LOG.warning("Límite gratuito de " + label + " alcanzado; se usa el siguiente motor.");
            default -> LOG.warning(label + " respondió " + status + ": " + detail);
        }
        return Optional.empty();
    }

    private Optional<String> offline(IOException e) {
        LOG.log(Level.WARNING, "Sin conexión con " + label + ": " + e.getMessage());
        return Optional.empty();
    }
}
