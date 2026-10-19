package com.civia.ai.assistant;

import com.civia.ai.Recommendation;
import com.civia.ai.library.KnowledgeBase;
import com.civia.ai.library.KnowledgeBase.Chunk;
import com.civia.ai.library.KnowledgeBase.Hit;
import com.civia.ai.library.SearchIndex;
import com.civia.ai.llm.LlmProvider;
import com.civia.ai.safety.Citations;
import com.civia.ai.safety.PrudentLanguage;
import com.civia.ai.safety.UntrustedContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * CIVIA Copilot: responde preguntas de arquitectura apoyándose en la biblioteca (con citas),
 * en el contexto del proyecto y, si está configurado, en Claude.
 */
public final class ArchitectAssistant {

    public record Source(int marker, String kind, String slug, String title, String section) {}

    public record Answer(String text, String engine, List<Source> sources, List<String> suggestions) {}

    /** Contexto opcional del proyecto activo. */
    public record ProjectContext(String description, List<Recommendation> recommendations) {}

    private static final Pattern GREETING = Pattern.compile("^(hola|buen[oa]s?( dias| tardes| noches)?|hey|saludos|hi|hello)\\b.*");
    private static final Pattern PROJECT_INTENT = Pattern.compile(".*\\b(falta|faltan|recomienda|recomendacion|siguiente paso|que hago|mi proyecto|este proyecto|avanzar|pendiente)\\b.*");

    /** Personalidad y reglas del Copilot cuando responde con Claude. */
    static final String SYSTEM_PROMPT = """
            Eres CIVIA Copilot, el asistente de IA de la plataforma CIVIA AI para ingeniería civil y             arquitectura. Conversas como un colega experto, cercano y riguroso: entiendes lo que la             persona realmente necesita y respondes eso, sin relleno.

            Cómo respondes:
            - En español, con el tono de la persona (tutea si ella tutea). Si escribe en otro idioma,             respóndele en ese idioma.
            - Primero la respuesta directa; después el detalle que ayude. Ajusta la extensión a la             pregunta: breve para lo simple, completa y ordenada para lo complejo.
            - Puedes responder cualquier pregunta, también fuera de la ingeniería (explicar un             concepto, redactar un correo, programar, estudiar, organizar una obra…). Tu fuerte es             la ingeniería civil: estructuras, geotecnia, hidráulica, vías, topografía, materiales,             construcción, presupuestos, BIM, normativa y diseño arquitectónico.
            - En cálculos: plantea los datos, la fórmula, las unidades (SI) y el resultado paso a             paso; señala las hipótesis y verifica el orden de magnitud.
            - Si falta un dato imprescindible, dilo y propone un valor típico para seguir, o pregunta.
            - Usa Markdown: títulos cortos, listas, tablas cuando comparen, `código` cuando aplique.
            - Si no sabes algo o depende de la norma local, dilo con honestidad. Nunca inventes             artículos de norma, cifras ni referencias.

            BIBLIOTECA: cuando uses sus fragmentos, cítalos con [n]. Si no cubren la pregunta,             responde con tu conocimiento general.
            Seguridad profesional: no emitas veredictos definitivos de seguridad estructural ni de             cumplimiento normativo; usa lenguaje de revisión y recuerda que la validación final             corresponde al profesional responsable.
            """;

    private final KnowledgeBase kb;
    private final Optional<LlmProvider> llm;

    public ArchitectAssistant(KnowledgeBase kb, Optional<LlmProvider> llm) {
        this.kb = kb;
        this.llm = llm.filter(LlmProvider::isAvailable);
    }

    public Answer ask(String question, List<LlmProvider.Turn> history, ProjectContext project,
                      List<LlmProvider.Attachment> attachments) {
        String q = question == null ? "" : question.strip();
        if (q.isEmpty()) {
            return new Answer("Escribe tu pregunta sobre arquitectura, tu proyecto, un plano o un modelo 3D.", "CIVIA local", List.of(), defaultSuggestions());
        }
        List<Hit> hits = kb.search(q, 6);
        List<Source> sources = toSources(hits);

        if (llm.isPresent()) {
            Optional<Answer> answer = askLlm(q, history, project, attachments, hits, sources);
            if (answer.isPresent()) return answer.get();
        }
        return answerLocally(q, project, hits, sources);
    }

    private List<Source> toSources(List<Hit> hits) {
        List<Source> sources = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Chunk c = hits.get(i).chunk();
            sources.add(new Source(i + 1, c.kind(), c.slug(), c.title(), c.section()));
        }
        return sources;
    }

    // ------------------------------------------------------------------ Claude

    private Optional<Answer> askLlm(String q, List<LlmProvider.Turn> history, ProjectContext project,
                                    List<LlmProvider.Attachment> attachments, List<Hit> hits, List<Source> sources) {
        List<Citations.SourceChunk> chunks = hits.stream()
                .map(h -> new Citations.SourceChunk(h.chunk().id(), h.chunk().title(), h.chunk().section(), h.chunk().text()))
                .toList();
        String boundary = UntrustedContent.newBoundary();
        StringBuilder system = new StringBuilder(SYSTEM_PROMPT);
        system.append(UntrustedContent.dataOnlyInstruction(boundary)).append("\n\n");
        system.append("BIBLIOTECA (fragmentos numerados):\n");
        system.append(chunks.isEmpty() ? "(sin fragmentos relevantes)" : Citations.numberSources(chunks));
        if (project != null && project.description() != null) {
            system.append("\n\nPROYECTO ACTIVO:\n")
                    .append(UntrustedContent.wrap("proyecto", project.description(), boundary).block());
            if (!project.recommendations().isEmpty()) {
                system.append("\nRecomendaciones automáticas del proyecto:\n");
                project.recommendations().forEach(r -> system.append("- [").append(r.priority()).append("] ")
                        .append(r.title()).append(": ").append(r.detail()).append('\n'));
            }
        }
        return llm.get().complete(new LlmProvider.Request(system.toString(), history, q, attachments)).map(text -> {
            String prudent = PrudentLanguage.isPrudent(text) ? text : PrudentLanguage.soften(text);
            List<Source> cited = new ArrayList<>();
            try {
                Citations.CheckedAnswer checked = Citations.check(prudent, chunks);
                for (Citations.Citation c : checked.citations()) cited.add(sources.get(c.marker() - 1));
            } catch (Citations.CitationException e) {
                // Respuesta de conocimiento general o con marcadores fuera de rango: se muestran las fuentes consultadas.
                cited.addAll(sources.subList(0, Math.min(3, sources.size())));
            }
            return new Answer(prudent + "\n\n_" + PrudentLanguage.DISCLAIMER + "_", llm.get().name(), cited, followUps(sources));
        });
    }

    // ------------------------------------------------------------------ motor local

    Answer answerLocally(String q, ProjectContext project, List<Hit> hits, List<Source> sources) {
        String folded = SearchIndex.fold(q);
        StringBuilder out = new StringBuilder();

        if (GREETING.matcher(folded).matches() && hits.isEmpty()) {
            out.append("¡Hola! Soy **CIVIA Copilot**. Puedo ayudarte con:\n")
                    .append("- Preguntas de arquitectura: normativa, dimensiones, materiales, estructuras, sostenibilidad…\n")
                    .append("- Revisión de **planos** que subas (qué les falta).\n")
                    .append("- Revisión de **modelos 3D** y volumetrías.\n")
                    .append("- Recomendaciones sobre el estado de tu **proyecto**.\n");
            return new Answer(out.toString(), "CIVIA local", List.of(), defaultSuggestions());
        }

        boolean projectQuestion = PROJECT_INTENT.matcher(folded).matches();
        if (project != null && projectQuestion && !project.recommendations().isEmpty()) {
            out.append("### Lo que hace falta en el proyecto\n");
            for (Recommendation r : project.recommendations()) {
                out.append("- **").append(r.title()).append("** (prioridad ").append(r.priority()).append("): ")
                        .append(r.detail()).append('\n');
            }
            out.append('\n');
        }

        if (hits.isEmpty()) {
            if (out.isEmpty()) {
                out.append(Citations.NO_SUPPORT_MESSAGE).append("\n\nPrueba con otras palabras o explora las categorías de la biblioteca: ");
                out.append(String.join(", ", kb.categories().stream().map(KnowledgeBase.Category::name).toList())).append('.');
            }
            return new Answer(out.toString(), "CIVIA local", List.of(), defaultSuggestions());
        }

        // Definición del glosario si la pregunta la pide o si es el mejor resultado.
        Map<String, Integer> markerOf = new LinkedHashMap<>();
        for (int i = 0; i < hits.size(); i++) markerOf.put(hits.get(i).chunk().id(), i + 1);
        Hit bestTerm = hits.stream().limit(3).filter(h -> "term".equals(h.chunk().kind())).findFirst().orElse(null);
        Hit bestArticle = hits.stream().filter(h -> "article".equals(h.chunk().kind())).findFirst().orElse(null);

        List<Source> used = new ArrayList<>();
        if (bestTerm != null) {
            out.append("**").append(bestTerm.chunk().title()).append("** — ")
                    .append(bestTerm.chunk().text().substring(bestTerm.chunk().text().indexOf(':') + 1).strip())
                    .append(" [").append(markerOf.get(bestTerm.chunk().id())).append("]\n\n");
            used.add(sources.get(markerOf.get(bestTerm.chunk().id()) - 1));
        }
        if (bestArticle != null) {
            Chunk c = bestArticle.chunk();
            out.append("### ").append(c.title());
            if (!"Introducción".equals(c.section())) out.append(" · ").append(c.section());
            out.append('\n').append(c.text()).append(" [").append(markerOf.get(c.id())).append("]\n");
            used.add(sources.get(markerOf.get(c.id()) - 1));

            // Otra sección relevante de un artículo distinto.
            for (Hit h : hits) {
                if (h == bestArticle || !"article".equals(h.chunk().kind()) || h.chunk().slug().equals(c.slug())) continue;
                if (h.score() < bestArticle.score() * 0.55) break;
                String excerpt = h.chunk().text();
                if (excerpt.length() > 600) excerpt = excerpt.substring(0, excerpt.lastIndexOf(' ', 600)) + "…";
                out.append("\n### También relevante: ").append(h.chunk().title()).append(" · ").append(h.chunk().section())
                        .append('\n').append(excerpt).append(" [").append(markerOf.get(h.chunk().id())).append("]\n");
                used.add(sources.get(markerOf.get(h.chunk().id()) - 1));
                break;
            }
        }
        out.append("\n_Valores orientativos: verifica la normativa local. ").append(PrudentLanguage.DISCLAIMER).append("_");
        return new Answer(out.toString(), "CIVIA local", used, followUps(sources));
    }

    private List<String> followUps(List<Source> sources) {
        List<String> s = new ArrayList<>();
        for (Source src : sources) {
            if (!"article".equals(src.kind())) continue;
            String suggestion = "Cuéntame más sobre " + src.title().toLowerCase();
            if (!s.contains(suggestion)) s.add(suggestion);
            if (s.size() == 3) break;
        }
        return s.isEmpty() ? defaultSuggestions() : s;
    }

    private static List<String> defaultSuggestions() {
        return List.of("¿Cómo calculo una escalera?", "¿Qué debe tener un plano para licencia?",
                "¿Qué le falta a mi proyecto?", "Estrategias bioclimáticas para clima cálido");
    }
}
