package com.civia.ai.calc;

import java.util.List;
import java.util.Map;

/** Tipos comunes de las calculadoras de ingeniería. */
public final class CalcModels {

    private CalcModels() {}

    /** Campo de entrada de una calculadora (para pintar el formulario en la interfaz). */
    public record Field(String id, String label, String unit, double defaultValue, double min, double max,
                        double step, String help, List<Option> options) {
        public Field {
            options = options == null ? List.of() : List.copyOf(options);
        }

        public static Field number(String id, String label, String unit, double def, double min, double max, double step, String help) {
            return new Field(id, label, unit, def, min, max, step, help, List.of());
        }

        public static Field choice(String id, String label, double def, String help, Option... options) {
            return new Field(id, label, "", def, 0, Double.MAX_VALUE, 1, help, List.of(options));
        }
    }

    /** Opción de una lista desplegable: el valor numérico que se envía y su texto. */
    public record Option(double value, String label) {}

    /** Calculadora disponible. */
    public record Definition(String id, String category, String name, String summary, String icon, List<Field> fields) {
        public Definition {
            fields = List.copyOf(fields);
        }
    }

    /** Resultado con su unidad; {@code highlight} marca el valor principal. */
    public record Value(String label, double value, String unit, int decimals, boolean highlight) {
        public static Value of(String label, double value, String unit, int decimals) {
            return new Value(label, value, unit, decimals, false);
        }

        public static Value main(String label, double value, String unit, int decimals) {
            return new Value(label, value, unit, decimals, true);
        }
    }

    /** Resultado completo: valores, pasos del cálculo, avisos y referencia normativa. */
    public record Result(String id, String name, Map<String, Double> inputs, List<Value> values, List<String> steps,
                         List<String> warnings, String reference) {
        public Result {
            inputs = Map.copyOf(inputs);
            values = List.copyOf(values);
            steps = List.copyOf(steps);
            warnings = List.copyOf(warnings);
        }
    }

    /** Entrada fuera de rango o incoherente: la interfaz muestra el mensaje junto al formulario. */
    public static class CalcException extends IllegalArgumentException {
        public CalcException(String message) {
            super(message);
        }
    }
}
