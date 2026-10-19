package com.civia.ai.plans;

import com.civia.ai.Recommendation;
import com.civia.ai.library.SearchIndex;
import com.civia.ai.llm.LlmProvider;
import com.civia.ai.plans.PlanReport.Check;
import com.civia.ai.plans.PlanReport.Status;
import com.civia.ai.safety.PrudentLanguage;
import com.civia.ai.safety.UntrustedContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Analiza planos arquitectónicos (PDF, imagen, DXF, DWG) y señala lo que falta.
 *
 * <p>Siempre corre el análisis local (determinista, sin red). Si hay un proveedor LLM disponible,
 * además le pide que lea el plano y combina ambos resultados.
 */
public final class PlanAnalyzer {

    public record PlanInput(String fileName, String mediaType, byte[] data, String notes) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SCALE = Pattern.compile("\\b1\\s?:\\s?(\\d{1,4})\\b");
    private static final Pattern DIMENSION = Pattern.compile("(?<![\\d.,])\\d{1,3}[.,]\\d{2}(?![\\d])");
    private static final Pattern LEVEL = Pattern.compile("\\bn\\s?[+-]\\s?\\d|\\bnpt\\b|\\bnivel\\b");
    private static final Pattern AREA = Pattern.compile("m2|m²|\\barea\\b");
    private static final Pattern OPENING_CODE = Pattern.compile("\\b[pv]\\s?-?\\s?\\d{1,2}\\b");

    private static final Map<String, String> ROOM_WORDS = new TreeMap<>(Map.ofEntries(
            Map.entry("sala", "Sala"), Map.entry("comedor", "Comedor"), Map.entry("cocina", "Cocina"),
            Map.entry("alcoba", "Alcoba"), Map.entry("dormitorio", "Dormitorio"), Map.entry("habitacion", "Habitación"),
            Map.entry("ba[nñ]o", "Baño"), Map.entry("estudio", "Estudio"), Map.entry("patio", "Patio"),
            Map.entry("terraza", "Terraza"), Map.entry("balcon", "Balcón"), Map.entry("garaje", "Garaje"),
            Map.entry("parqueadero", "Parqueadero"), Map.entry("hall", "Hall"), Map.entry("estar", "Estar"),
            Map.entry("lavanderia", "Lavandería"), Map.entry("ropas", "Patio de ropas"), Map.entry("oficina", "Oficina"),
            Map.entry("recepcion", "Recepción"), Map.entry("deposito", "Depósito"), Map.entry("bodega", "Bodega"),
            Map.entry("vestier", "Vestier"), Map.entry("escalera", "Escalera"), Map.entry("ascensor", "Ascensor"),
            Map.entry("local", "Local"), Map.entry("aula", "Aula"), Map.entry("circulacion", "Circulación")));

    private final Optional<LlmProvider> llm;

    public PlanAnalyzer(Optional<LlmProvider> llm) {
        this.llm = llm.filter(LlmProvider::isAvailable);
    }

    public PlanReport analyze(PlanInput input) {
        PlanReport local = analyzeLocally(input);
        if (llm.isEmpty() || !supportsVision(input)) {
            return local;
        }
        return analyzeWithLlm(input, local).orElse(local);
    }

    /** Límites de la API: PDF hasta ~32 MB por petición, imágenes hasta 5 MB. */
    private static boolean supportsVision(PlanInput input) {
        String mt = input.mediaType();
        if (mt == null) return false;
        if (mt.equals("application/pdf")) return input.data().length <= 24 * 1024 * 1024;
        boolean image = mt.equals("image/png") || mt.equals("image/jpeg") || mt.equals("image/webp") || mt.equals("image/gif");
        return image && input.data().length <= 5 * 1024 * 1024;
    }

    // ------------------------------------------------------------------ análisis local

    PlanReport analyzeLocally(PlanInput input) {
        String type = kind(input);
        Map<String, Object> metrics = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        metrics.put("formato", type.toUpperCase(Locale.ROOT));
        metrics.put("tamanoBytes", input.data().length);

        String text = "";
        int dxfDimensions = -1;
        switch (type) {
            case "pdf" -> text = readPdf(input.data(), metrics, warnings);
            case "dxf" -> {
                DxfSummary dxf = readDxf(input.data());
                text = dxf.text();
                dxfDimensions = dxf.dimensions();
                metrics.putAll(dxf.metrics());
            }
            case "imagen" -> readImage(input.data(), metrics, warnings);
            case "dwg" -> warnings.add("DWG es un formato binario propietario: exporta el plano a PDF o DXF para que CIVIA pueda leer su contenido.");
            default -> warnings.add("Formato no reconocido como plano. Se guardó en el proyecto sin análisis de contenido.");
        }

        String folded = SearchIndex.fold(text + " " + (input.notes() == null ? "" : input.notes()) + " " + input.fileName());
        boolean readable = !text.isBlank();
        metrics.put("caracteresLeidos", text.length());

        List<Check> checks = new ArrayList<>();
        LinkedHashSet<String> elements = new LinkedHashSet<>();
        String docType = detectDocType(folded);

        if (!readable) {
            String why = switch (type) {
                case "imagen" -> "La imagen no tiene texto extraíble sin IA visual.";
                case "pdf" -> "El PDF no tiene capa de texto (probablemente escaneado).";
                default -> "No se pudo extraer texto del archivo.";
            };
            for (String[] c : BASE_CHECKS) {
                checks.add(new Check(c[0], c[1], Status.REVISAR, why, c[2]));
            }
            if (!"dwg".equals(type)) {
                warnings.add(why + " Configura ANTHROPIC_API_KEY para que la IA lea el plano visualmente.");
            }
        } else {
            Matcher sm = SCALE.matcher(folded);
            checks.add(sm.find()
                    ? ok("escala", "Escala", "Se encontró la escala 1:" + sm.group(1) + ".")
                    : missing("escala", "Escala", "No se encontró una escala numérica (p. ej. 1:100).",
                    "Indica la escala en el rótulo y añade una escala gráfica."));
            checks.add(folded.contains("norte") || folded.contains("north")
                    ? ok("norte", "Norte", "Se encontró la indicación del norte.")
                    : missing("norte", "Norte", "No se encontró la flecha o indicación del norte.",
                    "Dibuja el norte en plantas y localización: es necesario para estudiar asoleamiento y ventilación."));
            boolean titleBlock = folded.contains("proyecto") && (folded.contains("fecha") || folded.contains("plano") || folded.contains("lamina") || folded.contains("hoja"));
            checks.add(titleBlock
                    ? ok("rotulo", "Rótulo", "El plano tiene datos de rótulo (proyecto, fecha o número de plano).")
                    : missing("rotulo", "Rótulo", "No se identificó un rótulo completo.",
                    "Incluye rótulo con proyecto, dirección, contenido, escala, fecha, número de plano, revisión y profesional responsable."));
            int dims = dxfDimensions >= 0 ? dxfDimensions : count(DIMENSION, folded);
            metrics.put("cotasDetectadas", dims);
            checks.add(dims >= 8
                    ? ok("cotas", "Cotas", dims + " cotas detectadas.")
                    : dims > 0
                    ? review("cotas", "Cotas", "Solo " + dims + " cotas detectadas.", "Completa cotas parciales, a ejes y totales; verifica que cierren.")
                    : missing("cotas", "Cotas", "No se detectaron cotas.", "Acota vanos, muros, ejes y dimensiones totales."));
            checks.add(LEVEL.matcher(folded).find()
                    ? ok("niveles", "Niveles", "Se encontraron niveles (N+/NPT).")
                    : missing("niveles", "Niveles", "No se encontraron niveles de piso.", "Indica N+0.00 y los niveles de cada piso, desnivel y cubierta."));
            checks.add(folded.contains("eje")
                    ? ok("ejes", "Ejes", "Se mencionan ejes estructurales.")
                    : review("ejes", "Ejes", "No se identificaron ejes nombrados.", "Numera ejes (1,2,3…) y letras (A,B,C…) coherentes en todos los planos."));
            checks.add(AREA.matcher(folded).find()
                    ? ok("areas", "Áreas", "Se indican áreas.")
                    : missing("areas", "Áreas", "No se encontraron áreas.", "Escribe el área de cada espacio y un cuadro de áreas con índices de ocupación y construcción."));

            for (Map.Entry<String, String> e : ROOM_WORDS.entrySet()) {
                if (folded.matches("(?s).*\\b" + e.getKey() + "s?\\b.*")) elements.add(e.getValue());
            }
            checks.add(elements.size() >= 3
                    ? ok("espacios", "Nombres de espacios", elements.size() + " tipos de espacio identificados.")
                    : review("espacios", "Nombres de espacios", "Pocos espacios nombrados.", "Rotula cada espacio con su nombre y área."));

            if ("planta".equals(docType) || "general".equals(docType)) {
                boolean openings = OPENING_CODE.matcher(folded).find() || folded.contains("cuadro de vanos") || folded.contains("carpinteria");
                checks.add(openings
                        ? ok("vanos", "Puertas y ventanas", "Se codifican puertas y ventanas.")
                        : review("vanos", "Puertas y ventanas", "No se encontró codificación de vanos (P-1, V-1…).", "Codifica puertas y ventanas y añade un cuadro de vanos con dimensiones."));
                checks.add(folded.contains("corte") || folded.contains("seccion")
                        ? ok("referencias", "Referencias de corte", "Se referencian cortes.")
                        : review("referencias", "Referencias de corte", "No se ven referencias a cortes.", "Indica en planta la línea y dirección de cada corte."));
            }
            if (elements.contains("Escalera")) {
                boolean stairData = folded.contains("huella") || folded.contains("contrahuella") || folded.contains("sube") || folded.contains("peldan");
                checks.add(stairData
                        ? ok("escalera", "Datos de escalera", "La escalera tiene datos (sube, huella o contrahuella).")
                        : missing("escalera", "Datos de escalera", "Hay escalera sin datos de huella, contrahuella o sentido.",
                        "Indica flecha de subida, número de peldaños, huella y contrahuella (2C + H ≈ 63–64 cm)."));
            }
            boolean professional = folded.contains("arq") || folded.contains("matricula") || folded.contains("ingeniero") || folded.contains("disen");
            checks.add(professional
                    ? ok("profesional", "Profesional responsable", "Se identifica el profesional o diseñador.")
                    : missing("profesional", "Profesional responsable", "No se identifica el profesional responsable.",
                    "Incluye nombre, matrícula profesional y firma del arquitecto responsable."));
            checks.add(folded.contains("accesib") || folded.contains("rampa") || folded.contains("ascensor")
                    ? ok("accesibilidad", "Accesibilidad", "El plano considera accesibilidad (rampa, ascensor o notas).")
                    : review("accesibilidad", "Accesibilidad", "No hay referencias a accesibilidad.",
                    "Verifica una ruta accesible, puertas de 0,80–0,90 m libres y al menos un baño accesible si la norma lo exige."));
        }

        int okCount = (int) checks.stream().filter(c -> c.status() == Status.OK).count();
        int completeness = checks.isEmpty() ? 0 : Math.round(okCount * 100f / checks.size());

        List<Recommendation> recs = new ArrayList<>();
        for (Check c : checks) {
            if (c.status() == Status.FALTA) recs.add(Recommendation.high("Falta: " + c.title(), c.recommendation(), "library:checklist-revision-planos"));
        }
        for (Check c : checks) {
            if (c.status() == Status.REVISAR) recs.add(Recommendation.medium("Revisar: " + c.title(), c.recommendation(), "library:cotas-y-simbologia"));
        }
        if (elements.contains("Baño") && !elements.contains("Patio") && !folded.contains("ventila")) {
            recs.add(Recommendation.low("Ventilación de baños", "Confirma ventilación natural o mecánica en los baños.", "library:instalaciones-hidrosanitarias"));
        }
        if (!readable && "imagen".equals(type)) {
            recs.add(Recommendation.medium("Mejor lectura del plano", "Sube el PDF vectorial original si lo tienes: se lee con mucha más precisión que una foto.", "upload-plan"));
        }

        String summary = readable
                ? "Plano tipo «" + docType + "» con " + elements.size() + " tipos de espacio identificados. "
                + okCount + " de " + checks.size() + " verificaciones completas (" + completeness + " %)."
                : "Archivo recibido (" + type.toUpperCase(Locale.ROOT) + "). El contenido no se pudo leer localmente; revisa las recomendaciones.";

        return new PlanReport("CIVIA local", docType, summary, completeness, checks, List.copyOf(elements), recs, metrics, warnings);
    }

    private static final String[][] BASE_CHECKS = {
            {"escala", "Escala", "Verifica que el plano indique la escala numérica y gráfica."},
            {"norte", "Norte", "Verifica la indicación del norte."},
            {"rotulo", "Rótulo", "Verifica el rótulo completo con datos del proyecto y del profesional."},
            {"cotas", "Cotas", "Verifica cotas parciales, a ejes y totales."},
            {"niveles", "Niveles", "Verifica los niveles de piso terminado."},
            {"areas", "Áreas", "Verifica áreas por espacio y cuadro de áreas."},
    };

    private static Check ok(String id, String title, String detail) {
        return new Check(id, title, Status.OK, detail, "");
    }

    private static Check missing(String id, String title, String detail, String rec) {
        return new Check(id, title, Status.FALTA, detail, rec);
    }

    private static Check review(String id, String title, String detail, String rec) {
        return new Check(id, title, Status.REVISAR, detail, rec);
    }

    private static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    static String kind(PlanInput input) {
        String name = input.fileName() == null ? "" : input.fileName().toLowerCase(Locale.ROOT);
        String mt = input.mediaType() == null ? "" : input.mediaType();
        if (mt.equals("application/pdf") || name.endsWith(".pdf")) return "pdf";
        if (mt.startsWith("image/") && !name.endsWith(".dxf")) return "imagen";
        if (name.endsWith(".dxf")) return "dxf";
        if (name.endsWith(".dwg")) return "dwg";
        return "otro";
    }

    static String detectDocType(String folded) {
        Map<String, String[]> types = new LinkedHashMap<>();
        types.put("estructural", new String[]{"estructural", "refuerzo", "zapata", "despiece", "estribo", "viga de"});
        types.put("eléctrico", new String[]{"electric", "tablero", "tomacorriente", "circuito", "luminaria"});
        types.put("hidrosanitario", new String[]{"hidraulic", "sanitari", "desague", "bajante", "acometida"});
        types.put("localización", new String[]{"localizacion", "implantacion", "lindero", "manzana"});
        types.put("corte", new String[]{"corte ", "seccion"});
        types.put("fachada", new String[]{"fachada", "alzado"});
        types.put("cubierta", new String[]{"planta de cubierta", "cubierta"});
        types.put("planta", new String[]{"planta", "primer piso", "segundo piso", "nivel 1"});
        String best = "general";
        int bestScore = 0;
        for (Map.Entry<String, String[]> e : types.entrySet()) {
            int score = 0;
            for (String w : e.getValue()) {
                int idx = -1;
                while ((idx = folded.indexOf(w, idx + 1)) >= 0) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        return best;
    }

    private static String readPdf(byte[] data, Map<String, Object> metrics, List<String> warnings) {
        try (PDDocument doc = Loader.loadPDF(data)) {
            metrics.put("paginas", doc.getNumberOfPages());
            if (doc.getNumberOfPages() > 0) {
                PDPage first = doc.getPage(0);
                float wMm = first.getMediaBox().getWidth() / 72f * 25.4f;
                float hMm = first.getMediaBox().getHeight() / 72f * 25.4f;
                metrics.put("formatoPapel", paperName(Math.max(wMm, hMm), Math.min(wMm, hMm)));
            }
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException e) {
            warnings.add("No se pudo leer el PDF: " + e.getMessage());
            return "";
        }
    }

    static String paperName(float longMm, float shortMm) {
        String[][] sizes = {{"A0", "1189", "841"}, {"A1", "841", "594"}, {"A2", "594", "420"}, {"A3", "420", "297"},
                {"A4", "297", "210"}, {"Carta", "279", "216"}, {"Oficio", "330", "216"}, {"Pliego", "1000", "700"}};
        for (String[] s : sizes) {
            if (Math.abs(longMm - Float.parseFloat(s[1])) < 12 && Math.abs(shortMm - Float.parseFloat(s[2])) < 12) {
                return s[0];
            }
        }
        return Math.round(longMm) + " × " + Math.round(shortMm) + " mm";
    }

    private static void readImage(byte[] data, Map<String, Object> metrics, List<String> warnings) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            if (img == null) {
                warnings.add("No se pudo decodificar la imagen.");
                return;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            metrics.put("anchoPx", w);
            metrics.put("altoPx", h);
            metrics.put("megapixeles", Math.round(w * (double) h / 100_000) / 10.0);
            // Muestreo: brillo medio, contraste y proporción de "tinta" (líneas oscuras sobre fondo claro).
            int step = Math.max(1, Math.max(w, h) / 600);
            long n = 0;
            double sum = 0;
            double sumSq = 0;
            long dark = 0;
            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    int rgb = img.getRGB(x, y);
                    double lum = 0.2126 * ((rgb >> 16) & 0xff) + 0.7152 * ((rgb >> 8) & 0xff) + 0.0722 * (rgb & 0xff);
                    sum += lum;
                    sumSq += lum * lum;
                    if (lum < 90) dark++;
                    n++;
                }
            }
            double mean = sum / n;
            double contrast = Math.sqrt(Math.max(0, sumSq / n - mean * mean));
            double ink = dark * 100.0 / n;
            metrics.put("brilloMedio", Math.round(mean));
            metrics.put("contraste", Math.round(contrast));
            metrics.put("tintaPorcentaje", Math.round(ink * 10) / 10.0);
            if (Math.max(w, h) < 1600) {
                warnings.add("Resolución baja (" + w + "×" + h + " px): las cotas pequeñas pueden ser ilegibles. Recomendado: 3000 px o más en el lado mayor.");
            }
            if (contrast < 35) {
                warnings.add("Contraste bajo: fotografía el plano con mejor luz, de frente y sin sombras.");
            }
            if (mean < 110 && ink > 40) {
                metrics.put("tipoImagen", "fotografía o render oscuro");
            } else if (ink < 25 && mean > 170) {
                metrics.put("tipoImagen", "dibujo lineal sobre fondo claro");
            } else {
                metrics.put("tipoImagen", "fotografía o imagen mixta");
            }
        } catch (IOException e) {
            warnings.add("No se pudo leer la imagen: " + e.getMessage());
        }
    }

    record DxfSummary(String text, int dimensions, Map<String, Object> metrics) {}

    static DxfSummary readDxf(byte[] data) {
        String[] lines = new String(data, StandardCharsets.UTF_8).split("\\r?\\n");
        Map<String, Integer> entities = new TreeMap<>();
        Set<String> layers = new java.util.TreeSet<>();
        StringBuilder text = new StringBuilder();
        boolean inEntities = false;
        String units = null;
        for (int i = 0; i + 1 < lines.length; i += 2) {
            String code = lines[i].trim();
            String value = lines[i + 1].trim();
            if ("2".equals(code) && "ENTITIES".equals(value)) inEntities = true;
            if ("0".equals(code) && "ENDSEC".equals(value)) inEntities = false;
            if ("9".equals(code) && "$INSUNITS".equals(value) && i + 3 < lines.length) {
                units = switch (lines[i + 3].trim()) {
                    case "4" -> "milímetros";
                    case "5" -> "centímetros";
                    case "6" -> "metros";
                    case "1" -> "pulgadas";
                    case "2" -> "pies";
                    default -> "sin unidades";
                };
            }
            if (inEntities && "0".equals(code)) entities.merge(value, 1, Integer::sum);
            if (inEntities && "8".equals(code)) layers.add(value);
            if (inEntities && ("1".equals(code) || "3".equals(code))) text.append(value.replaceAll("\\\\[A-Za-z][^;]*;|[{}]", "")).append('\n');
        }
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("entidades", entities);
        metrics.put("capas", layers.size());
        metrics.put("nombresDeCapas", layers.stream().limit(40).toList());
        if (units != null) metrics.put("unidades", units);
        // Los nombres de capa también dicen qué contiene el plano (MUROS, COTAS, EJES…).
        text.append(String.join(" ", layers)).append('\n');
        return new DxfSummary(text.toString(), entities.getOrDefault("DIMENSION", 0), metrics);
    }

    // ------------------------------------------------------------------ análisis con LLM

    private Optional<PlanReport> analyzeWithLlm(PlanInput input, PlanReport local) {
        String boundary = UntrustedContent.newBoundary();
        String notes = input.notes() == null || input.notes().isBlank() ? "(sin notas)"
                : UntrustedContent.wrap("notas-usuario", input.notes(), boundary).block();
        String system = """
                Eres CIVIA, asistente experto en arquitectura. Revisas planos arquitectónicos y técnicos \
                como lo haría un arquitecto revisor: identificas el tipo de plano, los espacios y elementos \
                presentes, y señalas con precisión lo que falta o debe revisarse (rótulo, escala, norte, \
                cotas que no cierran, niveles, ejes, áreas, vanos, escaleras, accesibilidad, ventilación e \
                iluminación natural, evacuación, coherencia entre plantas, cortes y fachadas).
                Usa lenguaje de revisión ("posible inconsistencia… se recomienda revisar…"). Nunca afirmes \
                que una estructura es segura o que cumple la norma: eso lo valida el profesional responsable.
                """ + UntrustedContent.dataOnlyInstruction(boundary);
        String prompt = """
                Analiza el plano adjunto (archivo «%s»). Notas del usuario: %s

                Un análisis automático previo encontró: %s

                Responde SOLO con un objeto JSON válido, sin texto adicional, con esta forma:
                {"documentType": "planta|corte|fachada|localización|cubierta|estructural|eléctrico|hidrosanitario|detalle|general",
                 "summary": "2-4 frases describiendo el plano y su estado",
                 "elements": ["espacios y elementos identificados"],
                 "checks": [{"id": "slug", "title": "…", "status": "OK|FALTA|REVISAR", "detail": "…", "recommendation": "…"}],
                 "recommendations": [{"priority": "alta|media|baja", "title": "…", "detail": "…"}]}
                Incluye entre 8 y 16 checks y las recomendaciones más útiles, de mayor a menor prioridad.
                """.formatted(input.fileName().replaceAll("[\\r\\n<>]", ""), notes, local.summary());

        var attachment = new LlmProvider.Attachment(input.mediaType(), input.data(), input.fileName());
        Optional<String> answer = llm.get().complete(new LlmProvider.Request(system, List.of(), prompt, List.of(attachment)));
        if (answer.isEmpty()) return Optional.empty();
        try {
            String raw = answer.get();
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) return Optional.empty();
            JsonNode root = JSON.readTree(raw.substring(start, end + 1));

            List<Check> checks = new ArrayList<>();
            for (JsonNode c : root.path("checks")) {
                Status status = switch (c.path("status").asText("REVISAR").toUpperCase(Locale.ROOT)) {
                    case "OK" -> Status.OK;
                    case "FALTA" -> Status.FALTA;
                    default -> Status.REVISAR;
                };
                checks.add(new Check(c.path("id").asText("check-" + checks.size()), c.path("title").asText(),
                        status, prudent(c.path("detail").asText()), prudent(c.path("recommendation").asText())));
            }
            List<String> elements = new ArrayList<>();
            root.path("elements").forEach(e -> elements.add(e.asText()));
            List<Recommendation> recs = new ArrayList<>();
            for (JsonNode r : root.path("recommendations")) {
                recs.add(new Recommendation(r.path("priority").asText("media"), r.path("title").asText(),
                        prudent(r.path("detail").asText()), "review"));
            }
            if (checks.isEmpty()) return Optional.empty();
            int okCount = (int) checks.stream().filter(c -> c.status() == Status.OK).count();
            Map<String, Object> metrics = new LinkedHashMap<>(local.metrics());
            metrics.put("revisionVisual", true);
            return Optional.of(new PlanReport(llm.get().name(), root.path("documentType").asText(local.documentType()),
                    prudent(root.path("summary").asText(local.summary())), Math.round(okCount * 100f / checks.size()),
                    checks, elements, recs, metrics, local.warnings().stream()
                    .filter(w -> !w.contains("ANTHROPIC_API_KEY")).toList()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String prudent(String text) {
        return PrudentLanguage.isPrudent(text) ? text : PrudentLanguage.soften(text);
    }
}
