# Servicio de Turnos y Reservas

Backend 2 de 2 del proyecto integrador 2026 (Programación 2). Registra y autentica a los usuarios finales, construye la
disponibilidad de turnos y gestiona el proceso de reserva con la cátedra (REST + Kafka). Consume el servicio de catálogo
(`servicio-catalogo-sincronizacion`) por HTTP con un JWT técnico.

> **Estado:** en construcción. Hoy es un esqueleto de Spring Boot; el plan está en `docs/BacklogServicioTurnos.md`.

## Documentación
| Documento | Contenido |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Contexto y reglas para el agente de IA |
| [`docs/FLUJO-DE-TRABAJO.md`](docs/FLUJO-DE-TRABAJO.md) | Cómo se trabaja: roles, ciclo por funcionalidad y trampas conocidas |
| [`docs/DECISIONES.md`](docs/DECISIONES.md) | Decisiones heredadas y pendientes |
| [`docs/BacklogServicioTurnos.md`](docs/BacklogServicioTurnos.md) | Issues y milestones |
| [`docs/fuentes.md`](docs/fuentes.md) | Mapa de las fuentes de verdad (enunciado y referencia de integración) |

## Requisitos y ejecución
Se completan con el issue T1 (base del servicio): JDK 25, Docker y Docker Compose, y el `.env` con la configuración.
