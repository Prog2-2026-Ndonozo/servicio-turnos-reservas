# Servicio de Turnos y Reservas

Backend 2 de 2 del proyecto integrador 2026 (cátedra Programación 2). Es dueño de los **usuarios finales** (registro y
login), de la **disponibilidad**, de los **procesos de reserva** y de las **reservas**, y aplica la propiedad por usuario.
Habla con la cátedra (REST + Kafka) y consume `servicio-catalogo-sincronizacion` **solo por HTTP + JWT técnico**. La app
KMP habla **únicamente con este servicio** (ADR-0036 del catálogo): nunca con el catálogo ni con la cátedra.

## Empezar una sesión
1. Leé `docs/FLUJO-DE-TRABAJO.md`: cómo trabajamos (roles, ciclo por funcionalidad, trampas ya conocidas).
2. Leé `docs/DECISIONES.md` (heredadas y pendientes) y, de `docs/BacklogServicioTurnos.md`, solo el issue en curso.

## Fuente única de la verdad
`../documentacion/PROJECT_STATEMENT-v1.md` (**E**) y `../documentacion/INTEGRATION_REFERENCE-v2.md` (**I**). Ganan sobre
todo lo demás. `docs/fuentes.md` mapea tema → sección → líneas: no leas E ni I completos salvo pedido. Si falta un dato
del contrato, **preguntar o señalar el gap; nunca inventar** campos, estados ni endpoints.

## Qué leer según la tarea
| Tarea | Leer |
|---|---|
| Llamar al catálogo (token, endpoints, errores) | `../servicio-catalogo-sincronizacion/docs/contratos/catalogo-para-turnos.md` (la fuente vive allá: no copiarla) |
| Decisiones que ya afectan a turnos | `docs/DECISIONES.md` + ADR-0035, 0036 y 0037 en `../servicio-catalogo-sincronizacion/docs/adr/` |
| Ubicar/crear una clase, casos de uso, adaptadores, tests | skill hexagonal: `../../hexagonal/.agents/skills/hexagonal-catalogo/` (`SKILL.md` y solo la referencia que toque) |
| Un ADR nuevo | `docs/adr/ADR-template.md` |
| Vocabulario | `../GLOSARIO.md` |

## Stack (no cambiar tecnologías ya propuestas)
Java 25, Spring Boot 4.1.1, Maven (`./mvnw`), **PostgreSQL propio y exclusivo** (prohibido H2/SQLite/embebidas como
persistencia principal, E §3; el esqueleto actual trae H2: hay que sacarlo), Flyway, Kafka, springdoc, Lombok, ArchUnit,
Testcontainers. Comandos: `./mvnw test` · `./mvnw spring-boot:run` · `docker compose up -d`.

## Alcance
Hace: registro y login, disponibilidad (horarios del catálogo + ocupaciones de la cátedra), holds, confirmación REST+Kafka,
estado de reservas, consulta y cancelación propias, recuperación. **No** guarda el catálogo ni lo cachea como vigente
(E §3, E §4.2): es de `servicio-catalogo-sincronizacion`.

## Reglas para el agente
- Actuá como **copiloto**: el alumno (Nehuen) aprende hexagonal. Mostrá el código en el chat, explicá y esperá su OK;
  **no edites código de producción sin un pedido claro**. Tests y documentación sí los escribe el agente.
- PROHIBIDO commitear, crear ramas o pushear. Git solo para leer. Al llegar a un commit, recordá **Conventional Commits**.
- Todo cambio de diseño (máquina de estados, idempotencia, seguridad, módulos) va a `docs/DECISIONES.md` con su ADR.
  Si el alumno toma un camino que se aparta de un ADR o de la skill, **resaltalo** antes de seguir.
- Secretos: JWT técnico de la cátedra, claves privadas y contraseñas **nunca** se loguean, versionan ni exponen. Viven en
  `.env` (gitignoreado) y no se pegan en el chat.
- **Espejo de docs:** todo cambio en `docs/` se refleja en `../documentacion/servicio-turnos/docs/` (ver `docs/FLUJO-DE-TRABAJO.md`).
- Informá con honestidad qué verificaste y qué no (si un test no corrió o Docker estaba apagado, decilo).

## Entorno
Java 25 por SDKMAN (`~/.sdkman/candidates/java/25.0.2-tem`): la terminal puede traer Java 24 por defecto y Maven falla con
`release version 25 not supported`. Los tests con Testcontainers necesitan Docker corriendo.
