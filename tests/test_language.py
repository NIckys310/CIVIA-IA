import pytest

from civia_ai.language import find_verdicts, is_prudent


@pytest.mark.parametrize(
    "text",
    [
        "La estructura es segura.",
        "Esta columna es insegura",
        "El diseño cumple con la NSR-10.",
        "La viga está mal diseñada",
        "The structure is safe.",
        "El edificio se va a caer",
    ],
)
def test_verdicts_are_detected(text: str) -> None:
    assert not is_prudent(text)
    assert find_verdicts(text)


@pytest.mark.parametrize(
    "text",
    [
        "Posible inconsistencia detectada en C-07; se recomienda revisar su continuidad.",
        "La sección de la viga V-101 difiere entre E-02 y E-03. Requiere revisión del profesional.",
        "Información: el nivel N+3.00 tiene 12 columnas detectadas.",
    ],
)
def test_review_language_is_accepted(text: str) -> None:
    assert is_prudent(text)
