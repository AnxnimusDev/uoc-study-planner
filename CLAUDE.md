# UOC Study Planner

Herramienta local que lee el aula Canvas de la UOC (`aula.uoc.edu`) con la sesión del propio alumno,
extrae los requisitos de cada reto con IA y genera un plan de estudio ajustado a sus horas libres.
Salidas: feed de calendario `.ics`, Recordatorios de Apple (macOS) y PDF semanal por correo.

Diseño completo: `docs/design.md`. Léelo antes de cualquier tarea que toque arquitectura,
modelo de datos, extractor o planificador. Decisiones cerradas: `docs/adr/`.

## Reglas que no se negocian

1. **Solo lectura contra Canvas.** Únicamente peticiones `GET` a `aula.uoc.edu`. Nunca POST, PUT,
   PATCH ni DELETE. Nunca envíes, modifiques ni marques nada en el aula. Hay un test que lo verifica.
2. **Nunca guardes ni pidas la contraseña de la UOC.** La autenticación es por cookie de sesión
   capturada tras un login interactivo (ver ADR 0002). La cookie se guarda cifrada.
3. **La IA extrae requisitos, nunca genera contenido de entregas.** El extractor no propone
   soluciones, ejemplos de respuesta ni redacción. Varias asignaturas prohíben o limitan la IA
   (ver ADR 0003). No añadas funciones que "ayuden a resolver" retos.
4. **Ningún secreto en el repositorio.** Ni cookies, ni claves de API, ni SMTP, ni datos personales.
   No leas `.env`, `data/` ni ficheros `*.cookie`. Si necesitas un valor, pídelo o usa `.env.example`.
5. **Ningún test llama a la red real.** Canvas se simula con WireMock sobre los fixtures de
   `backend/src/test/resources/fixtures/canvas/`; la API de Claude, con respuestas grabadas.
6. **Nada de datos de otros alumnos.** No se procesan ni se envían a la IA foros, nombres ni notas.

## Estructura

```
backend/            Spring Boot 3 + Java 21, monolito modular (Spring Modulith)
frontend/           React 19 + TypeScript + Vite (fase 3; vacío al principio)
reminders-bridge/   CLI Swift + EventKit para Recordatorios (solo macOS, fase 2)
docs/               design.md, adr/, canvas-api-notes.md, challenge-spec.schema.json
scripts/            utilidades (anonimizar fixtures)
config/             ejemplos de configuración del alumno
```

Cada subcarpeta con código tiene su propio `CLAUDE.md` con detalles locales.

## Módulos del backend (paquete raíz `edu.uoc.planner`)

| Paquete | Responsabilidad |
|---|---|
| `auth` | Login con Playwright, captura y cifrado de la cookie, detección de sesión caducada |
| `canvas` | Cliente HTTP de Canvas, paginación, sincronización, normalización a dominio |
| `extraction` | Enunciado HTML → `ChallengeSpec` JSON validado (SDK oficial de Anthropic, ADR 0006) |
| `availability` | Semana tipo, excepciones y check-ins → franjas libres |
| `planning` | Subtareas + franjas + plazos → plan (voraz en MVP, Timefold en fase 2) |
| `scheduling` | Tareas programadas: sync diaria, check-ins de lunes y día 1 |
| `output` | Feed ICS, PDF, correo, llamada al puente de Recordatorios |
| `web` | API REST para la UI |
| `shared` | Tipos comunes, cifrado, reloj inyectable |

Los módulos solo se comunican por su API pública (clases en la raíz del paquete) o por eventos de
dominio. Nada de acceder a `internal` de otro módulo. `ApplicationModules.of(...).verify()` lo comprueba.

## Comandos

```bash
make up          # PostgreSQL en Docker
make test        # tests del backend (mvn verify)
make run         # arranca el backend en local (perfil dev)
make login       # abre el navegador para iniciar sesión en la UOC y guarda la cookie
make sync        # sincroniza Canvas con la sesión guardada
make fixtures    # anonimiza respuestas crudas de Canvas en fixtures de test
```

Desde `backend/`: `./mvnw verify`, `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`,
`./mvnw test -Dtest=NombreDelTest`.

## Forma de trabajar

- **Antes de programar**, lee la sección del diseño que aplica y resume en 3–5 líneas qué vas a hacer.
  Si la tarea contradice el diseño o un ADR, para y pregunta.
- **Tests primero** para lógica de dominio (normalización, pesos, franjas, planificador). Un cambio
  sin test que lo cubra no está terminado.
- **Pasos pequeños.** Una funcionalidad por rama y por PR. Conventional commits
  (`feat(canvas): ...`, `fix(planning): ...`, `test: ...`, `docs: ...`).
- **Al terminar**, ejecuta `./mvnw verify` y di qué tests pasan. No digas que algo funciona sin
  haberlo ejecutado.
- **Decisión de arquitectura nueva** → ADR nuevo con `/new-adr` antes de implementarla.
- **Dependencias**: comprueba la última versión estable antes de añadirla. No añadas librerías para
  algo que Spring o el JDK ya resuelven.
- **Dudas sobre la API de Canvas**: consulta `docs/canvas-api-notes.md` y los fixtures reales antes de
  suponer la forma de un JSON.

## Convenciones de código (Java)

- Java 21: `record` para DTOs y value objects, `sealed` para jerarquías cerradas, `switch` con patrones.
- Constructor injection, nunca `@Autowired` en campos. Clases `final` cuando no se extienden.
- Fechas: `Instant` / `OffsetDateTime` en dominio y BD (`timestamptz`). `Europe/Madrid` solo al
  presentar. El reloj se inyecta (`java.time.Clock`) para poder testear.
- Errores de Canvas como excepciones propias del módulo (`CanvasSessionExpiredException`,
  `CanvasRateLimitedException`), nunca `RuntimeException` genérica.
- Logs en inglés, sin cookies, cabeceras de autenticación ni correos (hay filtro de enmascarado).
- Identificadores de código en inglés. Textos para el alumno en español (y los títulos de tareas en
  el idioma del enunciado, normalmente catalán).
- Formato con Spotless (`./mvnw spotless:apply`).

## Hallazgos de Canvas que debes tener presentes

- Respuestas JSON con prefijo `while(1);` cuando se usa cookie: quítalo antes de parsear.
- Paginación por cabecera `Link` con `rel="next"`.
- Un reto = un módulo cuyo nombre empieza por `Repte`. El enunciado es la página con `page_url`
  que empieza por `enunciat-de-lactivitat`. Las guías semanales empiezan por `ges-` (no siempre existen).
- La entrega de un reto se resuelve por `content_id` del elemento del módulo, **nunca** por enlaces
  del HTML: muchos apuntan a cursos de semestres anteriores (p. ej. `78734`, `87921`).
- Peso de un reto = suma de `points_possible` de sus entregas con `omit_from_final_grade == false`
  entre el total del curso. `group_weight` no sirve (todo está en un grupo al 100 %).
- Entregas con `submission_types: ["external_tool"]` (cuestionarios Wiris) no tienen contenido
  legible: se planifican con duración por defecto.
- `Pla docent` y `Biblioteca` son herramientas externas (LTI): fuera de alcance.
- Cursos con `enrollment_term_id` distinto del semestre actual (la Tutoria usa el 1) se excluyen por defecto.

## Estado actual

Fase 0 (base del proyecto). Siguiente paso: ver `docs/kickoff.md`.
