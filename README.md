# CIVIA — IA

Capa de inteligencia artificial de **CIVIA AI · Arquitectura inteligente**, en Java 21. Se
publica como librería Maven (`com.civia:civia-ai`) que usa CIVIA-BACKEND.

```
IA → interpreta · Normativa → establece criterios · Profesional → valida
```

**La IA nunca emite veredictos de seguridad estructural ni de cumplimiento normativo.**

**🌐 App en línea: [civia-black.vercel.app](https://civia-black.vercel.app)** · API: [civia-api-i71z.onrender.com](https://civia-api-i71z.onrender.com/api/v1/status)

| Repositorio | Contenido | Tecnología |
|---|---|---|
| [CIVIA-FRONTEND](https://github.com/NIckys310/CIVIA-FRONTEND) | Interfaz web (PWA, escritorio y móvil) y su servidor | HTML/CSS/JS + Three.js · servidor Java 21 |
| [CIVIA-BACKEND](https://github.com/NIckys310/CIVIA-BACKEND) | API, CIVIA Auth, base de datos, archivos | Java 21 · Spring Boot 3.5 · H2/PostgreSQL |
| [CIVIA-IA](https://github.com/NIckys310/CIVIA-IA) | Copilot, análisis de planos y modelos 3D, biblioteca | Java 21 · Claude (opcional) + motor local |

## Módulos

| Paquete | Qué hace |
|---|---|
| `llm` | `ClaudeProvider` (SDK oficial de Anthropic para Java, modelo `claude-opus-5-5`, PDF e imágenes, respaldo automático ante rechazos, respuestas en streaming). Se activa con `ANTHROPIC_API_KEY`; `CIVIA_AI_EFFORT` (`low`…`max`, por defecto `high`) ajusta la profundidad de razonamiento. |
| `llm` (gratis) | `OpenAiCompatibleProvider`: modelos abiertos con plan gratuito y formato de OpenAI, en streaming. **Groq** (`GROQ_API_KEY`, modelo `CIVIA_GROQ_MODEL`, por defecto `openai/gpt-oss-120b`), **Cerebras** (`CEREBRAS_API_KEY`) y **OpenRouter** (`OPENROUTER_API_KEY`, modelos `:free`). Solo texto: el análisis visual de planos lo hace el motor local. `FallbackProvider` los prueba en orden (Claude → Groq → Cerebras → OpenRouter → motor local) y pasa al siguiente si uno agota su cuota o falla. |
| `assistant` | **CIVIA Copilot** (`ArchitectAssistant`): conversa como un experto en ingeniería civil y responde cualquier pregunta, cita la biblioteca, usa el contexto del proyecto, entrega el texto a medida que se genera y cae al motor local si Claude no está disponible. `ProjectAdvisor`: qué le falta al proyecto. |
| `plans` | `PlanAnalyzer`: PDF (PDFBox: texto, páginas, formato de papel), imágenes (resolución, contraste), DXF (entidades, capas, unidades) y DWG. 8–16 verificaciones con recomendaciones; con Claude, lectura visual del plano. |
| `models` | `ModelAnalyzer`: revisa un modelo 3D a partir de sus estadísticas (unidades, escala, peso, componentes que faltan). `MassingAdvisor`: volumetría máxima según lote, índices y retiros. |
| `library` | `KnowledgeBase`: 19 categorías, 144 artículos y 265 términos de ingeniería civil y arquitectura en español (geotecnia, hidráulica, vías, topografía, puentes, seguridad en obra, estructuras, BIM, gestión…), búsqueda BM25 con normalización para español. Admite artículos del estudio. |
| `safety` | `UntrustedContent` (anti *prompt injection*), `Citations` (citas válidas), `PrudentLanguage` (sin veredictos). |

## Uso

```java
CiviaAi ai = CiviaAi.create(System.getenv("ANTHROPIC_API_KEY"), "claude-opus-5-5");
var answer = ai.assistant().ask("¿Cómo calculo una escalera?", List.of(), null, List.of());
// En streaming: cada fragmento llega a medida que Claude lo escribe.
ai.assistant().ask("¿Qué es la licuación?", List.of(), null, List.of(), System.out::print);
var report = ai.plans().analyze(new PlanAnalyzer.PlanInput("A-101.pdf", "application/pdf", bytes, null));
```

## Calidad

```bash
./mvnw verify       # instala con: ./mvnw install
```

Diseño completo: [docs/design.md](docs/design.md) · [Cómo contribuir](CONTRIBUTING.md) · [Seguridad](SECURITY.md)
