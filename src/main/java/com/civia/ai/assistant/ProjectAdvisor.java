package com.civia.ai.assistant;

import com.civia.ai.Recommendation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Revisa el estado de un proyecto y recomienda qué hace falta para avanzar. */
public final class ProjectAdvisor {

    /** Fotografía del proyecto que arma el backend. */
    public record ProjectSnapshot(
            String name,
            String type,
            String phase,
            Double builtArea,
            Integer floors,
            String location,
            int plans,
            int models3d,
            int photos,
            int otherDocuments,
            int openTasks,
            int overdueTasks,
            int budgetItems,
            int members,
            int analyses,
            int pendingFindings,
            List<String> planTypes) {
        public ProjectSnapshot {
            planTypes = planTypes == null ? List.of() : List.copyOf(planTypes);
        }
    }

    private static final Map<String, Integer> ORDER = Map.of("alta", 0, "media", 1, "baja", 2);

    public List<Recommendation> review(ProjectSnapshot p) {
        List<Recommendation> r = new ArrayList<>();
        if (p.location() == null || p.location().isBlank()) {
            r.add(Recommendation.medium("Ubicación del proyecto", "Registra la ubicación: define la normativa urbanística aplicable, el clima y el asoleamiento.", "edit-project"));
        }
        if (p.builtArea() == null || p.builtArea() <= 0) {
            r.add(Recommendation.low("Área construida", "Indica el área construida estimada para calcular costos por m² e índices.", "edit-project"));
        }
        if (p.plans() == 0) {
            r.add(Recommendation.high("Sube los planos", "El proyecto no tiene planos. Sube plantas, cortes y fachadas (PDF, imagen o DXF) para que CIVIA los revise.", "upload-plan"));
        } else {
            List<String> types = p.planTypes().stream().map(String::toLowerCase).toList();
            if (!types.contains("planta")) r.add(Recommendation.high("Faltan plantas", "No hay planos identificados como planta arquitectónica.", "upload-plan"));
            if (!types.contains("corte")) r.add(Recommendation.medium("Faltan cortes", "Se recomiendan al menos dos cortes perpendiculares, uno por la escalera.", "library:tipos-de-planos"));
            if (!types.contains("fachada")) r.add(Recommendation.medium("Faltan fachadas", "Sube las fachadas o alzados con materiales y alturas.", "library:tipos-de-planos"));
            if (!types.contains("localización")) r.add(Recommendation.low("Plano de localización", "Incluye la implantación en el lote con linderos, retiros y norte.", "library:tipos-de-planos"));
            if (p.floors() != null && p.floors() >= 2 && !types.contains("estructural")) {
                r.add(Recommendation.medium("Coordinación estructural", "Proyecto de " + p.floors() + " pisos sin planos estructurales cargados. Coordina con el ingeniero estructural.", "library:predimensionamiento"));
            }
        }
        if (p.analyses() < p.plans()) {
            r.add(Recommendation.medium("Planos sin analizar", (p.plans() - p.analyses()) + " plano(s) aún no se han analizado con la IA.", "analyze"));
        }
        if (p.pendingFindings() > 0) {
            r.add(Recommendation.high("Hallazgos pendientes", p.pendingFindings() + " elemento(s) marcados como faltantes en los análisis de planos.", "analyze"));
        }
        if (p.models3d() == 0) {
            r.add(Recommendation.medium("Modelo 3D", "Sube un modelo 3D (GLB, OBJ o STL) o genera una volumetría desde el visor para estudiar el proyecto en 3D.", "open-3d"));
        }
        if (p.photos() == 0 && p.phase() != null && p.phase().toLowerCase().contains("obra")) {
            r.add(Recommendation.medium("Registro fotográfico", "En fase de obra, documenta el avance con la cámara de CIVIA.", "open-camera"));
        }
        if (p.budgetItems() == 0) {
            r.add(Recommendation.medium("Presupuesto", "Aún no hay presupuesto. Crea capítulos e ítems para estimar el costo.", "open-budget"));
        }
        if (p.overdueTasks() > 0) {
            r.add(Recommendation.high("Tareas vencidas", p.overdueTasks() + " tarea(s) vencidas. Reprograma o reasigna.", "open-tasks"));
        } else if (p.openTasks() == 0) {
            r.add(Recommendation.low("Planifica tareas", "Crea las tareas de la fase actual con responsables y fechas.", "open-tasks"));
        }
        if (p.members() <= 1) {
            r.add(Recommendation.low("Equipo", "Invita a colaboradores (ingenieros, cliente, constructor) al proyecto.", "open-members"));
        }
        r.sort(Comparator.comparingInt(x -> ORDER.getOrDefault(x.priority(), 3)));
        return r;
    }
}
