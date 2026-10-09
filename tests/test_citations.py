import pytest

from civia_ai import DISCLAIMER
from civia_ai.citations import (
    NO_SUPPORT_MESSAGE,
    CitationError,
    SourceChunk,
    check_answer,
    number_sources,
)

CHUNKS = [
    SourceChunk(
        chunk_id="nsr10-c-001",
        norm_code="NSR-10",
        norm_version="2010",
        section="Título C",
        page=12,
        text="…",
    ),
    SourceChunk(
        chunk_id="nsr10-h-004",
        norm_code="NSR-10",
        norm_version="2010",
        section="Título H",
        text="…",
    ),
]


def test_sources_are_numbered_with_provenance() -> None:
    text = number_sources(CHUNKS)
    assert text.startswith("[1] NSR-10 (2010) · Título C, p. 12")
    assert "[2] NSR-10 (2010) · Título H" in text


def test_valid_citations_are_resolved_and_disclaimer_added() -> None:
    checked = check_answer(
        "Posible inconsistencia en C-07 [1]; revisar estudio de suelos [2].", CHUNKS
    )
    assert checked.supported
    assert [c.chunk_id for c in checked.citations] == ["nsr10-c-001", "nsr10-h-004"]
    assert checked.text.endswith(DISCLAIMER)


def test_invented_citation_is_rejected() -> None:
    with pytest.raises(CitationError, match="inexistentes"):
        check_answer("Según la norma [3] …", CHUNKS)


def test_answer_without_citations_is_rejected() -> None:
    with pytest.raises(CitationError):
        check_answer("La columna debe medir al menos 30 cm.", CHUNKS)


def test_explicit_no_support_is_allowed() -> None:
    checked = check_answer(NO_SUPPORT_MESSAGE, [])
    assert not checked.supported
    assert checked.citations == []
