"""Lenguaje prudente: la IA nunca emite veredictos de seguridad estructural.

CIVIA no dice "esta estructura es segura/insegura" ni "cumple la norma": usa lenguaje de
revisión ("posible inconsistencia detectada… se recomienda revisar…"). Este módulo detecta
veredictos en un texto generado para rechazarlo o pedir al modelo que lo reformule.
"""

import re
from dataclasses import dataclass

_VERDICTS = [
    r"\b(la|esta|esa|dicha)\s+(estructura|columna|viga|zapata|losa|edificaci[oó]n|cimentaci[oó]n)"
    r"\s+(es|est[aá])\s+(segura|insegura|correcta|bien\s+diseñad[ao])",
    r"\b(cumple|no\s+cumple)\s+(con\s+)?(la\s+)?(norma|nsr-10|normativa)",
    r"\bes\s+(completamente\s+)?segur[ao]\b",
    r"\bes\s+insegur[ao]\b",
    r"\b(est[aá]|esto\s+est[aá])\s+mal\s+(diseñad[ao]|calculad[ao])",
    r"\bse\s+(va\s+a|puede)\s+(caer|colapsar)\b",
    r"\b(the\s+)?structure\s+is\s+(safe|unsafe)\b",
    r"\b(complies|does\s+not\s+comply)\s+with\b",
]
_VERDICT_RE = re.compile("|".join(_VERDICTS), re.IGNORECASE)

SUGGESTED_PHRASING = (
    "Usa lenguaje de revisión: «Posible inconsistencia detectada en …; se recomienda revisar …». "
    "La validación corresponde al profesional responsable."
)


@dataclass(frozen=True)
class LanguageIssue:
    phrase: str
    start: int


def find_verdicts(text: str) -> list[LanguageIssue]:
    return [LanguageIssue(m.group(0), m.start()) for m in _VERDICT_RE.finditer(text)]


def is_prudent(text: str) -> bool:
    return not find_verdicts(text)
