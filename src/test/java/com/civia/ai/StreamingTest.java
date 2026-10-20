package com.civia.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civia.ai.llm.LlmProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class StreamingTest {

    /** Proveedor falso que entrega la respuesta en tres fragmentos. */
    private static final class FakeStreamingProvider implements LlmProvider {
        String lastSystem;

        @Override public boolean isAvailable() { return true; }
        @Override public String name() { return "Fake"; }
        @Override public Optional<String> complete(Request request) { return Optional.of("Una zapata reparte la carga."); }

        @Override
        public Optional<String> stream(Request request, Consumer<String> onDelta) {
            lastSystem = request.system();
            List.of("Una zapata ", "reparte ", "la carga.").forEach(onDelta);
            return Optional.of("Una zapata reparte la carga.");
        }
    }

    @Test
    void copilotForwardsEveryDeltaAndReturnsTheFullAnswer() {
        FakeStreamingProvider fake = new FakeStreamingProvider();
        CiviaAi ai = new CiviaAi(Optional.of(fake));
        List<String> deltas = new ArrayList<>();
        var answer = ai.assistant().ask("¿Qué es una zapata?", List.of(), null, List.of(), deltas::add);
        assertEquals(List.of("Una zapata ", "reparte ", "la carga."), deltas);
        assertTrue(answer.text().startsWith("Una zapata reparte la carga."));
        assertEquals("Fake", answer.engine());
        assertTrue(fake.lastSystem.contains("CIVIA Copilot"));
    }

    @Test
    void defaultStreamEmitsTheCompleteTextOnce() {
        LlmProvider plain = new LlmProvider() {
            @Override public boolean isAvailable() { return true; }
            @Override public String name() { return "Plain"; }
            @Override public Optional<String> complete(Request r) { return Optional.of("Una zapata reparte la carga."); }
        };
        List<String> deltas = new ArrayList<>();
        plain.stream(new LlmProvider.Request("s", List.of(), "q", List.of()), deltas::add);
        assertEquals(List.of("Una zapata reparte la carga."), deltas);
    }

    @Test
    void localEngineDoesNotEmitDeltas() {
        List<String> deltas = new ArrayList<>();
        var answer = new CiviaAi(Optional.empty()).assistant().ask("¿Qué es una zapata?", List.of(), null, List.of(), deltas::add);
        assertTrue(deltas.isEmpty());
        assertEquals("CIVIA local", answer.engine());
    }
}
