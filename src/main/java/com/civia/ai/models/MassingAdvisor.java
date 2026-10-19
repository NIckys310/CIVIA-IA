package com.civia.ai.models;

import com.civia.ai.Recommendation;
import java.util.ArrayList;
import java.util.List;

/**
 * Propone una volumetría inicial (masa) a partir del lote y la norma urbanística. El visor 3D la
 * genera al instante para estudiar implantación y sombras. Es geometría de anteproyecto, no diseño.
 */
public final class MassingAdvisor {

    public record MassingInput(double lotWidth, double lotDepth, double occupancyIndex, double constructionIndex,
                               double frontSetback, double rearSetback, double sideSetback, double floorHeight,
                               int maxFloors) {}

    public record MassingProposal(double footprintWidth, double footprintDepth, double offsetX, double offsetZ,
                                  int floors, double floorHeight, double footprintArea, double totalArea,
                                  double lotArea, List<Recommendation> notes) {}

    public MassingProposal propose(MassingInput in) {
        double lotW = positive(in.lotWidth(), 10);
        double lotD = positive(in.lotDepth(), 20);
        double io = clamp(in.occupancyIndex(), 0.05, 1, 0.7);
        double ic = clamp(in.constructionIndex(), 0.1, 30, 2.0);
        double floorH = clamp(in.floorHeight(), 2.4, 6, 2.9);
        double lotArea = lotW * lotD;

        double availW = Math.max(1, lotW - 2 * Math.max(0, in.sideSetback()));
        double availD = Math.max(1, lotD - Math.max(0, in.frontSetback()) - Math.max(0, in.rearSetback()));
        double maxFootprint = lotArea * io;
        double w = availW;
        double d = Math.min(availD, maxFootprint / w);
        double footprint = w * d;

        int floors = (int) Math.floor(lotArea * ic / footprint);
        if (in.maxFloors() > 0) floors = Math.min(floors, in.maxFloors());
        floors = Math.max(1, floors);

        List<Recommendation> notes = new ArrayList<>();
        notes.add(Recommendation.low("Volumetría de anteproyecto",
                "Masa máxima según índices y retiros. Ajusta con la ficha normativa real del predio.", "library:normativa-urbanistica"));
        if (d < 6) {
            notes.add(Recommendation.medium("Planta poco profunda", "El volumen resultante tiene menos de 6 m de fondo; revisa si el programa cabe o si conviene otra configuración.", "library:zonificacion-y-circulaciones"));
        }
        if (floors >= 5) {
            notes.add(Recommendation.medium("Circulación vertical", floors + " pisos: prevé ascensor, escalera de evacuación y núcleo de instalaciones.", "library:proteccion-contra-incendio"));
        }
        if (w / Math.max(d, 1) > 3 || d / Math.max(w, 1) > 3) {
            notes.add(Recommendation.low("Proporción alargada", "Plantas muy alargadas pueden necesitar juntas sísmicas y patios de luz intermedios.", "library:diseno-sismorresistente-arquitectonico"));
        }
        return new MassingProposal(round(w), round(d), round(Math.max(0, in.sideSetback())), round(Math.max(0, in.frontSetback())),
                floors, floorH, round(footprint), round(footprint * floors), round(lotArea), notes);
    }

    private static double positive(double v, double def) {
        return v > 0 ? v : def;
    }

    private static double clamp(double v, double min, double max, double def) {
        if (v <= 0 || Double.isNaN(v)) return def;
        return Math.min(max, Math.max(min, v));
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
