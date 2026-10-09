# CIVIA — IA

Capa de inteligencia artificial de **CIVIA AI**, el copiloto digital del ingeniero civil.

```
IA → interpreta · Motor de ingeniería → calcula · Normativa → establece criterios · Ingeniero → valida
```

**Este repositorio nunca hace cálculos estructurales ni emite veredictos de seguridad.**
La IA interpreta planos y resultados, redacta observaciones y responde con fuentes citadas;
los cálculos los hace el motor determinista de [CIVIA-BACKEND](https://github.com/NIckys310/CIVIA-BACKEND).

| Repositorio | Contenido |
|---|---|
| [CIVIA-FRONTEND](https://github.com/NIckys310/CIVIA-FRONTEND) | Web (PWA) y app móvil |
| [CIVIA-BACKEND](https://github.com/NIckys310/CIVIA-BACKEND) | API, base de datos, motor de cálculo |
| **CIVIA-IA** (este) | Visión, OCR, RAG con citas, LLM |

## Estado

| Módulo | Estado |
|---|---|
| `untrusted` — contenido de documentos como datos, no instrucciones (anti *prompt injection*) | ✔ |
| `citations` — respuestas del Copilot solo con citas a fuentes recuperadas | ✔ |
| `language` — detecta veredictos ("la estructura es segura") para reformular | ✔ |
| Preprocesado de planos (OpenCV), OCR (PaddleOCR), detección de elementos (YOLO) | Fase 1 |
| RAG sobre la biblioteca normativa (pgvector + reranking) | Fase 1 |
| Cliente LLM (Claude por defecto, proveedor intercambiable) | Fase 1 |

Diseño completo: [docs/design.md](docs/design.md).

## Puesta en marcha

```bash
pip install uv
uv sync --python 3.12
uv run pytest
uv run ruff check . && uv run mypy src
```

[Cómo contribuir](CONTRIBUTING.md) · [Seguridad](SECURITY.md)
