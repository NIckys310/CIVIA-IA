package com.civia.ai.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlockParam;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Claude (Anthropic) como proveedor. Lee planos en imagen y PDF directamente.
 *
 * <p>Se activa con la variable de entorno {@code ANTHROPIC_API_KEY}. El modelo se cambia con
 * {@code CIVIA_AI_MODEL} (por defecto {@value #DEFAULT_MODEL}).
 */
public final class ClaudeProvider implements LlmProvider {

    public static final String DEFAULT_MODEL = "claude-opus-5-5";
    private static final Logger LOG = Logger.getLogger(ClaudeProvider.class.getName());
    private static final long MAX_TOKENS = 16_000L;

    private final AnthropicClient client;
    private final String model;

    private ClaudeProvider(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    /** Crea el proveedor si hay clave; si no, devuelve vacío y CIVIA usa el motor local. */
    public static Optional<ClaudeProvider> fromEnvironment(String apiKey, String model) {
        String key = apiKey != null && !apiKey.isBlank() ? apiKey : System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(key)
                .timeout(Duration.ofMinutes(3))
                .maxRetries(2)
                .build();
        String chosen = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
        return Optional.of(new ClaudeProvider(client, chosen));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String name() {
        return "Claude (" + model + ")";
    }

    @Override
    public Optional<String> complete(Request request) {
        try {
            return textOf(client.messages().create(params(request)));
        } catch (RuntimeException e) {
            return handle(e);
        }
    }

    private MessageCreateParams params(Request request) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(request.system())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
                // Si el modelo declina por política, la API reintenta con un modelo de respaldo.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));

        for (Turn turn : request.history()) {
            if ("assistant".equals(turn.role())) {
                params.addAssistantMessage(turn.text());
            } else {
                params.addUserMessage(turn.text());
            }
        }

        List<ContentBlockParam> blocks = new ArrayList<>();
        for (Attachment a : request.attachments()) {
            String data = Base64.getEncoder().encodeToString(a.data());
            if (a.isPdf()) {
                blocks.add(ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                        .source(Base64PdfSource.builder().data(data).build())
                        .title(a.title())
                        .build()));
            } else if (a.isImage() && imageType(a.mediaType()) != null) {
                blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                        .source(Base64ImageSource.builder().data(data).mediaType(imageType(a.mediaType())).build())
                        .build()));
            }
        }
        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(request.prompt()).build()));
        params.addUserMessageOfBlockParams(blocks);
        return params.build();
    }

    private static Optional<String> textOf(Message response) {
        if (response.stopReason().map(r -> r.toString().equalsIgnoreCase("refusal")).orElse(false)) {
            LOG.info("Claude declinó la solicitud; se usa el motor local.");
            return Optional.empty();
        }
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining("\n"))
                .strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    /** Errores de la API: se registran y CIVIA cae al motor local. */
    private static Optional<String> handle(RuntimeException e) {
        if (e instanceof UnauthorizedException) {
            LOG.warning("ANTHROPIC_API_KEY no es válida; se usa el motor local.");
        } else if (e instanceof RateLimitException) {
            LOG.warning("Límite de uso de Claude alcanzado; se usa el motor local.");
        } else if (e instanceof AnthropicServiceException) {
            LOG.log(Level.WARNING, "Error de la API de Claude: " + e.getMessage());
        } else if (e instanceof AnthropicIoException) {
            LOG.log(Level.WARNING, "Sin conexión con Claude: " + e.getMessage());
        } else {
            throw e;
        }
        return Optional.empty();
    }

    private static Base64ImageSource.MediaType imageType(String mediaType) {
        return switch (mediaType) {
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/jpeg", "image/jpg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            default -> null;
        };
    }
}
