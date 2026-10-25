package com.civia.ai.models;

import com.civia.ai.Recommendation;
import java.util.ArrayList;
import java.util.List;

/**
 * Propone una volumetría inicial (masa) a partir del lote y la norma urbanística. El visor 3D la
 * genera al instante para estudiar implantación y sombras. Es geometría de anteproyecto, no diseño.
 *
 * <p>Si la persona pide un número de pisos, ese número manda: la norma (índice de construcción) ya no
 * recorta el edificio, solo genera un aviso. Sin pisos pedidos se usa el máximo que permite la norma.
 */
public final class MassingAdvisor {

    /** Tope técnico (no normativo) para que la geometría siga siendo manejable en el navegador. */
    public static final int MAX_FLOORS = 1000;

    /**
     * @param maxFloors tope opcional de la norma local (0 = sin tope); solo aplica cuando no se piden pisos.
     * @param floors    pisos pedidos (0 = calcularlos con la norma). Sin límite normativo.
     */
    public record MassingInput(double lotWidth, double lotDepth, double occupancyIndex, double constructionIndex,
                               double frontSetback, double rearSetback, double sideSetback, double floorHeight,
                               int maxFloors, int floors) {
        /** Compatibilidad con clientes anteriores que no envían los pisos pedidos. */
        public MassingInput(double lotWidth, double lotDepth, double occupancyIndex, double constructionIndex,
                            double frontSetback, double rearSetback, double sideSetback, double floorHeight, int maxFloors) {
            this(lotWidth, lotDepth, occupancyIndex, constructionIndex, frontSetback, rearSetback, sideSetback, floorHeight, maxFloors, 0);
        }
    }

    /**
     * @param normFloors  pisos que permite el índice de construcción con esta huella.
     * @param exceedsNorm true si los pisos pedidos superan lo que permite la norma (aviso, no bloqueo).
     */
    public record MassingProposal(double footprintWidth, double footprintDepth, double offsetX, double offsetZ,
                                  int floors, double floorHeight, double footprintArea, double totalArea,
                                  double lotArea, List<Recommendation> notes, int normFloors, boolean exceedsNorm) {}

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

        int normFloors = Math.max(1, (int) Math.floor(lotArea * ic / footprint));
        int floors;
        if (in.floors() > 0) {
            floors = Math.min(in.floors(), MAX_FLOORS);
        } else {
            floors = in.maxFloors() > 0 ? Math.min(normFloors, in.maxFloors()) : normFloors;
        }
        floors = Math.max(1, floors);
        boolean exceeds = floors > normFloors;

        List<Recommendation> notes = new ArrayList<>();
        notes.add(Recommendation.low("Volumetría de anteproyecto",
                "Masa máxima según índices y retiros. Ajusta con la ficha normativa real del predio.", "library:normativa-urbanistica"));
        if (exceeds) {
            notes.add(Recommendation.medium("Posible exceso sobre la norma",
                    floors + " pisos superan los " + normFloors + " que permite el índice de construcción (" + String.valueOf(round(ic)).replace('.', ',')
                            + "). La volumetría se dibuja igual; confirma con la ficha normativa del predio.", "library:normativa-urbanistica"));
        }
        if (d < 6) {
            notes.add(Recommendation.medium("Planta poco profunda", "El volumen resultante tiene menos de 6 m de fondo; revisa si el programa cabe o si conviene otra configuración.", "library:zonificacion-y-circulaciones"));
        }
        if (floors >= 5) {
            notes.add(Recommendation.medium("Circulación vertical", floors + " pisos: prevé ascensor, escalera de evacuación y núcleo de instalaciones.", "library:proteccion-contra-incendio"));
        }
        if (floors >= 15) {
            notes.add(Recommendation.medium("Edificio en altura", "Prevé varios ascensores, escalera presurizada y un núcleo estructural rígido; el estudio de viento y sísmico será determinante.", "library:diseno-sismorresistente-arquitectonico"));
        }
        if (w / Math.max(d, 1) > 3 || d / Math.max(w, 1) > 3) {
            notes.add(Recommendation.low("Proporción alargada", "Plantas muy alargadas pueden necesitar juntas sísmicas y patios de luz intermedios.", "library:diseno-sismorresistente-arquitectonico"));
        }
        return new MassingProposal(round(w), round(d), round(Math.max(0, in.sideSetback())), round(Math.max(0, in.frontSetback())),
                floors, floorH, round(footprint), round(footprint * floors), round(lotArea), notes, normFloors, exceeds);
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
