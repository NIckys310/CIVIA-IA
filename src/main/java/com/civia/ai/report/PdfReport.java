package com.civia.ai.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Informe PDF con el estilo de CIVIA: cabecera con marca, títulos, párrafos, tablas, listas y
 * recuadros de aviso; pie con fecha, aviso profesional y "Página n de m".
 *
 * <p>Uso: {@code new PdfReport("Informe de proyecto", "Casa Ríos").heading("Resumen").paragraph("…").toBytes()}.
 * Usa las fuentes estándar del PDF (sin archivos externos); los caracteres que no tienen se
 * sustituyen por equivalentes legibles (φ → phi, ≈ → ~).
 */
public final class PdfReport {

    private static final float MARGIN = 50;
    private static final float TOP = PDRectangle.A4.getHeight() - 92;
    private static final float BOTTOM = 70;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
    private static final Color INK = new Color(0x15, 0x17, 0x1C);
    private static final Color MUTED = new Color(0x5C, 0x62, 0x6E);
    private static final Color GOLD = new Color(0xB0, 0x7D, 0x45);
    private static final Color LINE = new Color(0xDE, 0xDA, 0xD3);
    private static final Color ZEBRA = new Color(0xF6, 0xF4, 0xF0);
    private static final Color WARN_BG = new Color(0xFB, 0xF3, 0xE6);

    private static final Map<String, String> REPLACEMENTS = Map.ofEntries(
            Map.entry("φ", "phi"), Map.entry("θ", "theta"), Map.entry("δ", "delta"), Map.entry("σ", "sigma"),
            Map.entry("ρ", "rho"), Map.entry("Δ", "Delta"), Map.entry("γ", "gamma"), Map.entry("√", "raiz "),
            Map.entry("≈", "~"), Map.entry("≤", "<="), Map.entry("≥", ">="), Map.entry("⁴", "4"), Map.entry("⁻", "-"),
            Map.entry("⌈", ""), Map.entry("⌉", ""), Map.entry("→", "->"), Map.entry("−", "-"), Map.entry("″", "\""),
            Map.entry("′", "'"), Map.entry("✓", "OK"), Map.entry("⚠", "!"));

    private final PDDocument doc = new PDDocument();
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final String title;
    private final String subtitle;
    private final Map<Character, Boolean> encodable = new HashMap<>();
    private PDPageContentStream out;
    private float y;

    public PdfReport(String title, String subtitle) {
        this.title = clean(title);
        this.subtitle = subtitle == null ? "" : clean(subtitle);
        newPage();
        cover();
    }

    // ------------------------------------------------------------------ contenido

    /** Título de sección con una línea dorada debajo. */
    public PdfReport heading(String text) {
        ensure(46);
        y -= 18;
        write(bold, 13.5f, INK, MARGIN, y, clean(text));
        y -= 7;
        rect(MARGIN, y, 34, 1.6f, GOLD);
        y -= 14;
        return this;
    }

    public PdfReport paragraph(String text) {
        for (String line : wrap(clean(text), regular, 10.5f, WIDTH)) {
            ensure(15);
            write(regular, 10.5f, INK, MARGIN, y, line);
            y -= 15;
        }
        y -= 4;
        return this;
    }

    public PdfReport bullets(List<String> items) {
        for (String item : items) {
            List<String> lines = wrap(clean(item), regular, 10.5f, WIDTH - 16);
            for (int i = 0; i < lines.size(); i++) {
                ensure(15);
                if (i == 0) rect(MARGIN + 3, y + 3, 3.5f, 3.5f, GOLD);
                write(regular, 10.5f, INK, MARGIN + 16, y, lines.get(i));
                y -= 15;
            }
        }
        y -= 4;
        return this;
    }

    /** Pares etiqueta → valor en dos columnas (datos del proyecto, entradas de un cálculo). */
    public PdfReport keyValues(Map<String, String> pairs) {
        List<String[]> rows = new ArrayList<>();
        pairs.forEach((k, v) -> rows.add(new String[] {k, v}));
        return table(List.of("Dato", "Valor"), rows, new float[] {0.45f, 0.55f});
    }

    /** Tabla con cabecera sombreada y filas alternas; las celdas largas se dividen en líneas. */
    public PdfReport table(List<String> header, List<String[]> rows, float[] weights) {
        float[] widths = new float[header.size()];
        for (int i = 0; i < widths.length; i++) widths[i] = WIDTH * weights[i];
        tableRow(header.toArray(String[]::new), widths, true, false);
        for (int r = 0; r < rows.size(); r++) tableRow(rows.get(r), widths, false, r % 2 == 1);
        y -= 10;
        return this;
    }

    /** Recuadro destacado para avisos y limitaciones. */
    public PdfReport callout(String label, List<String> lines) {
        if (lines.isEmpty()) return this;
        List<String> wrapped = new ArrayList<>();
        for (String l : lines) wrapped.addAll(wrap("• " + clean(l), regular, 10, WIDTH - 24));
        float h = 24 + wrapped.size() * 14;
        ensure(h + 8);
        rect(MARGIN, y - h + 12, WIDTH, h, WARN_BG);
        rect(MARGIN, y - h + 12, 2.5f, h, GOLD);
        write(bold, 10, GOLD, MARGIN + 12, y - 2, clean(label).toUpperCase(Locale.ROOT));
        float ly = y - 18;
        for (String l : wrapped) {
            write(regular, 10, INK, MARGIN + 12, ly, l);
            ly -= 14;
        }
        y -= h + 8;
        return this;
    }

    /** Cifras destacadas en tarjetas (máx. 4 por fila). */
    public PdfReport metrics(List<String[]> labelValueUnit) {
        int perRow = Math.min(4, Math.max(1, labelValueUnit.size()));
        float gap = 10;
        float w = (WIDTH - gap * (perRow - 1)) / perRow;
        for (int i = 0; i < labelValueUnit.size(); i += perRow) {
            ensure(64);
            for (int j = 0; j < perRow && i + j < labelValueUnit.size(); j++) {
                String[] m = labelValueUnit.get(i + j);
                float x = MARGIN + j * (w + gap);
                rect(x, y - 46, w, 56, ZEBRA);
                rect(x, y + 9, w, 1.2f, GOLD);
                write(regular, 8, MUTED, x + 10, y - 4, clip(clean(m[0]).toUpperCase(Locale.ROOT), regular, 8, w - 20));
                write(bold, 16, INK, x + 10, y - 26, clip(clean(m[1]), bold, 16, w - 20));
                write(regular, 8.5f, MUTED, x + 10, y - 39, clip(clean(m[2]), regular, 8.5f, w - 20));
            }
            y -= 68;
        }
        return this;
    }

    public PdfReport spacer(float points) {
        y -= points;
        return this;
    }

    /** Cierra el documento: pone pie y numeración en todas las páginas. */
    public byte[] toBytes() {
        try {
            out.close();
            int total = doc.getNumberOfPages();
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CO")));
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream s = new PDPageContentStream(doc, doc.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                    out = s;
                    rect(MARGIN, 52, WIDTH, 0.6f, LINE);
                    write(regular, 7.5f, MUTED, MARGIN, 40, clean("CIVIA AI · " + date + " · Documento orientativo: requiere revisión y aprobación del profesional responsable."));
                    String page = "Página " + (i + 1) + " de " + total;
                    write(regular, 7.5f, MUTED, MARGIN + WIDTH - regular.getStringWidth(page) / 1000 * 7.5f, 28, page);
                }
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            doc.save(bytes);
            doc.close();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ maquetación

    private void cover() {
        write(bold, 21, INK, MARGIN, y, clip(title, bold, 21, WIDTH));
        y -= 20;
        if (!subtitle.isEmpty()) {
            write(regular, 12, MUTED, MARGIN, y, clip(subtitle, regular, 12, WIDTH));
            y -= 12;
        }
        y -= 10;
    }

    private void newPage() {
        try {
            if (out != null) out.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            out = new PDPageContentStream(doc, page);
            float top = PDRectangle.A4.getHeight();
            rect(0, top - 6, PDRectangle.A4.getWidth(), 6, INK);
            rect(0, top - 7.2f, PDRectangle.A4.getWidth(), 1.2f, GOLD);
            write(bold, 11, INK, MARGIN, top - 40, "CIVIA AI");
            write(regular, 7.5f, GOLD, MARGIN + 58, top - 40, "INGENIERÍA Y ARQUITECTURA INTELIGENTE");
            float tw = regular.getStringWidth(clean(title)) / 1000 * 8.5f;
            write(regular, 8.5f, MUTED, MARGIN + WIDTH - Math.min(tw, WIDTH / 2), top - 40, clip(clean(title), regular, 8.5f, WIDTH / 2));
            rect(MARGIN, top - 52, WIDTH, 0.6f, LINE);
            y = TOP;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ensure(float needed) {
        if (y - needed < BOTTOM) newPage();
    }

    private void tableRow(String[] cells, float[] widths, boolean header, boolean zebra) {
        PDType1Font font = header ? bold : regular;
        float size = header ? 8.5f : 9.5f;
        List<List<String>> lines = new ArrayList<>();
        int max = 1;
        for (int i = 0; i < widths.length; i++) {
            String text = i < cells.length && cells[i] != null ? clean(cells[i]) : "";
            List<String> l = wrap(header ? text.toUpperCase(Locale.ROOT) : text, font, size, widths[i] - 12);
            lines.add(l);
            max = Math.max(max, l.size());
        }
        float h = 8 + max * 12.5f;
        ensure(h + 2);
        if (header) rect(MARGIN, y - h + 10, WIDTH, h, INK);
        else if (zebra) rect(MARGIN, y - h + 10, WIDTH, h, ZEBRA);
        float x = MARGIN;
        for (int i = 0; i < widths.length; i++) {
            float ly = y - 2;
            for (String l : lines.get(i)) {
                write(font, size, header ? Color.WHITE : INK, x + 6, ly, l);
                ly -= 12.5f;
            }
            x += widths[i];
        }
        if (!header) rect(MARGIN, y - h + 10, WIDTH, 0.4f, LINE);
        y -= h;
    }

    private void rect(float x, float yy, float w, float h, Color color) {
        try {
            out.setNonStrokingColor(color);
            out.addRect(x, yy, w, h);
            out.fill();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(PDType1Font font, float size, Color color, float x, float yy, String text) {
        if (text.isEmpty()) return;
        try {
            out.beginText();
            out.setFont(font, size);
            out.setNonStrokingColor(color);
            out.newLineAtOffset(x, yy);
            out.showText(text);
            out.endText();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> wrap(String text, PDType1Font font, float size, float width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textWidth(candidate, font, size) <= width || line.isEmpty()) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private String clip(String text, PDType1Font font, float size, float width) {
        if (textWidth(text, font, size) <= width) return text;
        String t = text;
        while (t.length() > 1 && textWidth(t + "…", font, size) > width) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private float textWidth(String text, PDType1Font font, float size) {
        try {
            return font.getStringWidth(text) / 1000 * size;
        } catch (IOException | IllegalArgumentException e) {
            return text.length() * size * 0.5f;
        }
    }

    /** Sustituye los caracteres que las fuentes estándar del PDF no pueden mostrar. */
    String clean(String text) {
        if (text == null) return "";
        String s = text.replace('\t', ' ').replace("\r", "");
        for (Map.Entry<String, String> e : REPLACEMENTS.entrySet()) s = s.replace(e.getKey(), e.getValue());
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '\n') { sb.append(c); continue; }
            boolean ok = encodable.computeIfAbsent(c, ch -> {
                try {
                    regular.encode(String.valueOf(ch));
                    return true;
                } catch (IOException | IllegalArgumentException e) {
                    return false;
                }
            });
            sb.append(ok ? c : '?');
        }
        return sb.toString();
    }
}
