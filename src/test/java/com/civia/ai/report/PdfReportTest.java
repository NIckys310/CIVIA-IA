package com.civia.ai.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civia.ai.calc.EngineeringCalculators;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfReportTest {

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void reportHasTitleSectionsTablesAndPageNumbers() throws Exception {
        PdfReport report = new PdfReport("Informe de proyecto", "Casa Ríos · Medellín");
        report.heading("Resumen").paragraph("Vivienda unifamiliar de dos pisos con estructura en concreto.");
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < 80; i++) rows.add(new String[] {"Tarea " + i, "Pendiente", "2026-11-0" + (i % 9 + 1)});
        report.table(List.of("Tarea", "Estado", "Fecha"), rows, new float[] {0.5f, 0.25f, 0.25f});
        byte[] pdf = report.toBytes();
        String text = text(pdf);
        assertTrue(text.contains("Informe de proyecto"));
        assertTrue(text.contains("Casa Ríos"));
        assertTrue(text.contains("Tarea 79"));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertTrue(doc.getNumberOfPages() >= 2);
            assertTrue(text.contains("Página 1 de " + doc.getNumberOfPages()));
        }
    }

    @Test
    void unsupportedCharactersAreReplaced() {
        PdfReport report = new PdfReport("x", "");
        assertEquals("phiPn ~ 1987 -> OK", report.clean("φPn ≈ 1987 → ✓"));
        assertEquals("? ok", report.clean("Ω ok"));
    }

    @Test
    void calculationReportListsInputsResultsAndSteps() throws Exception {
        EngineeringCalculators calc = new EngineeringCalculators();
        var definition = calc.catalog().stream().filter(d -> d.id().equals("viga")).findFirst().orElseThrow();
        var result = calc.run("viga", Map.of("L", 6.0, "w", 25.0));
        String text = text(CalcReports.render(definition, result, "Bodega Norte", "Ing. Laura Gómez"));
        assertTrue(text.contains("Memoria de cálculo"));
        assertTrue(text.contains("Bodega Norte"));
        assertTrue(text.contains("Simplemente apoyada"));
        assertTrue(text.contains("112,50"));
        assertTrue(text.contains("Procedimiento"));
        assertTrue(text.contains("profesional responsable"));
    }
}
