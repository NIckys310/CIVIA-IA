package com.civia.ai.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lenguaje prudente: la IA nunca emite veredictos de seguridad estructural.
 *
 * <p>CIVIA no dice "esta estructura es segura/insegura" ni "cumple la norma": usa lenguaje de
 * revisión ("posible inconsistencia detectada… se recomienda revisar…").
 */
public final class PrudentLanguage {

    public static final String DISCLAIMER =
            "Requiere revisión y aprobación del profesional responsable.";

    public static final String SUGGESTED_PHRASING =
            "Usa lenguaje de revisión: «Posible inconsistencia detectada en …; se recomienda revisar …». "
                    + "La validación corresponde al profesional responsable.";

    private static final Pattern VERDICTS = Pattern.compile(String.join("|",
            "\\b(la|esta|esa|dicha)\\s+(estructura|columna|viga|zapata|losa|edificaci[oó]n|cimentaci[oó]n)"
                    + "\\s+(es|est[aá])\\s+(segura|insegura|correcta|bien\\s+diseñad[ao])",
            "\\b(cumple|no\\s+cumple)\\s+(con\\s+)?(la\\s+)?(norma|nsr-10|normativa)",
            "\\bes\\s+(completamente\\s+)?segur[ao]\\b",
            "\\bes\\s+insegur[ao]\\b",
            "\\b(est[aá]|esto\\s+est[aá])\\s+mal\\s+(diseñad[ao]|calculad[ao])",
            "\\bse\\s+(va\\s+a|puede)\\s+(caer|colapsar)\\b",
            "\\b(the\\s+)?structure\\s+is\\s+(safe|unsafe)\\b",
            "\\b(complies|does\\s+not\\s+comply)\\s+with\\b"),
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private PrudentLanguage() {}

    public record LanguageIssue(String phrase, int start) {}

    public static List<LanguageIssue> findVerdicts(String text) {
        List<LanguageIssue> issues = new ArrayList<>();
        Matcher m = VERDICTS.matcher(text);
        while (m.find()) {
            issues.add(new LanguageIssue(m.group(), m.start()));
        }
        return issues;
    }

    public static boolean isPrudent(String text) {
        return findVerdicts(text).isEmpty();
    }

    /** Reemplaza veredictos por una formulación de revisión (respaldo si el LLM no reformula). */
    public static String soften(String text) {
        return VERDICTS.matcher(text).replaceAll("[requiere revisión profesional]");
    }
}
