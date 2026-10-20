package com.civia.ai.library;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Biblioteca de arquitectura: categorías, artículos y glosario con búsqueda BM25.
 *
 * <p>El contenido base viaja dentro de la librería ({@code civia/library/*.json}); el backend
 * puede sumar artículos propios de la organización con {@link #with(Collection)}.
 */
public final class KnowledgeBase {

    public record Category(String id, String name, String icon, String description) {}

    public record Article(String slug, String category, String title, String summary, List<String> tags, String body) {
        public Article {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public record Term(String term, String category, String definition) {}

    /** Fragmento indexado: una sección de un artículo o una entrada del glosario. */
    public record Chunk(String id, String kind, String slug, String title, String section, String category, String text) {}

    public record Hit(Chunk chunk, double score) {}

    private static final String[] ARTICLE_FILES = {
            "civia/library/articles-diseno.json",
            "civia/library/articles-tecnica.json",
            "civia/library/articles-practica.json",
            "civia/library/articles-ingenieria.json"
    };

    private final List<Category> categories;
    private final List<Article> articles;
    private final List<Term> terms;
    private final Map<String, Article> bySlug = new HashMap<>();
    private final SearchIndex<Chunk> index;

    private KnowledgeBase(List<Category> categories, List<Article> articles, List<Term> terms) {
        this.categories = List.copyOf(categories);
        this.articles = List.copyOf(articles);
        this.terms = terms.stream().sorted(Comparator.comparing(t -> SearchIndex.fold(t.term()))).toList();
        List<Chunk> chunks = new ArrayList<>();
        for (Article a : this.articles) {
            bySlug.put(a.slug(), a);
            chunks.addAll(split(a));
        }
        for (Term t : this.terms) {
            chunks.add(new Chunk("term:" + SearchIndex.fold(t.term()), "term", null, t.term(), "Glosario",
                    t.category(), t.term() + ": " + t.definition()));
        }
        this.index = new SearchIndex<>(chunks, c -> {
            // El título y las etiquetas pesan más que el cuerpo.
            Article a = c.slug() == null ? null : bySlug.get(c.slug());
            String tags = a == null ? "" : String.join(" ", a.tags());
            return (c.title() + " ").repeat(3) + (c.section() + " ").repeat(2) + tags + " " + tags + " " + c.text();
        });
    }

    /** Carga la biblioteca base empaquetada en la librería. */
    public static KnowledgeBase loadDefault() {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<Category> categories = read(mapper, "civia/library/categories.json", new TypeReference<>() {});
        List<Article> articles = new ArrayList<>();
        for (String file : ARTICLE_FILES) {
            articles.addAll(read(mapper, file, new TypeReference<List<Article>>() {}));
        }
        List<Term> terms = read(mapper, "civia/library/glossary.json", new TypeReference<>() {});
        return new KnowledgeBase(categories, articles, terms);
    }

    /** Copia de la biblioteca con artículos adicionales (p. ej. los creados por los usuarios). */
    public KnowledgeBase with(Collection<Article> extra) {
        Map<String, Article> merged = new java.util.LinkedHashMap<>();
        articles.forEach(a -> merged.put(a.slug(), a));
        extra.forEach(a -> merged.put(a.slug(), a));
        return new KnowledgeBase(categories, new ArrayList<>(merged.values()), terms);
    }

    private static <T> T read(ObjectMapper mapper, String resource, TypeReference<T> type) {
        try (InputStream in = KnowledgeBase.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Falta el recurso " + resource);
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Chunk> split(Article a) {
        List<Chunk> out = new ArrayList<>();
        String section = "Introducción";
        StringBuilder current = new StringBuilder(a.summary()).append('\n');
        int n = 0;
        for (String line : a.body().split("\n")) {
            if (line.startsWith("## ")) {
                if (!current.toString().isBlank()) {
                    out.add(new Chunk(a.slug() + "#" + n++, "article", a.slug(), a.title(), section, a.category(), current.toString().strip()));
                }
                section = line.substring(3).strip();
                current = new StringBuilder();
            } else {
                current.append(line).append('\n');
            }
        }
        if (!current.toString().isBlank()) {
            out.add(new Chunk(a.slug() + "#" + n, "article", a.slug(), a.title(), section, a.category(), current.toString().strip()));
        }
        return out;
    }

    public List<Category> categories() {
        return categories;
    }

    public List<Article> articles() {
        return articles;
    }

    public List<Article> articlesIn(String category) {
        return articles.stream().filter(a -> a.category().equals(category)).toList();
    }

    public Optional<Article> article(String slug) {
        return Optional.ofNullable(bySlug.get(slug));
    }

    public List<Term> terms() {
        return terms;
    }

    public List<Hit> search(String query, int limit) {
        return index.search(query, limit).stream().map(r -> new Hit(r.item(), r.score())).toList();
    }

    /** Artículos (sin repetir) más relevantes para la consulta. */
    public List<Article> searchArticles(String query, int limit) {
        List<Article> out = new ArrayList<>();
        for (Hit h : search(query, limit * 4)) {
            if (h.chunk().slug() == null) continue;
            Article a = bySlug.get(h.chunk().slug());
            if (a != null && !out.contains(a)) out.add(a);
            if (out.size() == limit) break;
        }
        return out;
    }
}
