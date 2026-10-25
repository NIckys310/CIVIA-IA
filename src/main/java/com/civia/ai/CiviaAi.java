package com.civia.ai;

import com.civia.ai.assistant.ArchitectAssistant;
import com.civia.ai.assistant.ProjectAdvisor;
import com.civia.ai.library.KnowledgeBase;
import com.civia.ai.llm.ClaudeProvider;
import com.civia.ai.llm.FallbackProvider;
import com.civia.ai.llm.OpenAiCompatibleProvider;
import com.civia.ai.llm.LlmProvider;
import com.civia.ai.models.MassingAdvisor;
import com.civia.ai.models.ModelAnalyzer;
import com.civia.ai.plans.PlanAnalyzer;
import java.util.Collection;
import java.util.Optional;

/**
 * Punto de entrada de la capa de IA. Sin claves todo funciona con el motor local. Con claves, el
 * asistente y los analizadores usan los proveedores configurados en este orden y caen al
 * siguiente (y por último al motor local) si uno falla o agota su cuota:
 * Claude ({@code ANTHROPIC_API_KEY}) → Groq ({@code GROQ_API_KEY}) → Cerebras
 * ({@code CEREBRAS_API_KEY}) → OpenRouter ({@code OPENROUTER_API_KEY}).
 */
public final class CiviaAi {

    private final Optional<LlmProvider> llm;
    private volatile KnowledgeBase knowledgeBase;
    private volatile ArchitectAssistant assistant;
    private final PlanAnalyzer planAnalyzer;
    private final ModelAnalyzer modelAnalyzer;
    private final MassingAdvisor massingAdvisor = new MassingAdvisor();
    private final ProjectAdvisor projectAdvisor = new ProjectAdvisor();

    public CiviaAi(Optional<LlmProvider> llm) {
        this.llm = llm;
        this.knowledgeBase = KnowledgeBase.loadDefault();
        this.assistant = new ArchitectAssistant(knowledgeBase, llm);
        this.planAnalyzer = new PlanAnalyzer(llm);
        this.modelAnalyzer = new ModelAnalyzer(llm);
    }

    /** Configuración desde el entorno (ver la descripción de la clase). */
    public static CiviaAi create(String apiKey, String model) {
        java.util.List<LlmProvider> chain = new java.util.ArrayList<>();
        ClaudeProvider.fromEnvironment(apiKey, model).ifPresent(chain::add);
        OpenAiCompatibleProvider.groq().ifPresent(chain::add);
        OpenAiCompatibleProvider.cerebras().ifPresent(chain::add);
        OpenAiCompatibleProvider.openRouter().ifPresent(chain::add);
        return new CiviaAi(FallbackProvider.of(chain));
    }

    public String engineName() {
        return llm.map(LlmProvider::name).orElse("CIVIA local");
    }

    public boolean llmEnabled() {
        return llm.isPresent();
    }

    public KnowledgeBase library() {
        return knowledgeBase;
    }

    /** Añade a la búsqueda los artículos creados por los usuarios. */
    public void extendLibrary(Collection<KnowledgeBase.Article> extra) {
        KnowledgeBase updated = KnowledgeBase.loadDefault().with(extra);
        this.knowledgeBase = updated;
        this.assistant = new ArchitectAssistant(updated, llm);
    }

    public ArchitectAssistant assistant() {
        return assistant;
    }

    public PlanAnalyzer plans() {
        return planAnalyzer;
    }

    public ModelAnalyzer models() {
        return modelAnalyzer;
    }

    public MassingAdvisor massing() {
        return massingAdvisor;
    }

    public ProjectAdvisor projects() {
        return projectAdvisor;
    }
}
