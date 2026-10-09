# Política de seguridad de CIVIA

## Reportar una vulnerabilidad

Si encuentras una vulnerabilidad, **no abras un issue público**. Usa
[GitHub Security Advisories](../../security/advisories/new) de este repositorio
(reporte privado). Incluye pasos para reproducir, impacto y versión afectada.

- Acuse de recibo: 3 días hábiles.
- Evaluación inicial: 10 días hábiles.
- Divulgación coordinada: publicamos el aviso cuando la corrección esté disponible
  y damos crédito a quien reporta, salvo que prefiera el anonimato.

## Alcance

API (`services/api`), web (`apps/web`), app móvil (`apps/mobile`) y paquetes compartidos.
Fuera de alcance: ataques de denegación de servicio volumétricos, ingeniería social y
hallazgos en dependencias sin un vector explotable en CIVIA.

## Datos personales

CIVIA trata datos personales conforme a la **Ley 1581 de 2012 (Habeas Data, Colombia)**.
Los planos de los clientes son propiedad intelectual confidencial: un incidente que los
exponga se trata como severidad crítica.

Ver también [`docs/security/threat-model.md`](docs/security/threat-model.md).
