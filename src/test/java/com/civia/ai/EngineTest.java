package com.civia.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civia.ai.assistant.ArchitectAssistant;
import com.civia.ai.assistant.ProjectAdvisor;
import com.civia.ai.library.KnowledgeBase;
import com.civia.ai.models.MassingAdvisor;
import com.civia.ai.models.ModelAnalyzer;
import com.civia.ai.plans.PlanAnalyzer;
import com.civia.ai.plans.PlanReport;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class EngineTest {

    private final CiviaAi ai = new CiviaAi(Optional.empty());

    @Test
    void libraryLoadsAndSearchFindsStairs() {
        KnowledgeBase kb = ai.library();
        assertTrue(kb.categories().size() >= 13);
        assertTrue(kb.articles().size() >= 50);
        assertTrue(kb.terms().size() >= 120);
        assertEquals("escaleras", kb.searchArticles("¿Cómo calculo la huella y contrahuella de una escalera?", 1).get(0).slug());
        assertEquals("normativa-urbanistica", kb.searchArticles("índice de ocupación del lote", 1).get(0).slug());
    }

    @Test
    void assistantAnswersLocallyWithSources() {
        var answer = ai.assistant().ask("¿Qué es una zapata?", List.of(), null, List.of());
        assertEquals("CIVIA local", answer.engine());
        assertTrue(answer.text().toLowerCase().contains("zapata"));
        assertFalse(answer.sources().isEmpty());
    }

    @Test
    void assistantIncludesProjectRecommendations() {
        var ctx = new ArchitectAssistant.ProjectContext("Casa", List.of(Recommendation.high("Sube los planos", "Sin planos", "upload-plan")));
        var answer = ai.assistant().ask("¿Qué le falta a mi proyecto?", List.of(), ctx, List.of());
        assertTrue(answer.text().contains("Sube los planos"));
    }

    @Test
    void pdfPlanIsCheckedForMissingItems() throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(420 / 25.4f * 72, 297 / 25.4f * 72));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(40, 700);
                cs.showText("PROYECTO CASA LOMA - PLANTA PRIMER PISO - ESCALA 1:100 - FECHA 2026");
                cs.newLineAtOffset(0, -14);
                cs.showText("SALA 18.50 m2  COCINA 8.20 m2  ALCOBA 11.00 m2  BANO 3.10 m2 ESCALERA");
                cs.newLineAtOffset(0, -14);
                cs.showText("3.20 2.85 4.10 1.20 0.90 3.00 2.40 6.15 N+0.00 EJE A EJE 1");
                cs.endText();
            }
            doc.save(out);
            pdf = out.toByteArray();
        }
        PlanReport r = ai.plans().analyze(new PlanAnalyzer.PlanInput("planta.pdf", "application/pdf", pdf, null));
        assertEquals("A3", r.metrics().get("formatoPapel"));
        assertEquals("planta", r.documentType());
        assertTrue(r.detectedElements().contains("Cocina"));
        assertTrue(status(r, "escala") == PlanReport.Status.OK);
        assertTrue(status(r, "norte") == PlanReport.Status.FALTA);
        assertTrue(status(r, "escalera") == PlanReport.Status.FALTA);
        assertTrue(r.completeness() > 0 && r.completeness() < 100);
    }

    @Test
    void dxfEntitiesAreCounted() {
        String dxf = "0\nSECTION\n2\nHEADER\n9\n$INSUNITS\n70\n6\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n"
                + "0\nLINE\n8\nMUROS\n0\nDIMENSION\n8\nCOTAS\n0\nTEXT\n8\nTEXTOS\n1\nCOCINA\n0\nENDSEC\n0\nEOF\n";
        PlanReport r = ai.plans().analyze(new PlanAnalyzer.PlanInput("p.dxf", "application/dxf", dxf.getBytes(StandardCharsets.UTF_8), null));
        assertEquals("metros", r.metrics().get("unidades"));
        assertTrue(r.detectedElements().contains("Cocina"));
    }

    @Test
    void modelInMillimetersIsFlagged() {
        var stats = new ModelAnalyzer.ModelStats("casa.glb", "glb", 1000, 50_000, 30_000, 12, 4, 0,
                12000, 6500, 9000, List.of("Muro_01", "Losa_N1", "Ventana_1", "Puerta"));
        var report = ai.models().analyze(stats, null);
        assertEquals("milímetros", report.metrics().get("unidadesProbables"));
        assertTrue(report.recommendations().stream().anyMatch(r -> r.title().contains("Circulación vertical")));
    }

    @Test
    void massingRespectsIndices() {
        var p = ai.massing().propose(new MassingAdvisor.MassingInput(10, 20, 0.6, 2.4, 3, 3, 0, 3, 0));
        assertEquals(200, p.lotArea());
        assertTrue(p.footprintArea() <= 120.01);
        assertTrue(p.totalArea() <= 480.01);
    }

    @Test
    void projectAdvisorAsksForPlans() {
        var recs = ai.projects().review(new ProjectAdvisor.ProjectSnapshot("Casa", "vivienda", "diseño", null, 2, null,
                0, 0, 0, 0, 0, 0, 0, 1, 0, 0, List.of()));
        assertEquals("alta", recs.get(0).priority());
        assertTrue(recs.stream().anyMatch(r -> r.action().equals("upload-plan")));
    }

    private static PlanReport.Status status(PlanReport r, String id) {
        return r.checks().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow().status();
    }
}
