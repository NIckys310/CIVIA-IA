# Cómo contribuir a CIVIA

CIVIA vive en tres repositorios:

| Repo | Contenido | Stack |
|---|---|---|
| [CIVIA-FRONTEND](https://github.com/NIckys310/CIVIA-FRONTEND) | Web (PWA) y app móvil | Next.js, Expo, TypeScript |
| [CIVIA-BACKEND](https://github.com/NIckys310/CIVIA-BACKEND) | API, base de datos, infraestructura, motor de cálculo | FastAPI, PostgreSQL, Python |
| [CIVIA-IA](https://github.com/NIckys310/CIVIA-IA) | Visión, OCR, RAG con citas, LLM | Python |

Somos dos personas. Cada quien hace **sus propios commits desde su cuenta**: el historial de
Git es la evidencia de quién hizo qué, así que nunca se commitea a nombre de otra persona ni
se agregan commits vacíos o artificiales.

## Primer día (cada integrante, en cada repo)

```bash
git clone https://github.com/NIckys310/<REPO>.git
cd <REPO>
git config user.name "Tu Nombre"
git config user.email "el-correo-de-tu-cuenta-de-github@ejemplo.com"
git switch develop
```

Comprueba que ese correo está verificado en GitHub (Settings → Emails). Si no coincide con
`user.email`, tus commits no aparecen en tu perfil ni en las estadísticas del repo.

Dependencias:

- **CIVIA-FRONTEND:** `npm install`
- **CIVIA-BACKEND / CIVIA-IA:** `pip install uv` y luego `uv sync --python 3.12`
- Hooks (todos): `uvx pre-commit install`

## Ramas

```
main      ← solo versiones estables (tags v0.x.y). Nadie hace push directo.
develop   ← integración. Entra solo por Pull Request.
feat/<módulo>-<descripción>   fix/<...>   docs/<...>   security/<...>
```

1. `git switch develop && git pull`
2. `git switch -c feat/web-plan-viewer`
3. Commits pequeños y atómicos (uno por cambio lógico); cada uno compila y pasa tests.
4. `git push -u origin feat/web-plan-viewer` y abre un PR hacia `develop`.
5. La otra persona revisa. Se integra con **merge commit** (el squash está desactivado para
   conservar los commits individuales de cada autor).

## Commits

[Conventional Commits](https://www.conventionalcommits.org/es/): `feat(api): ...`, `fix(web): ...`,
`test(engine): ...`, `docs(adr): ...`, `security(api): ...`, `refactor`, `perf`, `build`, `ci`, `chore`.
Imperativo, asunto ≤ 72 caracteres, cuerpo con el *por qué* cuando haga falta.

## Contrato entre repos

- La API publica su contrato en `CIVIA-BACKEND/services/api/openapi.json`.
- El frontend lo copia y genera tipos con `npm run contract:sync` (en CIVIA-FRONTEND).
- Un cambio de API = PR en el backend + PR en el frontend que sincroniza el contrato.

## Reparto sugerido por fases

| Área | Integrante A (NIckys310) | Integrante B |
|---|---|---|
| Fase 1 | BACKEND: subida segura de planos y versiones · IA: OCR y detección | FRONTEND: visor de planos (PDF.js + superposición), biblioteca técnica, UI de Copilot · IA: RAG con citas |
| Fase 2 | BACKEND: `civia-engine` (cargas, combinaciones, verificaciones) | FRONTEND: UI de cálculos con trazabilidad y fórmulas (KaTeX) |
| Fase 3 | BACKEND: reconstrucción 3D, IFC | FRONTEND: visor 3D (React Three Fiber) |
| Fase 4 | BACKEND: sincronización offline | FRONTEND: app móvil, CIVIA Measure, evidencia |

Cada integrante revisa los PR del otro: los comentarios y aprobaciones también quedan en GitHub.

## Antes de abrir un PR

- **FRONTEND:** `npx turbo run typecheck test` y `npm run lint -w @civia/web`
- **BACKEND:** `cd services/api && ../../.venv/Scripts/python -m pytest`, `uv run ruff check .`, `uv run mypy services/api/src`
- **IA:** `uv run pytest`, `uv run ruff check .`, `uv run mypy src`
