package com.civia.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civia.ai.library.KnowledgeBase;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** La biblioteca de ingeniería civil responde a preguntas típicas con el artículo correcto. */
class LibrarySearchTest {

    private static final KnowledgeBase KB = new CiviaAi(Optional.empty()).library();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "¿Qué es la licuación de un suelo en un sismo?|licuacion-de-suelos",
            "¿Cómo calculo el golpe de ariete al cerrar una válvula?|golpe-de-ariete",
            "Diseño de un pavimento rígido con juntas y pasadores|pavimentos-rigidos",
            "Precisión del RTK con GNSS|gnss-gps",
            "¿Por qué fallan los puentes por socavación?|socavacion-en-puentes",
            "Medidas para trabajo en alturas con arnés|trabajo-en-alturas",
            "Método racional para el caudal de aguas lluvias|drenaje-pluvial-urbano",
            "Valor ganado CPI y SPI|control-de-costos-y-valor-ganado"
    })
    void civilEngineeringQuestionsFindTheirArticle(String question, String slug) {
        assertEquals(slug, KB.searchArticles(question, 1).get(0).slug());
    }

    @Test
    void everyArticleBelongsToAKnownCategory() {
        Set<String> ids = new HashSet<>();
        KB.categories().forEach(c -> ids.add(c.id()));
        KB.articles().forEach(a -> assertTrue(ids.contains(a.category()), a.slug() + " → " + a.category()));
    }

    @Test
    void slugsAndTermsAreUnique() {
        Set<String> slugs = new HashSet<>();
        KB.articles().forEach(a -> assertTrue(slugs.add(a.slug()), "slug repetido: " + a.slug()));
        Set<String> terms = new HashSet<>();
        KB.terms().forEach(t -> assertTrue(terms.add(t.term().toLowerCase()), "término repetido: " + t.term()));
    }

    @Test
    void newCategoriesHaveContent() {
        for (String id : new String[] {"geotecnia", "hidraulica", "vias", "topografia", "puentes", "seguridad"}) {
            assertFalse(KB.articlesIn(id).isEmpty(), id);
        }
    }

    @Test
    void articlesAreWellFormed() {
        KB.articles().forEach(a -> {
            assertTrue(a.slug().matches("[a-z0-9]+(-[a-z0-9]+)*"), "slug inválido: " + a.slug());
            assertFalse(a.summary().isBlank(), a.slug());
            assertFalse(a.tags().isEmpty(), a.slug());
            assertTrue(a.body().contains("## "), "sin secciones: " + a.slug());
        });
    }
}
