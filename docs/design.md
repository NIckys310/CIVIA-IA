# Diseño de la capa de IA

## Responsabilidades

| Hace | No hace |
|---|---|
| Leer planos (texto de PDF, DXF; visión con Claude para PDF e imágenes) | Calcular cargas, esfuerzos o dimensionamiento |
| Señalar lo que falta en planos, modelos 3D y proyectos | Decidir si algo "es seguro" o "cumple" |
| Responder preguntas con fuentes de la biblioteca | Inventar artículos de norma |
| Proponer volumetrías de anteproyecto desde índices urbanísticos | Seguir instrucciones escritas dentro de un plano |

## Motor dual

```
Pregunta / archivo ──▶ motor local (siempre) ──▶ ¿Claude disponible y archivo admitido?
                                                   │ sí: Claude con el resultado local como contexto
                                                   │     → validación (JSON, lenguaje prudente, citas)
                                                   │     → si falla, se usa el resultado local
                                                   └ no: resultado local
```

- Límites de la API respetados: PDF ≤ 24 MB y imágenes ≤ 5 MB se envían a Claude; el resto se
  analiza localmente.
- `fallbacks: "default"` (beta `server-side-fallback-2026-07-01`): si el modelo declina por
  política, la API reintenta con un modelo de respaldo.

## Seguridad (OWASP Top 10 for LLM Applications)

| Riesgo | Control |
|---|---|
| LLM01 Prompt injection | `UntrustedContent.wrap`: delimitador aleatorio por petición, neutraliza marcado falso, señala frases de inyección |
| LLM02 Manejo inseguro de la salida | La salida nunca se ejecuta; la interfaz escapa todo el HTML; `Citations.check` valida citas; `PrudentLanguage` suaviza veredictos |
| LLM06 Divulgación | Solo se envía el proyecto activo y los archivos que el usuario adjunta |
| LLM09 Dependencia excesiva | Aviso "Requiere revisión y aprobación del profesional responsable" |
| LLM10 Consumo sin límites | Tamaños máximos por archivo, `max_tokens`, límite de peticiones por IP en la API |

## Biblioteca

Contenido propio en `src/main/resources/civia/library/*.json` (categorías, artículos con secciones
`##`, glosario). Para ampliarla, agrega artículos al JSON o desde la interfaz ("Aportar un
artículo"). Los valores son orientativos y siempre remiten a la normativa local.

Archivos de artículos: `articles-diseno.json` (historia, diseño, programa), `articles-tecnica.json`
(estructuras, materiales, instalaciones, normativa, sostenibilidad), `articles-practica.json`
(planos, BIM, urbanismo, gestión, interiores) y `articles-ingenieria.json` (geotecnia, hidráulica,
vías, topografía, puentes, seguridad en obra y complementos de ingeniería civil).

## Copilot con Claude

Con `ANTHROPIC_API_KEY`, el Copilot conversa como un experto: responde cualquier pregunta, con
prioridad en ingeniería civil, plantea los cálculos paso a paso y cita la biblioteca cuando la usa.
`LlmProvider.stream` entrega el texto a medida que se genera; la respuesta final pasa por
`PrudentLanguage` y la verificación de citas antes de guardarse.
