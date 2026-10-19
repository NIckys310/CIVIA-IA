# Cómo contribuir a CIVIA AI

CIVIA AI está dividido en tres repositorios que se clonan lado a lado:

| Repositorio | Contenido | Tecnología |
|---|---|---|
| [CIVIA-FRONTEND](https://github.com/NIckys310/CIVIA-FRONTEND) | Interfaz web (PWA, escritorio y móvil) y su servidor | HTML/CSS/JS + Three.js · servidor Java 21 |
| [CIVIA-BACKEND](https://github.com/NIckys310/CIVIA-BACKEND) | API, CIVIA Auth, base de datos, archivos | Java 21 · Spring Boot 3.5 · H2/PostgreSQL |
| [CIVIA-IA](https://github.com/NIckys310/CIVIA-IA) | Copilot, análisis de planos y modelos 3D, biblioteca | Java 21 · Claude (opcional) + motor local |

## Requisitos

- Java 21 (Temurin u Oracle). Cada repositorio Java trae Maven Wrapper (`./mvnw`).
- Git y, opcionalmente, [pre-commit](https://pre-commit.com) para el análisis de secretos.

## Flujo de trabajo

1. Crea una rama desde `develop`: `feat/…`, `fix/…`, `docs/…`.
2. Commits pequeños con [Conventional Commits](https://www.conventionalcommits.org/es/) en español
   (`feat(api): …`, `fix(web): …`).
3. Abre un PR hacia `develop` con la plantilla. La CI debe pasar.
4. `develop` se integra en `main` para publicar.

## Cambios entre repositorios

- Si cambias una clase pública de **CIVIA-IA**, instala la librería (`./mvnw install`) y compila
  **CIVIA-BACKEND** antes de abrir el PR. La CI del backend usa la rama del mismo nombre en
  CIVIA-IA si existe.
- Si cambias un endpoint de **CIVIA-BACKEND**, actualiza el cliente en `CIVIA-FRONTEND/web/js`.
  El contrato vivo está en `http://localhost:8080/api/docs` (OpenAPI).

## Calidad

- **IA:** `./mvnw verify` (pruebas de seguridad del LLM, biblioteca, planos y modelos).
- **BACKEND:** `./mvnw verify` (pruebas de integración: CIVIA Auth, archivos, IA, presupuesto, aislamiento entre usuarios).
- **FRONTEND:** `javac -d out server/CiviaWeb.java` y revisión manual en el navegador (escritorio y 375 px).

## Seguridad

Nunca subas secretos (`.env`, claves, datos de clientes). Reporta vulnerabilidades según
[SECURITY.md](SECURITY.md), no en issues públicos.
