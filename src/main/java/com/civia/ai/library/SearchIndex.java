package com.civia.ai.library;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Índice BM25 en memoria, con normalización para español (acentos, plurales, palabras vacías). */
public final class SearchIndex<T> {

    public record Result<T>(T item, double score) {}

    private static final double K1 = 1.4;
    private static final double B = 0.75;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9ñ]+");

    private static final Set<String> STOPWORDS = Set.of(
            "a", "al", "algo", "como", "con", "cual", "cuales", "cuando", "de", "del", "debe", "deben", "donde",
            "el", "ella", "ellos", "en", "entre", "es", "esa", "ese", "eso", "esta", "este", "esto", "hay", "la",
            "las", "le", "les", "lo", "los", "mas", "me", "mi", "muy", "no", "o", "para", "pero", "por", "que",
            "se", "si", "sin", "sobre", "son", "su", "sus", "te", "tiene", "un", "una", "uno", "unos", "unas",
            "y", "ya", "cuanto", "cuanta", "cuantos", "cuantas", "quiero", "saber", "necesito", "dime", "explica",
            "explicame", "puedo", "puede", "hacer", "tipo", "tipos", "the", "of", "and", "is", "what", "how");

    private final List<T> items;
    private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
    private final int[] lengths;
    private final Map<String, Integer> docFreq = new HashMap<>();
    private final double avgLength;

    public SearchIndex(List<T> items, Function<T, String> text) {
        this.items = List.copyOf(items);
        this.lengths = new int[items.size()];
        long total = 0;
        for (int i = 0; i < items.size(); i++) {
            List<String> tokens = tokenize(text.apply(items.get(i)));
            Map<String, Integer> tf = new HashMap<>();
            for (String t : tokens) tf.merge(t, 1, Integer::sum);
            termFreqs.add(tf);
            tf.keySet().forEach(t -> docFreq.merge(t, 1, Integer::sum));
            lengths[i] = tokens.size();
            total += tokens.size();
        }
        this.avgLength = items.isEmpty() ? 1 : (double) total / items.size();
    }

    public List<Result<T>> search(String query, int limit) {
        List<String> q = tokenize(query == null ? "" : query);
        if (q.isEmpty() || items.isEmpty()) return List.of();
        int n = items.size();
        List<Result<T>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Integer> tf = termFreqs.get(i);
            double score = 0;
            for (String term : q) {
                Integer f = tf.get(term);
                if (f == null) continue;
                int df = docFreq.getOrDefault(term, 0);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                score += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * lengths[i] / avgLength));
            }
            if (score > 0) results.add(new Result<>(items.get(i), score));
        }
        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results.size() > limit ? results.subList(0, limit) : results;
    }

    /** Minúsculas sin tildes (la ñ se conserva). */
    public static String fold(String s) {
        String lower = s.toLowerCase(Locale.ROOT).replace('ñ', '\u0001');
        String folded = DIACRITICS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
        return folded.replace('\u0001', 'ñ');
    }

    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : NON_WORD.split(fold(text))) {
            if (raw.length() < 2 || STOPWORDS.contains(raw)) continue;
            out.add(stem(raw));
        }
        return out;
    }

    /** Stemming ligero: plurales y algunas terminaciones frecuentes. */
    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ciones")) return w.substring(0, w.length() - 5) + "on";
        if (w.length() > 4 && w.endsWith("ces")) return w.substring(0, w.length() - 3) + "z";
        if (w.length() > 4 && (w.endsWith("res") || w.endsWith("les") || w.endsWith("nes") || w.endsWith("des")))
            return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s")) return w.substring(0, w.length() - 1);
        return w;
    }
}
