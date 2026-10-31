package com.civia.ai.report;

import com.civia.ai.calc.CalcModels.Definition;
import com.civia.ai.calc.CalcModels.Field;
import com.civia.ai.calc.CalcModels.Option;
import com.civia.ai.calc.CalcModels.Result;
import com.civia.ai.calc.CalcModels.Value;
import com.civia.ai.calc.EngineeringCalculators;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Memoria de cálculo en PDF a partir del resultado de una calculadora. */
public final class CalcReports {

    private CalcReports() {}

    public static byte[] render(Definition definition, Result result, String project, String author) {
        PdfReport pdf = new PdfReport("Memoria de cálculo · " + result.name(),
                (project == null || project.isBlank() ? "" : "Proyecto: " + project + " · ") + (author == null ? "" : "Elaboró: " + author));
        List<String[]> highlights = new ArrayList<>();
        for (Value v : result.values()) if (v.highlight()) highlights.add(new String[] {v.label(), number(v), v.unit()});
        if (!highlights.isEmpty()) pdf.metrics(highlights);

        pdf.heading("Datos de entrada");
        Map<String, String> inputs = new LinkedHashMap<>();
        for (Field f : definition.fields()) {
            double value = result.inputs().getOrDefault(f.id(), f.defaultValue());
            String shown = f.options().isEmpty() ? format(value, 3) + (f.unit().isEmpty() ? "" : " " + f.unit())
                    : f.options().stream().filter(o -> Math.abs(o.value() - value) < 1e-9).map(Option::label).findFirst().orElse(format(value, 2));
            inputs.put(f.label(), shown);
        }
        pdf.keyValues(inputs);

        pdf.heading("Resultados");
        List<String[]> rows = new ArrayList<>();
        for (Value v : result.values()) rows.add(new String[] {v.label(), number(v), v.unit()});
        pdf.table(List.of("Magnitud", "Valor", "Unidad"), rows, new float[] {0.5f, 0.25f, 0.25f});

        pdf.heading("Procedimiento");
        pdf.bullets(result.steps());
        List<String> notes = new ArrayList<>(result.warnings());
        notes.add(EngineeringCalculators.DISCLAIMER);
        pdf.callout("Observaciones", notes);
        pdf.paragraph("Referencia: " + result.reference());
        return pdf.toBytes();
    }

    private static String number(Value v) {
        return format(v.value(), v.decimals());
    }

    static String format(double value, int decimals) {
        String pattern = "%,." + Math.max(0, decimals) + "f";
        return String.format(Locale.forLanguageTag("es-CO"), pattern, value);
    }
}
