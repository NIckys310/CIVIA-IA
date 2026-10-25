package com.civia.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Proveedor con formato OpenAI (Groq, Cerebras…) contra un servidor falso local. */
class OpenAiCompatibleProviderTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "";
    private volatile String contentType = "application/json";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", contentType);
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiCompatibleProvider provider() {
        return new OpenAiCompatibleProvider("Groq", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "gsk_test", "openai/gpt-oss-120b", 1024, "medium");
    }

    private static LlmProvider.Request req(List<LlmProvider.Turn> history, List<LlmProvider.Attachment> files) {
        return new LlmProvider.Request("Eres CIVIA.", history, "¿Pendiente máxima de una rampa?", files);
    }

    @Test
    void completesAndSendsExpectedRequest() {
        reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"  La pendiente máxima suele ser 8 %. \"}}]}";
        Optional<String> text = provider().complete(req(List.of(), List.of()));
        assertEquals(Optional.of("La pendiente máxima suele ser 8 %."), text);
        String body = lastBody.get();
        assertTrue(body.contains("\"model\":\"openai/gpt-oss-120b\""));
        assertTrue(body.contains("\"reasoning_effort\":\"medium\""));
        assertTrue(body.contains("\"role\":\"system\""));
    }

    @Test
    void streamsDeltasAndIgnoresReasoning() {
        contentType = "text/event-stream";
        reply = """
                data: {"choices":[{"delta":{"reasoning":"pienso…"}}]}

                data: {"choices":[{"delta":{"content":"Hasta "}}]}

                data: {"choices":[{"delta":{"content":"8 %."}}]}

                data: [DONE]

                """;
        List<String> deltas = new ArrayList<>();
        Optional<String> text = provider().stream(req(List.of(), List.of()), deltas::add);
        assertEquals(List.of("Hasta ", "8 %."), deltas);
        assertEquals(Optional.of("Hasta 8 %."), text);
    }

    @Test
    void rateLimitReturnsEmpty() {
        status = 429;
        reply = "{\"error\":{\"message\":\"Rate limit\"}}";
        assertTrue(provider().complete(req(List.of(), List.of())).isEmpty());
        assertTrue(provider().stream(req(List.of(), List.of()), d -> { }).isEmpty());
    }

    @Test
    void attachmentsGoToLocalEngine() {
        reply = "{\"choices\":[{\"message\":{\"content\":\"no debería llamarse\"}}]}";
        var pdf = new LlmProvider.Attachment("application/pdf", new byte[] {1, 2, 3}, "plano.pdf");
        assertTrue(provider().complete(req(List.of(), List.of(pdf))).isEmpty());
        assertEquals(null, lastBody.get());
    }

    @Test
    void historyIsTrimmedForFreeTierLimits() throws IOException {
        List<LlmProvider.Turn> history = new ArrayList<>();
        for (int i = 0; i < 30; i++) history.add(new LlmProvider.Turn(i % 2 == 0 ? "user" : "assistant", "turno " + i + " " + "x".repeat(5_000)));
        String body = provider().body(req(history, List.of()), false);
        assertFalse(body.contains("turno 21 "));
        assertTrue(body.contains("turno 22 "));
        assertTrue(body.contains("turno 29 "));
        assertFalse(body.contains("x".repeat(OpenAiCompatibleProvider.MAX_TURN_CHARS + 1)));
    }

    @Test
    void effortMapping() {
        assertEquals("high", OpenAiCompatibleProvider.effort("xhigh"));
        assertEquals("low", OpenAiCompatibleProvider.effort("LOW"));
        assertEquals("medium", OpenAiCompatibleProvider.effort(null));
    }

    @Test
    void fallbackUsesNextProviderOnlyBeforeAnyText() {
        LlmProvider failing = fake("A", Optional.empty(), List.of());
        LlmProvider working = fake("B", Optional.of("respuesta B"), List.of("respuesta ", "B"));
        LlmProvider chain = FallbackProvider.of(List.of(failing, working)).orElseThrow();
        assertEquals("A", chain.name());
        assertEquals(Optional.of("respuesta B"), chain.complete(req(List.of(), List.of())));
        List<String> deltas = new ArrayList<>();
        assertEquals(Optional.of("respuesta B"), chain.stream(req(List.of(), List.of()), deltas::add));
        assertEquals(List.of("respuesta ", "B"), deltas);

        // Si el primero ya envió texto, no se mezcla con otra respuesta.
        LlmProvider partial = fake("P", Optional.empty(), List.of("medio "));
        List<String> mixed = new ArrayList<>();
        FallbackProvider.of(List.of(partial, working)).orElseThrow().stream(req(List.of(), List.of()), mixed::add);
        assertEquals(List.of("medio "), mixed);

        assertTrue(FallbackProvider.of(List.of()).isEmpty());
    }

    private static LlmProvider fake(String name, Optional<String> result, List<String> deltas) {
        return new LlmProvider() {
            public boolean isAvailable() { return true; }
            public String name() { return name; }
            public Optional<String> complete(Request r) { return result; }
            public Optional<String> stream(Request r, java.util.function.Consumer<String> onDelta) {
                deltas.forEach(onDelta);
                return result;
            }
        };
    }
}
