# Decisiones — servicio de turnos y reservas

Registro de decisiones de diseño. Cada decisión nueva lleva su ADR en `docs/adr/` (plantilla: `docs/adr/ADR-template.md`).
Las decisiones heredadas viven como ADR en el repo hermano `../servicio-catalogo-sincronizacion/docs/adr/`.

## Decididas (heredadas del servicio de catálogo)

| Decisión | Resumen | Fuente |
|---|---|---|
| El registro y el login del usuario final viven **en este servicio** | Turnos emite y valida los JWT de usuario; el catálogo nunca los ve | ADR-0035 (catálogo) |
| La app KMP habla **solo con este servicio** | Turnos expone a KMP la búsqueda, las categorías y la disponibilidad, consultando al catálogo. KMP no llama al catálogo ni a la cátedra | ADR-0036 (catálogo), I §17 |
| Turnos llama al catálogo con un **JWT técnico RS256** | `iss=servicio-turnos`, `aud=servicio-catalogo`, `sub=servicio-turnos`, `scope=catalog:read`, vigencia 5 min, `jti`, `userId` opcional e informativo. La clave **privada** solo existe acá; el catálogo guarda la pública | ADR-0037 (catálogo) |
| Contrato catálogo → turnos | Operaciones, modelos, errores y reglas de uso (ignorar horarios y profesionales con `enabled=false`; no cachear el catálogo como vigente) | `../servicio-catalogo-sincronizacion/docs/contratos/catalogo-para-turnos.md` |
| "Disponibilidad" en el filtro del catálogo es **un prefiltro** | `availableOn` = atiende ese día de la semana. La disponibilidad real (slots libres) la construye turnos con las ocupaciones de la cátedra | ADR-0034 (catálogo), E §7 |
| **CORS no se implementa** | Ningún navegador ni app llega a los backends sin pasar por turnos; según el profesor (verbal, a confirmar por escrito) su mención en E §9 es un error del documento | DECISIONES del catálogo |
| Arquitectura hexagonal con ArchUnit | Dominio sin framework; adaptadores en infraestructura; los drivers entran por la fachada `@Service`. La skill gana ante una duda de diseño | skill `hexagonal-catalogo`, `docs/` del catálogo |
| Un issue por milestone, una rama por issue, un PR por rama | Commits Conventional Commits; código, tests y docs en commits separados | `docs/FLUJO-DE-TRABAJO.md` |

## Decididas en este servicio

Aceptadas el 2026-11-16, tras la confirmación del alumno.

| Decisión | Resumen | Dónde |
|---|---|---|
| PostgreSQL y Flyway | PostgreSQL propio y exclusivo; migraciones SQL planas; Hibernate en `validate`; se saca H2 del esqueleto | `adr/0001-postgresql-y-flyway.md` |
| Cuatro módulos desde el inicio y reglas entre ellos | `user`, `professional`, `availability`, `reservation` + `config/` técnico; grafo `reservation → availability → professional`, `user` independiente; un módulo solo ve el modelo y los puertos de entrada de otro; reglas de ArchUnit que fijan los pares permitidos y prohíben ciclos; `professional` sin persistencia (cubre la base, no las cachés en memoria: esas se revisan a mano); sin `shared`; carpetas con el primer archivo | `adr/0002-organizacion-en-modulos.md` |

## Pendientes (decisiones a tomar en este servicio)

| Decisión | Bloquea | Notas |
|---|---|---|
| Cómo informa un puerto de entrada «no reservable» o «catálogo caído» entre módulos | T6, T7 | La regla estricta prohíbe usar las excepciones de otro módulo. Inclinación: resultado como tipo de `domain.model` (ADR-0002). «Catálogo caído» es un fallo de infraestructura: si va como valor del resultado, se traduce a `503` en el borde web |
| Contrato y validaciones del usuario compatible con JHipster | T2 | E §3.2 dice "respetando las validaciones definidas por la cátedra", que no figuran en E ni en I (I §5.1 trae las de la cuenta técnica). **Gap a confirmar.** Rutas habituales de JHipster: `/api/register` y `/api/authenticate` (supuesto) |
| JWT del usuario: algoritmo, clave, vigencia y claims | T3 | Solo turnos lo emite y lo valida, así que una clave simétrica es posible. Hash de contraseñas con BCrypt |
| Qué es el `externalPatientId` | T7 | I §2 pide "un identificador estable del usuario dentro de la aplicación"; propuesta: su ID interno inmutable, nunca el `login` |
| Máquina de estados de la reserva | T7-T9 | E §10 la deja a cargo del alumno; E §7: no retroceder desde un estado final. Estados externos en I §10, §11 y §15 |
| Idempotencia | T8-T9 | Por `eventId` de Kafka y por `reservationProcessId`. Aprendizaje del catálogo (ADR-0028): registrar el id procesado **en la misma transacción** que el efecto |
| Algoritmo de disponibilidad | T6 | Slots a partir de los horarios habilitados (`slotDurationMinutes`) menos las ocupaciones `CONFIRMED` y `HELD` (I §8). Las horas son locales de atención (I §4) |
| Persistencia del progreso y recuperación al reiniciar | T12 | E §8, I §18.3 y §18.4: reconciliar con REST usando los identificadores persistidos |
| Timeouts, reintentos y backoff | T4, T12 | Reintentos limitados y observables (I §18.4). Referencia: ADR-0030 del catálogo (backoff exponencial del listener) |
| Contrato REST hacia KMP | T5-T11 | Endpoints y DTO; se documenta como contrato (E §12) |
| Cómo se filtran las reservas por usuario | T10 | `GET /api/appointments` devuelve las de toda la cuenta técnica: hay que aplicar la propiedad por usuario (I §11) |
| Topics, grupo de consumo y claves de Kafka | T8 | Vienen de la configuración de integración (I §5.1); la key es `reservationProcessId` (I §15) |
| Dockerización del servicio | T1, T14 | E §3: "la infraestructura local de los dos servicios backend, sus bases de datos y las dependencias necesarias deberá poder levantarse mediante Docker Compose". Ambiguo: ¿incluye los servicios o solo sus bases? **Se pregunta a la cátedra el 17/11.** Que el servicio nazca con `Dockerfile` y Compose, sin bloquear el hito del 17/11 |
