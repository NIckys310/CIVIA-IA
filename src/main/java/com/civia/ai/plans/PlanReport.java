package com.civia.ai.plans;

import com.civia.ai.Recommendation;
import java.util.List;
import java.util.Map;

/** Resultado del análisis de un plano. */
public record PlanReport(
        String engine,
        String documentType,
        String summary,
        int completeness,
        List<Check> checks,
        List<String> detectedElements,
        List<Recommendation> recommendations,
        Map<String, Object> metrics,
        List<String> warnings) {

    /** Estado de cada verificación: OK, FALTA o REVISAR. */
    public enum Status { OK, FALTA, REVISAR }

    public record Check(String id, String title, Status status, String detail, String recommendation) {}
}
