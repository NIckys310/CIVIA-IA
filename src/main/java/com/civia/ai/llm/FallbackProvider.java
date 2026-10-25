package com.civia.ai.llm;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Cadena de proveedores: prueba cada uno en orden y usa el primero que responda. Si todos fallan
 * (sin clave, límite gratuito agotado, sin conexión), devuelve vacío y CIVIA usa su motor local.
 *
 * <p>En streaming solo se pasa al siguiente si el anterior no alcanzó a enviar texto; así la
 * persona nunca ve dos respuestas mezcladas.
 */
public final class FallbackProvider implements LlmProvider {

    private final List<LlmProvider> chain;

    public FallbackProvider(List<LlmProvider> chain) {
        this.chain = chain.stream().filter(LlmProvider::isAvailable).toList();
    }

    /** Uno solo sin cadena, la cadena si hay varios, o vacío si no hay ninguno. */
    public static Optional<LlmProvider> of(List<LlmProvider> providers) {
        List<LlmProvider> available = providers.stream().filter(LlmProvider::isAvailable).toList();
        if (available.isEmpty()) return Optional.empty();
        if (available.size() == 1) return Optional.of(available.get(0));
        return Optional.of(new FallbackProvider(available));
    }

    @Override
    public boolean isAvailable() {
        return !chain.isEmpty();
    }

    /** Nombre del primero de la cadena (el que responde normalmente). */
    @Override
    public String name() {
        return chain.isEmpty() ? "CIVIA local" : chain.get(0).name();
    }

    @Override
    public Optional<String> complete(Request request) {
        for (LlmProvider p : chain) {
            Optional<String> text = p.complete(request);
            if (text.isPresent()) return text;
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> stream(Request request, Consumer<String> onDelta) {
        for (LlmProvider p : chain) {
            AtomicBoolean sent = new AtomicBoolean(false);
            Optional<String> text = p.stream(request, delta -> { sent.set(true); onDelta.accept(delta); });
            if (text.isPresent() || sent.get()) return text;
        }
        return Optional.empty();
    }
}
