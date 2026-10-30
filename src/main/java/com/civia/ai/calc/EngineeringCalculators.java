package com.civia.ai.calc;

import static com.civia.ai.calc.CalcModels.Field.choice;
import static com.civia.ai.calc.CalcModels.Field.number;

import com.civia.ai.calc.CalcModels.CalcException;
import com.civia.ai.calc.CalcModels.Definition;
import com.civia.ai.calc.CalcModels.Field;
import com.civia.ai.calc.CalcModels.Option;
import com.civia.ai.calc.CalcModels.Result;
import com.civia.ai.calc.CalcModels.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Calculadoras de ingeniería civil para anteproyecto: vigas, columnas, zapatas, losas, escaleras,
 * caudales, tuberías, dosificación de concreto, acero de refuerzo y movimiento de tierras.
 *
 * <p>Son herramientas de predimensionamiento y verificación rápida con fórmulas de libro (ACI 318,
 * NSR-10, RAS). No reemplazan el diseño ni la firma del profesional responsable: cada resultado
 * lo dice y trae los pasos para revisarlo.
 */
public final class EngineeringCalculators {

    public static final String DISCLAIMER =
            "Cálculo orientativo de predimensionamiento. Verifica con la norma vigente; el diseño definitivo lo firma el profesional responsable.";

    private record Calc(Definition definition, Function<Map<String, Double>, Result> run) {}

    private final Map<String, Calc> calcs = new LinkedHashMap<>();

    public EngineeringCalculators() {
        register(beamDefinition(), this::beam);
        register(columnDefinition(), this::column);
        register(footingDefinition(), this::footing);
        register(slabDefinition(), this::slab);
        register(stairDefinition(), this::stair);
        register(rationalDefinition(), this::rational);
        register(manningDefinition(), this::manning);
        register(concreteDefinition(), this::concrete);
        register(rebarDefinition(), this::rebar);
        register(earthworkDefinition(), this::earthwork);
    }

    private void register(Definition d, Function<Map<String, Double>, Result> run) {
        calcs.put(d.id(), new Calc(d, run));
    }

    /** Todas las calculadoras, en el orden en que se muestran. */
    public List<Definition> catalog() {
        return calcs.values().stream().map(Calc::definition).toList();
    }

    /** Ejecuta una calculadora. Los campos que falten toman su valor por defecto. */
    public Result run(String id, Map<String, Double> raw) {
        Calc calc = calcs.get(id);
        if (calc == null) throw new CalcException("No existe la calculadora «" + id + "».");
        Map<String, Double> in = new LinkedHashMap<>();
        for (Field f : calc.definition().fields()) {
            Double v = raw == null ? null : raw.get(f.id());
            double value = v == null || v.isNaN() ? f.defaultValue() : v;
            if (value < f.min() || value > f.max()) {
                throw new CalcException(f.label() + " debe estar entre " + fmt(f.min()) + " y " + fmt(f.max()) + (f.unit().isEmpty() ? "" : " " + f.unit()) + ".");
            }
            in.put(f.id(), value);
        }
        return calc.run().apply(in);
    }

    // ------------------------------------------------------------------ viga

    private static Definition beamDefinition() {
        return new Definition("viga", "Estructuras", "Viga de concreto o acero",
                "Reacciones, cortante, momento y deflexión con carga distribuida y puntual; peralte mínimo ACI.", "beam", List.of(
                choice("apoyo", "Tipo de apoyo", 1, "Condición de los extremos de la viga",
                        new Option(1, "Simplemente apoyada"), new Option(2, "Voladizo"), new Option(3, "Empotrada en ambos extremos")),
                number("L", "Luz", "m", 6, 0.5, 40, 0.1, "Distancia entre apoyos (o largo del voladizo)"),
                number("w", "Carga distribuida", "kN/m", 25, 0, 500, 0.5, "Carga uniforme mayorada o de servicio"),
                number("P", "Carga puntual", "kN", 0, 0, 2000, 1, "En el centro de la luz (en el extremo libre si es voladizo)"),
                number("b", "Ancho de la sección", "m", 0.30, 0.10, 2, 0.01, "Base de la viga rectangular"),
                number("h", "Altura de la sección", "m", 0.50, 0.10, 3, 0.01, "Peralte total"),
                number("E", "Módulo de elasticidad", "GPa", 21.5, 5, 210, 0.5, "Concreto f'c 21 MPa ≈ 21,5 GPa; acero 200 GPa")));
    }

    private Result beam(Map<String, Double> in) {
        int support = (int) Math.round(in.get("apoyo"));
        double L = in.get("L"), w = in.get("w"), P = in.get("P"), b = in.get("b"), h = in.get("h"), E = in.get("E") * 1e6; // kPa
        double I = b * Math.pow(h, 3) / 12;
        double R, V, M, d;
        String name;
        List<String> steps = new ArrayList<>();
        steps.add("Inercia de la sección: I = b·h³/12 = " + fmt(b) + "·" + fmt(h) + "³/12 = " + sci(I) + " m⁴");
        switch (support) {
            case 2 -> {
                name = "voladizo";
                R = w * L + P; V = R; M = w * L * L / 2 + P * L;
                d = w * Math.pow(L, 4) / (8 * E * I) + P * Math.pow(L, 3) / (3 * E * I);
                steps.add("Reacción en el empotramiento: R = w·L + P = " + fmt(R) + " kN");
                steps.add("Momento máximo (empotramiento): M = w·L²/2 + P·L = " + fmt(M) + " kN·m");
                steps.add("Deflexión en el extremo: δ = w·L⁴/(8EI) + P·L³/(3EI)");
            }
            case 3 -> {
                name = "empotrada en ambos extremos";
                R = w * L / 2 + P / 2; V = R; M = w * L * L / 12 + P * L / 8;
                d = w * Math.pow(L, 4) / (384 * E * I) + P * Math.pow(L, 3) / (192 * E * I);
                steps.add("Reacciones: R = w·L/2 + P/2 = " + fmt(R) + " kN");
                steps.add("Momento máximo (en los apoyos): M = w·L²/12 + P·L/8 = " + fmt(M) + " kN·m");
                steps.add("Deflexión al centro: δ = w·L⁴/(384EI) + P·L³/(192EI)");
            }
            default -> {
                name = "simplemente apoyada";
                R = w * L / 2 + P / 2; V = R; M = w * L * L / 8 + P * L / 4;
                d = 5 * w * Math.pow(L, 4) / (384 * E * I) + P * Math.pow(L, 3) / (48 * E * I);
                steps.add("Reacciones: R = w·L/2 + P/2 = " + fmt(R) + " kN");
                steps.add("Momento máximo (centro de la luz): M = w·L²/8 + P·L/4 = " + fmt(M) + " kN·m");
                steps.add("Deflexión al centro: δ = 5w·L⁴/(384EI) + P·L³/(48EI)");
            }
        }
        double sigma = 6 * M / (b * h * h) / 1000; // MPa
        double dmm = d * 1000;
        double limit = support == 2 ? L * 1000 / 180 : L * 1000 / 360;
        double hMin = L / (support == 2 ? 8 : support == 3 ? 21 : 16);
        steps.add("Esfuerzo de flexión: σ = 6M/(b·h²) = " + fmt(sigma) + " MPa");
        steps.add("Peralte mínimo ACI 318 (tabla 9.3.1.1, sin calcular deflexiones): h ≥ L/" + (support == 2 ? "8" : support == 3 ? "21" : "16") + " = " + fmt(hMin) + " m");
        List<String> warnings = new ArrayList<>();
        if (h < hMin) warnings.add("La altura " + fmt(h) + " m es menor que el peralte mínimo de " + fmt(hMin) + " m: revisa deflexiones o aumenta la sección.");
        if (dmm > limit) warnings.add("La deflexión (" + fmt(dmm) + " mm) supera el límite orientativo L/" + (support == 2 ? "180" : "360") + " = " + fmt(limit) + " mm.");
        if (b < 0.25 && E < 100e6) warnings.add("En zonas sísmicas, las vigas de concreto suelen exigir un ancho mínimo de 0,25 m.");
        return new Result("viga", "Viga " + name, in, List.of(
                Value.main("Momento máximo", M, "kN·m", 2),
                Value.of("Cortante máximo", V, "kN", 2),
                Value.of("Reacción por apoyo", R, "kN", 2),
                Value.of("Deflexión máxima", dmm, "mm", 2),
                Value.of("Límite de deflexión", limit, "mm", 1),
                Value.of("Esfuerzo de flexión", sigma, "MPa", 2),
                Value.of("Peralte mínimo ACI", hMin, "m", 2),
                Value.of("Inercia", I * 1e8, "cm⁴ ×10⁴", 2)),
                steps, warnings, "ACI 318-19 tabla 9.3.1.1 · resistencia de materiales (Gere)");
    }

    // ------------------------------------------------------------------ columna

    private static Definition columnDefinition() {
        return new Definition("columna", "Estructuras", "Columna de concreto a compresión",
                "Resistencia axial de diseño φPn,máx de una columna con estribos y su nivel de uso.", "columns", List.of(
                number("b", "Lado b", "m", 0.40, 0.15, 2, 0.01, "Dimensión menor de la sección"),
                number("h", "Lado h", "m", 0.40, 0.15, 2, 0.01, "Dimensión mayor de la sección"),
                number("fc", "f'c del concreto", "MPa", 21, 14, 70, 0.5, "Resistencia especificada a 28 días"),
                number("fy", "fy del acero", "MPa", 420, 240, 550, 10, "Grado 60 ≈ 420 MPa"),
                number("rho", "Cuantía de acero", "%", 1.5, 0.5, 8, 0.1, "Entre 1 % y 4 % es lo habitual"),
                number("Pu", "Carga axial mayorada", "kN", 1500, 0, 50000, 10, "Pu de la combinación más desfavorable")));
    }

    private Result column(Map<String, Double> in) {
        double b = in.get("b"), h = in.get("h"), fc = in.get("fc"), fy = in.get("fy"), rho = in.get("rho") / 100, Pu = in.get("Pu");
        double Ag = b * h * 1e6; // mm²
        double Ast = rho * Ag;
        double Pn = 0.85 * fc * (Ag - Ast) + fy * Ast; // N
        double phiPnMax = 0.80 * 0.65 * Pn / 1000; // kN
        double use = phiPnMax > 0 ? Pu / phiPnMax * 100 : 0;
        List<String> steps = List.of(
                "Área bruta: Ag = b·h = " + fmt(Ag) + " mm²",
                "Acero longitudinal: Ast = ρ·Ag = " + fmt(Ast) + " mm²",
                "Resistencia nominal: Pn = 0,85·f'c·(Ag − Ast) + fy·Ast = " + fmt(Pn / 1000) + " kN",
                "Resistencia de diseño (estribos): φPn,máx = 0,80·φ·Pn con φ = 0,65 → " + fmt(phiPnMax) + " kN",
                "Uso: Pu / φPn,máx = " + fmt(use) + " %");
        List<String> warnings = new ArrayList<>();
        if (use > 100) warnings.add("La carga supera la resistencia de diseño: aumenta la sección, f'c o la cuantía.");
        if (rho < 0.01 || rho > 0.04) warnings.add("La cuantía está fuera del rango habitual de 1 % a 4 %.");
        if (Math.min(b, h) < 0.30) warnings.add("En zonas de amenaza sísmica alta las columnas suelen exigir 0,30 m de lado mínimo.");
        warnings.add("Solo compresión axial: con momentos importantes usa el diagrama de interacción y revisa esbeltez.");
        return new Result("columna", "Columna a compresión", in, List.of(
                Value.main("Resistencia de diseño φPn,máx", phiPnMax, "kN", 1),
                Value.of("Uso de la columna", use, "%", 1),
                Value.of("Área bruta", Ag / 1e4, "cm²", 1),
                Value.of("Acero longitudinal", Ast / 100, "cm²", 2)),
                steps, warnings, "ACI 318-19 22.4 · NSR-10 C.10.3");
    }

    // ------------------------------------------------------------------ zapata

    private static Definition footingDefinition() {
        return new Definition("zapata", "Geotecnia", "Zapata aislada cuadrada",
                "Área y lado de una zapata a partir de la carga de servicio y la capacidad admisible del suelo.", "layers", List.of(
                number("P", "Carga de servicio", "kN", 800, 10, 50000, 10, "Carga sin mayorar que baja por la columna"),
                number("qadm", "Capacidad admisible", "kPa", 150, 20, 2000, 5, "Del estudio de suelos"),
                number("c", "Lado de la columna", "m", 0.40, 0.15, 2, 0.01, "Para el voladizo de la zapata"),
                number("peso", "Peso propio y relleno", "%", 10, 0, 40, 1, "Se suma a la carga (≈ 10 %)")));
    }

    private Result footing(Map<String, Double> in) {
        double P = in.get("P"), q = in.get("qadm"), c = in.get("c"), extra = in.get("peso") / 100;
        double Ptot = P * (1 + extra);
        double A = Ptot / q;
        double B = Math.ceil(Math.sqrt(A) / 0.05) * 0.05;
        double pressure = Ptot / (B * B);
        double overhang = (B - c) / 2;
        double hPractical = Math.max(0.30, Math.ceil(overhang * 0.5 / 0.05) * 0.05);
        List<String> steps = List.of(
                "Carga total con peso propio: P·(1 + " + fmt(extra * 100) + " %) = " + fmt(Ptot) + " kN",
                "Área requerida: A = P / qadm = " + fmt(A) + " m²",
                "Lado (redondeado a 5 cm): B = √A → " + fmt(B) + " m",
                "Presión de contacto: q = P / B² = " + fmt(pressure) + " kPa",
                "Voladizo: (B − c)/2 = " + fmt(overhang) + " m");
        List<String> warnings = new ArrayList<>();
        if (B > 4) warnings.add("Zapata muy grande: evalúa una losa de cimentación o cimentación profunda.");
        warnings.add("Revisa punzonamiento, cortante y flexión para definir el espesor y el refuerzo; verifica asentamientos.");
        return new Result("zapata", "Zapata aislada", in, List.of(
                Value.main("Lado de la zapata", B, "m", 2),
                Value.of("Área requerida", A, "m²", 2),
                Value.of("Presión de contacto", pressure, "kPa", 1),
                Value.of("Espesor práctico inicial", hPractical, "m", 2),
                Value.of("Voladizo", overhang, "m", 2)),
                steps, warnings, "NSR-10 título H · ACI 318-19 cap. 13");
    }

    // ------------------------------------------------------------------ losa

    private static Definition slabDefinition() {
        return new Definition("losa", "Estructuras", "Espesor mínimo de losa en una dirección",
                "Espesor mínimo sin calcular deflexiones (ACI tabla 7.3.1.1) y su peso propio.", "layers", List.of(
                number("L", "Luz libre", "m", 4.5, 0.5, 15, 0.05, "Luz de cálculo de la losa"),
                choice("apoyo", "Condición de apoyo", 1, "Continuidad de la losa",
                        new Option(1, "Simplemente apoyada"), new Option(2, "Un extremo continuo"), new Option(3, "Ambos extremos continuos"), new Option(4, "Voladizo")),
                choice("tipo", "Tipo de losa", 1, "Maciza o aligerada (nervada)",
                        new Option(1, "Maciza"), new Option(2, "Aligerada / nervada")),
                number("fy", "fy del acero", "MPa", 420, 240, 550, 10, "Corrige el espesor si no es 420 MPa")));
    }

    private Result slab(Map<String, Double> in) {
        double L = in.get("L"), fy = in.get("fy");
        int support = (int) Math.round(in.get("apoyo"));
        boolean solid = Math.round(in.get("tipo")) == 1;
        double[] solidDiv = {20, 24, 28, 10};
        double[] ribbedDiv = {16, 18.5, 21, 8};
        double div = (solid ? solidDiv : ribbedDiv)[Math.max(1, Math.min(4, support)) - 1];
        double factor = 0.4 + fy / 700;
        double h = L / div * (fy == 420 ? 1 : factor);
        double hRounded = Math.ceil(h / 0.01) * 0.01;
        double weight = solid ? hRounded * 24 : hRounded * 24 * 0.55;
        List<String> steps = new ArrayList<>(List.of(
                "Tabla 7.3.1.1 (" + (solid ? "maciza" : "nervada") + "): h ≥ L/" + fmt(div) + " = " + fmt(L / div) + " m"));
        if (fy != 420) steps.add("Corrección por fy: × (0,4 + fy/700) = × " + fmt(factor));
        steps.add("Espesor mínimo redondeado: " + fmt(hRounded) + " m");
        steps.add("Peso propio aproximado: " + fmt(weight) + " kN/m²" + (solid ? " (24 kN/m³)" : " (≈ 55 % de una maciza del mismo espesor)"));
        List<String> warnings = new ArrayList<>();
        if (solid && hRounded < 0.10) warnings.add("En la práctica se usan losas macizas de al menos 0,10 m.");
        warnings.add("Para luces largas o cargas altas calcula las deflexiones en lugar de usar el espesor mínimo.");
        return new Result("losa", "Losa en una dirección", in, List.of(
                Value.main("Espesor mínimo", hRounded, "m", 2),
                Value.of("Peso propio", weight, "kN/m²", 2)),
                steps, warnings, "ACI 318-19 tabla 7.3.1.1 · NSR-10 C.9.5");
    }

    // ------------------------------------------------------------------ escalera

    private static Definition stairDefinition() {
        return new Definition("escalera", "Arquitectura", "Escalera (regla de Blondel)",
                "Número de peldaños, contrahuella, huella y desarrollo a partir de la altura a salvar.", "ruler", List.of(
                number("H", "Altura a salvar", "m", 2.80, 0.3, 10, 0.01, "De piso terminado a piso terminado"),
                number("ch", "Contrahuella deseada", "m", 0.175, 0.14, 0.20, 0.005, "Entre 0,16 y 0,18 m es cómodo"),
                number("ancho", "Ancho del tramo", "m", 1.10, 0.6, 4, 0.05, "Ancho libre de la escalera")));
    }

    private Result stair(Map<String, Double> in) {
        double H = in.get("H"), target = in.get("ch"), width = in.get("ancho");
        int n = (int) Math.ceil(H / target - 1e-9);
        double ch = H / n;
        double tread = 0.63 - 2 * ch;
        double run = (n - 1) * tread;
        double angle = Math.toDegrees(Math.atan2(H, run));
        List<String> steps = List.of(
                "Número de contrahuellas: n = ⌈H / ch⌉ = ⌈" + fmt(H) + " / " + fmt(target) + "⌉ = " + n,
                "Contrahuella real: H / n = " + fmt3(ch) + " m",
                "Huella (Blondel 2c + h = 0,63 m): h = 0,63 − 2·" + fmt3(ch) + " = " + fmt3(tread) + " m",
                "Desarrollo horizontal: (n − 1)·h = " + fmt(run) + " m",
                "Pendiente: atan(H / desarrollo) = " + fmt(angle) + "°");
        List<String> warnings = new ArrayList<>();
        if (ch > 0.18) warnings.add("Contrahuella mayor de 0,18 m: incómoda y suele incumplir la norma de evacuación.");
        if (tread < 0.25) warnings.add("Huella menor de 0,25 m: aumenta el número de peldaños.");
        if (width < 0.90) warnings.add("Ancho menor de 0,90 m: revisa la norma de medios de evacuación.");
        if (n > 18) warnings.add("Más de 18 peldaños seguidos: incluye un descanso intermedio.");
        return new Result("escalera", "Escalera", in, List.of(
                Value.main("Número de peldaños", n, "contrahuellas", 0),
                Value.of("Contrahuella", ch, "m", 3),
                Value.of("Huella", tread, "m", 3),
                Value.of("Desarrollo horizontal", run, "m", 2),
                Value.of("Pendiente", angle, "°", 1)),
                steps, warnings, "Regla de Blondel · NSR-10 K.3.8");
    }

    // ------------------------------------------------------------------ caudal (método racional)

    private static Definition rationalDefinition() {
        return new Definition("caudal", "Hidráulica", "Caudal pluvial (método racional)",
                "Caudal pico de una cuenca pequeña: Q = C·i·A / 360.", "drop", List.of(
                number("C", "Coeficiente de escorrentía", "", 0.85, 0.05, 1, 0.01, "Cubiertas y pavimentos 0,75–0,95; zonas verdes 0,10–0,35"),
                number("i", "Intensidad de lluvia", "mm/h", 90, 1, 500, 1, "De la curva IDF para el tiempo de concentración"),
                number("A", "Área de la cuenca", "ha", 1.2, 0.001, 300, 0.01, "1 ha = 10 000 m²")));
    }

    private Result rational(Map<String, Double> in) {
        double C = in.get("C"), i = in.get("i"), A = in.get("A");
        double Q = C * i * A / 360;
        List<String> warnings = new ArrayList<>();
        if (A > 250) warnings.add("El método racional se usa en cuencas pequeñas; para áreas mayores usa un hidrograma.");
        return new Result("caudal", "Caudal pluvial", in, List.of(
                Value.main("Caudal pico", Q * 1000, "L/s", 1),
                Value.of("Caudal pico", Q, "m³/s", 3)),
                List.of("Q = C·i·A / 360 = " + fmt(C) + "·" + fmt(i) + "·" + fmt(A) + " / 360 = " + fmt3(Q) + " m³/s"),
                warnings, "Método racional · RAS 2000 título D");
    }

    // ------------------------------------------------------------------ tubería (Manning)

    private static Definition manningDefinition() {
        return new Definition("manning", "Hidráulica", "Tubería a flujo libre (Manning)",
                "Caudal y velocidad de un colector circular parcialmente lleno.", "pipe", List.of(
                number("D", "Diámetro interno", "m", 0.30, 0.05, 4, 0.01, "Diámetro interior del tubo"),
                number("S", "Pendiente", "%", 1.0, 0.05, 30, 0.05, "Pendiente del colector"),
                number("n", "Coeficiente de Manning", "", 0.010, 0.008, 0.035, 0.001, "PVC 0,009–0,010; concreto 0,013"),
                number("yD", "Relación de llenado y/D", "", 0.70, 0.05, 1, 0.01, "Diseño habitual ≤ 0,70–0,85")));
    }

    private Result manning(Map<String, Double> in) {
        double D = in.get("D"), S = in.get("S") / 100, n = in.get("n"), yD = in.get("yD");
        double theta = 2 * Math.acos(1 - 2 * yD);
        double A = D * D / 8 * (theta - Math.sin(theta));
        double P = D * theta / 2;
        double R = A / P;
        double V = Math.pow(R, 2.0 / 3) * Math.sqrt(S) / n;
        double Q = V * A;
        double Afull = Math.PI * D * D / 4;
        double Vfull = Math.pow(D / 4, 2.0 / 3) * Math.sqrt(S) / n;
        double Qfull = Vfull * Afull;
        List<String> steps = List.of(
                "Ángulo central: θ = 2·acos(1 − 2·y/D) = " + fmt3(theta) + " rad",
                "Área mojada: A = D²/8·(θ − sen θ) = " + sci(A) + " m²",
                "Perímetro mojado: P = D·θ/2 = " + fmt3(P) + " m · radio hidráulico R = A/P = " + fmt3(R) + " m",
                "Velocidad: V = (1/n)·R^(2/3)·S^(1/2) = " + fmt(V) + " m/s",
                "Caudal: Q = V·A = " + fmt3(Q) + " m³/s");
        List<String> warnings = new ArrayList<>();
        if (V < 0.6) warnings.add("Velocidad menor de 0,6 m/s: riesgo de sedimentación (revisa la fuerza tractiva).");
        if (V > 5) warnings.add("Velocidad mayor de 5 m/s: riesgo de erosión del tubo.");
        if (yD > 0.85) warnings.add("Llenado mayor de 0,85: deja espacio para la ventilación del colector.");
        return new Result("manning", "Tubería a flujo libre", in, List.of(
                Value.main("Caudal", Q * 1000, "L/s", 1),
                Value.of("Velocidad", V, "m/s", 2),
                Value.of("Caudal a tubo lleno", Qfull * 1000, "L/s", 1),
                Value.of("Velocidad a tubo lleno", Vfull, "m/s", 2),
                Value.of("Radio hidráulico", R, "m", 3)),
                steps, warnings, "Ecuación de Manning · RAS 2000 título D");
    }

    // ------------------------------------------------------------------ dosificación de concreto

    private static final double[][] MIXES = {
            // f'c MPa, cemento kg/m³, arena m³/m³, grava m³/m³, agua L/m³
            {17.5, 300, 0.56, 0.84, 190},
            {21.0, 350, 0.54, 0.81, 190},
            {24.5, 380, 0.52, 0.78, 190},
            {28.0, 420, 0.50, 0.76, 190},
    };

    private static Definition concreteDefinition() {
        return new Definition("concreto", "Materiales", "Dosificación de concreto",
                "Cemento, arena, grava y agua para un volumen de concreto según la resistencia.", "brick", List.of(
                number("V", "Volumen de concreto", "m³", 3, 0.01, 10000, 0.01, "Volumen neto de los elementos"),
                choice("fc", "Resistencia f'c", 21, "Resistencia especificada",
                        new Option(17.5, "17,5 MPa (2500 psi)"), new Option(21, "21 MPa (3000 psi)"),
                        new Option(24.5, "24,5 MPa (3500 psi)"), new Option(28, "28 MPa (4000 psi)")),
                number("desp", "Desperdicio", "%", 5, 0, 30, 1, "Pérdidas en obra")));
    }

    private Result concrete(Map<String, Double> in) {
        double V = in.get("V") * (1 + in.get("desp") / 100);
        double fc = in.get("fc");
        double[] mix = MIXES[1];
        for (double[] m : MIXES) if (Math.abs(m[0] - fc) < 0.01) mix = m;
        double cement = mix[1] * V;
        double bags = Math.ceil(cement / 50);
        double sand = mix[2] * V, gravel = mix[3] * V, water = mix[4] * V;
        return new Result("concreto", "Dosificación de concreto", in, List.of(
                Value.main("Cemento", bags, "bultos de 50 kg", 0),
                Value.of("Cemento", cement, "kg", 0),
                Value.of("Arena", sand, "m³", 2),
                Value.of("Grava", gravel, "m³", 2),
                Value.of("Agua", water, "L", 0),
                Value.of("Volumen con desperdicio", V, "m³", 2)),
                List.of("Por m³ (" + fmt(fc) + " MPa): " + fmt(mix[1]) + " kg de cemento, " + fmt(mix[2]) + " m³ de arena, " + fmt(mix[3]) + " m³ de grava y " + fmt(mix[4]) + " L de agua",
                        "Volumen con desperdicio: " + fmt(in.get("V")) + " × (1 + " + fmt(in.get("desp")) + " %) = " + fmt(V) + " m³"),
                List.of("Proporciones típicas de referencia: valida con un diseño de mezcla y con los agregados reales (humedad y absorción)."),
                "ACI 211.1 (referencia) · prácticas habituales en Colombia");
    }

    // ------------------------------------------------------------------ acero de refuerzo

    private static final double[][] BARS = {
            // número, diámetro mm, área mm², kg/m
            {3, 9.5, 71, 0.560}, {4, 12.7, 129, 0.994}, {5, 15.9, 199, 1.552},
            {6, 19.1, 284, 2.235}, {7, 22.2, 387, 3.042}, {8, 25.4, 510, 3.973},
    };

    private static Definition rebarDefinition() {
        return new Definition("acero", "Materiales", "Peso y área de acero de refuerzo",
                "Kilos, área y barras comerciales para un despiece de varillas corrugadas.", "wire", List.of(
                choice("barra", "Barra", 4, "Diámetro de la varilla",
                        new Option(3, "#3 (3/8″)"), new Option(4, "#4 (1/2″)"), new Option(5, "#5 (5/8″)"),
                        new Option(6, "#6 (3/4″)"), new Option(7, "#7 (7/8″)"), new Option(8, "#8 (1″)")),
                number("cantidad", "Número de barras", "", 20, 1, 100000, 1, "Barras iguales del despiece"),
                number("largo", "Largo de cada barra", "m", 6, 0.1, 24, 0.05, "Incluye ganchos y traslapos"),
                number("desp", "Desperdicio", "%", 5, 0, 30, 1, "Recortes en el figurado")));
    }

    private Result rebar(Map<String, Double> in) {
        int number = (int) Math.round(in.get("barra"));
        double[] bar = BARS[1];
        for (double[] b : BARS) if (b[0] == number) bar = b;
        double count = in.get("cantidad"), len = in.get("largo"), waste = in.get("desp") / 100;
        double total = count * len * (1 + waste);
        double kg = total * bar[3];
        double area = count * bar[2];
        double commercial = Math.ceil(total / 6);
        return new Result("acero", "Acero de refuerzo #" + number, in, List.of(
                Value.main("Peso total", kg, "kg", 1),
                Value.of("Longitud total", total, "m", 1),
                Value.of("Área total de las barras", area / 100, "cm²", 2),
                Value.of("Varillas comerciales de 6 m", commercial, "unidades", 0),
                Value.of("Peso por metro", bar[3], "kg/m", 3),
                Value.of("Diámetro", bar[1], "mm", 1)),
                List.of("Longitud: " + fmt(count) + " × " + fmt(len) + " m × (1 + " + fmt(waste * 100) + " %) = " + fmt(total) + " m",
                        "Peso: " + fmt(total) + " m × " + bar[3] + " kg/m = " + fmt(kg) + " kg",
                        "Área: " + fmt(count) + " × " + fmt(bar[2]) + " mm² = " + fmt(area) + " mm²"),
                List.of(), "NTC 2289 / ASTM A706 (masas nominales)");
    }

    // ------------------------------------------------------------------ movimiento de tierras

    private static Definition earthworkDefinition() {
        return new Definition("tierras", "Obra", "Excavación y acarreo",
                "Volumen en banco y suelto de una excavación y viajes de volqueta.", "map", List.of(
                number("largo", "Largo", "m", 12, 0.1, 5000, 0.1, ""),
                number("ancho", "Ancho", "m", 8, 0.1, 5000, 0.1, ""),
                number("prof", "Profundidad", "m", 1.5, 0.05, 50, 0.05, ""),
                number("abund", "Abundamiento", "%", 25, 0, 80, 1, "Arcillas 20–40 %, roca fracturada 50–70 %"),
                number("volqueta", "Capacidad de la volqueta", "m³", 7, 1, 40, 0.5, "Volumen suelto por viaje")));
    }

    private Result earthwork(Map<String, Double> in) {
        double bank = in.get("largo") * in.get("ancho") * in.get("prof");
        double loose = bank * (1 + in.get("abund") / 100);
        double trips = Math.ceil(loose / in.get("volqueta"));
        return new Result("tierras", "Excavación y acarreo", in, List.of(
                Value.main("Viajes de volqueta", trips, "viajes", 0),
                Value.of("Volumen en banco", bank, "m³", 2),
                Value.of("Volumen suelto", loose, "m³", 2)),
                List.of("Volumen en banco: L·B·H = " + fmt(bank) + " m³",
                        "Volumen suelto: × (1 + " + fmt(in.get("abund")) + " %) = " + fmt(loose) + " m³",
                        "Viajes: ⌈" + fmt(loose) + " / " + fmt(in.get("volqueta")) + "⌉ = " + (long) trips),
                List.of("Si la excavación supera 1,5 m de profundidad, prevé entibado o taludes seguros."),
                "Prácticas de obra · Res. 0312 / plan de manejo de excavaciones");
    }

    // ------------------------------------------------------------------ formato

    static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e12) return String.format(Locale.forLanguageTag("es-CO"), "%,.0f", v);
        return String.format(Locale.forLanguageTag("es-CO"), "%,.2f", v);
    }

    static String fmt3(double v) {
        return String.format(Locale.forLanguageTag("es-CO"), "%,.3f", v);
    }

    static String sci(double v) {
        return String.format(Locale.ROOT, "%.3e", v).replace('.', ',');
    }
}
