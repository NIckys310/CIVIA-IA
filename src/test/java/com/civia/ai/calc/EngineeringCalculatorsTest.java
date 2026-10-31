package com.civia.ai.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civia.ai.calc.CalcModels.CalcException;
import com.civia.ai.calc.CalcModels.Result;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Valores verificados a mano con las fórmulas de cada calculadora. */
class EngineeringCalculatorsTest {

    private final EngineeringCalculators calc = new EngineeringCalculators();

    private static double value(Result r, String label) {
        return r.values().stream().filter(v -> v.label().equals(label)).findFirst().orElseThrow().value();
    }

    @Test
    void catalogListsTenCalculatorsWithFields() {
        assertEquals(10, calc.catalog().size());
        calc.catalog().forEach(d -> assertFalse(d.fields().isEmpty(), d.id()));
    }

    @Test
    void simplySupportedBeam() {
        Result r = calc.run("viga", Map.of("apoyo", 1.0, "L", 6.0, "w", 25.0, "P", 0.0, "b", 0.30, "h", 0.50, "E", 21.5));
        assertEquals(112.5, value(r, "Momento máximo"), 1e-6);
        assertEquals(75, value(r, "Reacción por apoyo"), 1e-6);
        assertEquals(6.279, value(r, "Deflexión máxima"), 1e-3);
        assertEquals(0.375, value(r, "Peralte mínimo ACI"), 1e-9);
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test
    void cantileverBeamWarnsAboutAShallowSection() {
        Result r = calc.run("viga", Map.of("apoyo", 2.0, "L", 3.0, "w", 10.0, "P", 5.0, "h", 0.30));
        assertEquals(10 * 9 / 2.0 + 5 * 3, value(r, "Momento máximo"), 1e-9);
        assertEquals(35, value(r, "Cortante máximo"), 1e-9);
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("peralte mínimo")));
    }

    @Test
    void tiedColumnAxialCapacity() {
        Result r = calc.run("columna", Map.of("b", 0.4, "h", 0.4, "fc", 21.0, "fy", 420.0, "rho", 1.5, "Pu", 1500.0));
        assertEquals(1987.0, value(r, "Resistencia de diseño φPn,máx"), 0.1);
        assertEquals(1500 / 1987.0032 * 100, value(r, "Uso de la columna"), 0.01);
    }

    @Test
    void squareFooting() {
        Result r = calc.run("zapata", Map.of("P", 800.0, "qadm", 150.0, "c", 0.4, "peso", 10.0));
        assertEquals(2.45, value(r, "Lado de la zapata"), 1e-9);
        assertEquals(880 / (2.45 * 2.45), value(r, "Presión de contacto"), 1e-6);
    }

    @Test
    void oneWaySlabMinimumThickness() {
        Result r = calc.run("losa", Map.of("L", 4.5, "apoyo", 1.0, "tipo", 1.0, "fy", 420.0));
        assertEquals(0.23, value(r, "Espesor mínimo"), 1e-9); // 4,5/20 = 0,225 → 0,23
    }

    @Test
    void stairFollowsBlondel() {
        Result r = calc.run("escalera", Map.of("H", 2.8, "ch", 0.175, "ancho", 1.1));
        assertEquals(16, value(r, "Número de peldaños"), 1e-9);
        assertEquals(0.28, value(r, "Huella"), 1e-9);
    }

    @Test
    void rationalMethod() {
        Result r = calc.run("caudal", Map.of("C", 0.85, "i", 90.0, "A", 1.2));
        assertEquals(255, value(r, "Caudal pico"), 1e-9);
    }

    @Test
    void manningFullPipeMatchesTheFullFlowFormula() {
        Result r = calc.run("manning", Map.of("D", 0.3, "S", 1.0, "n", 0.01, "yD", 1.0));
        assertEquals(value(r, "Caudal a tubo lleno"), value(r, "Caudal"), 1e-6);
    }

    @Test
    void concreteMix() {
        Result r = calc.run("concreto", Map.of("V", 3.0, "fc", 21.0, "desp", 5.0));
        assertEquals(23, value(r, "Cemento"), 1e-9); // 350 kg × 3,15 m³ = 1102,5 kg → 23 bultos
    }

    @Test
    void rebarWeight() {
        Result r = calc.run("acero", Map.of("barra", 4.0, "cantidad", 20.0, "largo", 6.0, "desp", 5.0));
        assertEquals(126 * 0.994, value(r, "Peso total"), 1e-9);
        assertEquals(21, value(r, "Varillas comerciales de 6 m"), 1e-9);
    }

    @Test
    void earthwork() {
        Result r = calc.run("tierras", Map.of("largo", 12.0, "ancho", 8.0, "prof", 1.5, "abund", 25.0, "volqueta", 7.0));
        assertEquals(144, value(r, "Volumen en banco"), 1e-9);
        assertEquals(26, value(r, "Viajes de volqueta"), 1e-9);
    }

    @Test
    void missingFieldsUseDefaultsAndOutOfRangeIsRejected() {
        assertEquals("viga", calc.run("viga", Map.of()).id());
        CalcException e = assertThrows(CalcException.class, () -> calc.run("viga", Map.of("L", 100.0)));
        assertTrue(e.getMessage().contains("Luz"));
        assertThrows(CalcException.class, () -> calc.run("nada", Map.of()));
    }
}
