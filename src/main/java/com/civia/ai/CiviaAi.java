package com.civia.ai;

import com.civia.ai.assistant.ArchitectAssistant;
import com.civia.ai.assistant.ProjectAdvisor;
import com.civia.ai.library.KnowledgeBase;
import com.civia.ai.llm.ClaudeProvider;
import com.civia.ai.llm.LlmProvider;
import com.civia.ai.models.MassingAdvisor;
import com.civia.ai.models.ModelAnalyzer;
import com.civia.ai.plans.PlanAnalyzer;
import java.util.Collection;
import java.util.Optional;

/**
 * Punto de entrada de la capa de IA. Sin {@code ANTHROPIC_API_KEY} todo funciona con el motor
 * local; con la clave, el asistente y los analizadores usan Claude y caen al motor local si falla.
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

    /** Configuración desde el entorno: {@code ANTHROPIC_API_KEY} y {@code CIVIA_AI_MODEL}. */
    public static CiviaAi create(String apiKey, String model) {
        return new CiviaAi(ClaudeProvider.fromEnvironment(apiKey, model).map(p -> p));
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
