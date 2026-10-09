"""Citas obligatorias en las respuestas de CIVIA Copilot (RAG).

Reglas:
- Cada afirmación del Copilot se apoya en fragmentos recuperados (`SourceChunk`) y los cita
  con marcadores `[n]` que corresponden a esos fragmentos.
- Un marcador que no corresponde a ningún fragmento recuperado es una cita inventada: la
  respuesta se rechaza.
- Si no hay soporte, la respuesta debe decirlo explícitamente (`NO_SUPPORT_MESSAGE`) en vez de
  responder sin fuentes.
"""

import re

from pydantic import BaseModel, Field

from civia_ai import DISCLAIMER

NO_SUPPORT_MESSAGE = (
    "No encontré soporte en las fuentes disponibles para responder con seguridad. "
    "Indica la normativa o el documento que debo consultar."
)

_MARKER_RE = re.compile(r"\[(\d{1,3})\]")


class SourceChunk(BaseModel):
    """Fragmento de la biblioteca técnica/normativa con su procedencia completa."""

    chunk_id: str
    norm_code: str = Field(examples=["NSR-10"])
    norm_version: str = Field(examples=["2010"])
    section: str = Field(examples=["Título C"])
    page: int | None = None
    text: str

    def label(self) -> str:
        page = f", p. {self.page}" if self.page is not None else ""
        return f"{self.norm_code} ({self.norm_version}) · {self.section}{page}"


class Citation(BaseModel):
    marker: int
    chunk_id: str
    label: str


class CheckedAnswer(BaseModel):
    text: str
    citations: list[Citation]
    supported: bool


class CitationError(ValueError):
    """La respuesta cita fuentes inexistentes o afirma sin soporte."""


def number_sources(chunks: list[SourceChunk]) -> str:
    """Texto para el prompt: cada fragmento con su número de cita [n] y su procedencia."""
    return "\n\n".join(f"[{i}] {c.label()}\n{c.text}" for i, c in enumerate(chunks, start=1))


def check_answer(answer: str, chunks: list[SourceChunk]) -> CheckedAnswer:
    """Valida las citas de una respuesta del LLM y añade el aviso de revisión profesional."""
    text = answer.strip()
    if text.startswith(NO_SUPPORT_MESSAGE[:40]):
        return CheckedAnswer(text=f"{text}\n\n{DISCLAIMER}", citations=[], supported=False)

    markers = [int(m) for m in _MARKER_RE.findall(text)]
    if not markers:
        raise CitationError("La respuesta no cita ninguna fuente")
    invalid = sorted({m for m in markers if not 1 <= m <= len(chunks)})
    if invalid:
        raise CitationError(f"Citas inexistentes: {invalid}")

    citations = [
        Citation(marker=m, chunk_id=chunks[m - 1].chunk_id, label=chunks[m - 1].label())
        for m in sorted(set(markers))
    ]
    return CheckedAnswer(text=f"{text}\n\n{DISCLAIMER}", citations=citations, supported=True)
