# ADR-0002: Cuatro módulos desde el inicio y reglas de dependencia entre ellos

Fecha: 2026-11-15
Estado: propuesta

## Contexto
Hay que decidir cómo se organiza el código de este servicio antes de escribir el primero. La skill `hexagonal-catalogo`
define el módulo (*slice*) como un agregado con sus tres capas, y pide que entre módulos se dependa del puerto de entrada
o del modelo, nunca de la infraestructura. El catálogo se quedó con un solo módulo por falta de tiempo y dejó la migración
para después (ADR-0033 del catálogo, estimada en ~90 archivos). Acá se parte de cero, así que separar cuesta poco ahora.

Restricciones del enunciado que pesan en la partición:
- **E §3 y E §4.2:** turnos no guarda ni replica el catálogo. Antes de iniciar una operación nueva debe obtener del
  catálogo la información vigente; solo puede conservar en una reserva datos descriptivos históricos del profesional.
- **E §8:** si el catálogo no está disponible, no pueden iniciarse operaciones que requieran validar información vigente.
  Crear un hold es una de ellas: **`reservation` depende del catálogo**, aunque sea de forma indirecta.
- **E §4.2:** cada proceso de reserva queda asociado al usuario final que lo inició.

## Opciones consideradas

### Cantidad de módulos
1. **Un solo módulo, como el catálogo.**
   + Menos paquetes al principio.
   − Repite la deuda que el ADR-0033 del catálogo ya reconoció, y las reglas por módulo no tendrían qué verificar.
2. **Un módulo por responsabilidad, desde el inicio.**
   + Las fronteras quedan en el árbol de paquetes y en ArchUnit desde la primera clase.
   − Cada módulo exige su propia fachada, puertos y adaptadores, incluso para consultas finas.

### Dónde vive la disponibilidad
A. **Módulo propio `availability`.** Sus slots se calculan a partir de la agenda y de las ocupaciones y no forman parte del
   agregado de la reserva. Es el término de E §7, I §8 y T6.
B. **Dentro de `reservation`.** Un solo cliente de la cátedra, pero mezcla un cálculo sin estado con el proceso persistente
   y su máquina de estados (T7 a T12).
C. **Dentro del módulo que consulta al catálogo.** Le sumaría una dependencia de la cátedra (ocupaciones) a un módulo que
   solo debería hablar con el catálogo.

### Dónde viven la búsqueda y las categorías
A. **Módulo propio `professional`.** Un único cliente del catálogo, un único emisor del JWT técnico y una sola traducción
   de errores. `availability` lo usa por su puerto de entrada.
B. **Dentro de `availability`.** Quedan menos módulos, pero la búsqueda y las categorías no son disponibilidad.
C. **Un módulo `search` y un cliente del catálogo en cada módulo.** Duplica el firmado RS256 y el manejo de claves.

El nombre `catalog` se descartó: es el del otro servicio y sugiere que turnos es dueño del catálogo (E §4.2).

## Decisión
Opción 2, con `availability` propio (A) y `professional` propio (A). Cuatro módulos bajo
`com.ndonozo.servicioturnosreservas`, más `config/` en la raíz:

| Módulo | Responsabilidad | Issues |
|---|---|---|
| `user` | Registro, login y emisión del JWT del usuario final | T2, T3 |
| `professional` | Consultas al catálogo: búsqueda, categorías y agenda; emisor del JWT técnico | T4, T5 |
| `availability` | Slots libres: agenda del profesional más ocupaciones de la cátedra | T6 |
| `reservation` | Procesos de reserva, hold, máquina de estados, Kafka, consulta y cancelación propias | T7 a T12 |

### Dependencias permitidas
```
user              (independiente)
professional      (independiente: solo habla con el catálogo externo)
availability  ->  professional   (puerto de entrada de la agenda)
reservation   ->  availability   (puerto de entrada; p. ej. validar que un horario sea reservable)
```
- `reservation` llega al catálogo **a través de `availability`** (E §4.2, E §8): para iniciar un hold valida primero que el
  horario sea reservable con datos vigentes. El nombre exacto del puerto se define en T6 y T7.
- `reservation` recibe el identificador del usuario autenticado como parámetro desde su capa web; no depende de `user`.
- `availability` y `reservation` llaman a la cátedra cada uno con su propio adaptador y sus propios DTO (duplicar antes que
  compartir). No hay módulo `shared`.
- `config/` de la raíz contiene solo **configuración técnica** (beans de `RestClient`, propiedades, cadena de seguridad,
  manejo global de errores). No tiene lógica de negocio, y ni `domain` ni `application` pueden depender de él.

### Reglas de ArchUnit
| Regla | Qué hace |
|---|---|
| `MODULO_SOLO_TIENE_TRES_CAPAS` (adaptada) | Todo lo que cuelga del paquete base, salvo `config` y la clase raíz, debe estar en `<módulo>.{domain,application,infrastructure}`. Un módulo nuevo queda cubierto sin editar la regla |
| `DOMINIO_ES_PURO`, `APLICACION_NO_CONOCE_INFRAESTRUCTURA`, `WEB_NO_VE_PERSISTENCIA_NI_PUERTOS_DE_SALIDA`, `PUERTOS_DE_ENTRADA_SOLO_SE_IMPLEMENTAN_EN_APLICACION`, `SERVICE_SOLO_EN_LAS_FACHADAS`, `SIN_INYECCION_POR_CAMPO` | Se heredan del catálogo sin cambios: sus patrones son por capa (`..domain..`) y ya valen para cada módulo |
| Las dos reglas de la cátedra del catálogo | **No se heredan**: son de la sincronización. Reaparecen adaptadas cuando existan los adaptadores de la cátedra (T6 y T7) |
| **Nueva:** un módulo no depende de la infraestructura de otro | Un módulo solo puede depender de `<otro>.domain.model` y `<otro>.domain.ports.in`. Los módulos se detectan por el nombre del paquete. **Estricta desde el inicio**: la fachada, la infraestructura y las excepciones de otro módulo quedan prohibidas |
| **Nueva:** `professional` no usa persistencia | Ninguna clase de `professional` puede depender de `jakarta.persistence`, de Spring Data ni de las otras vías de acceso a base (`org.hibernate`, `org.springframework.jdbc`, `org.springframework.orm`, `java.sql`, `javax.sql`) |

Cada regla nueva lleva dos fixtures: uno que la viola y otro que la respeta, para comprobar que detecta lo que protege y que
no bloquea de más (`docs/FLUJO-DE-TRABAJO.md`, «Revisar la mutación»). La regla entre módulos recibe el paquete base como
parámetro para poder probarse con fixtures.

### Forma de verificar la prohibición de duplicar el catálogo
La regla `professional` no usa persistencia es **la forma verificable** de E §3 y E §4.2: sin acceso a base, ese módulo no
puede almacenar el catálogo, ni como réplica ni como caché. Alcance y límites:
- Cubre el código del módulo. **No** detecta una migración de Flyway que cree una tabla de profesionales: las migraciones se
  revisan a mano.
- La lista ampliada (`hibernate`, `jdbc`, `orm`, `java.sql`, `javax.sql`) va más allá de lo pedido (`jakarta.persistence` y
  Spring Data): sin ella, un `JdbcTemplate` esquivaría la regla. Se puede acotar si se prefiere.
- Los datos descriptivos históricos del profesional que E §4.2 permite conservar viven en `reservation`, no en `professional`.

## Justificación
Partir de cero hace barato lo que el catálogo dejó como deuda. Cada módulo tiene un motivo de cambio distinto: usuarios y
seguridad, consultas al catálogo, cálculo de slots y proceso de reserva. El grafo de dependencias refleja las exigencias del
enunciado: lo que necesite información vigente del catálogo la obtiene por la cadena `reservation → availability →
professional`, y solo `professional` conoce al catálogo (ADR-0036 y ADR-0037 del catálogo). La regla estricta entre módulos
y la de persistencia convierten las prohibiciones de E §3 en tests que fallan.

## Desvíos conocidos respecto de la skill
Se registran sin decidir que sean correctos; se revisan si surgen problemas.
1. **Las carpetas de cada capa nacen con su primer archivo.** La skill pide que las tres carpetas existan siempre, con
   `.gitkeep`. Acá no se crea ninguna carpeta vacía: en T1 no existe ningún módulo; `user` aparece en T2 y `professional` en
   T4. Una capa ausente en un módulo no es una violación.
2. **Sin módulo `shared`.** Es coherente con «duplicá primero», pero la skill admite compartir conceptos transversales. Lo
   único común es `config/`.
3. **`availability` no tiene persistencia propia.** La skill define el slice como un agregado y su receta es de CRUD;
   no cubre un slice cuyo dominio es un cálculo sobre datos ajenos. Se lo trata como un slice por responsabilidad.

## Consecuencias
- T1 no crea carpetas de módulos: deja el `pom.xml`, Docker Compose, `.env.example` y las reglas de ArchUnit con sus fixtures.
  Con cero módulos las reglas pasan en vacío (`allowEmptyShould`), así que los fixtures son la única prueba real.
- **Pendiente para T6 y T7:** cómo informa un puerto de entrada que el horario no es reservable o que el catálogo no responde
  (E §8). La regla estricta prohíbe usar las excepciones de otro módulo. Inclinación: que el puerto devuelva un resultado
  como tipo de `domain.model` (la skill prefiere resultado a excepción), en lugar de abrir la regla. Se decide con su ADR.
- **No verificado por regla:** la dirección del grafo. La regla genérica permite a cualquier módulo depender del modelo y de
  los puertos de entrada de otro, así que nada impide hoy que `reservation` dependa de `professional` o que dos módulos se
  referencien mutuamente. Se propone, sin haberlo decidido, una regla que liste los pares permitidos. Pendiente de
  confirmar.
- Cada módulo con consultas al catálogo o a la cátedra necesita su propia fachada y puertos, incluso cuando solo reenvía
  (`professional` en T5). Se acepta ese costo.
- Si se cambia el nombre de un módulo, se cambia el nombre del paquete: las reglas no listan módulos, no hace falta tocarlas.
