# UOC Study Planner — Documento de diseño

19 oct 2026 · Adrián · Versión editable: https://claude.ai/code/artifact/bbabeed4-431f-4fcc-943e-fe55ef771fdf

## 1. Resumen y objetivos

UOC Study Planner es una herramienta local que lee el aula Canvas de la UOC con la sesión del propio alumno, extrae los requisitos de cada reto y genera un plan de estudio ajustado a sus horas libres reales. El plan se publica como calendario (.ics), como recordatorios de Apple y como PDF semanal por correo.

**Problema.** Un alumno que trabaja a jornada completa tiene 5–7 asignaturas en paralelo, con entregas, cuestionarios semanales, foros y vídeos de autoría repartidos en páginas distintas. Las fechas oficiales existen, pero nadie traduce "Repte 1" en horas concretas de su semana.

**Usuario objetivo.** Primero, el autor (alumno del Grau en Enginyeria Informàtica, teletrabajo con días puntuales de oficina). Después, cualquier alumno de la UOC que lo instale en su equipo.

**Objetivos**

1. Sincronizar asignaturas, retos, entregas, fechas y pesos desde Canvas sin intervención manual.
2. Convertir cada enunciado en subtareas medibles, con requisitos de formato, rúbrica y política de IA.
3. Repartir las subtareas en las horas libres del alumno, respetando un margen configurable antes de cada entrega.
4. Replanificar cada lunes y el día 1 de cada mes a partir de un check-in breve.
5. Publicar el plan en calendario, Recordatorios y PDF por correo.

**No-objetivos**

- No resuelve, redacta ni corrige actividades. Solo extrae requisitos y planifica.
- No entrega nada en Canvas ni escribe en el aula (solo lectura).
- No custodia credenciales de terceros ni ofrece un servicio en la nube compartido.
- No sustituye al Pla docent: si un dato solo está allí, el alumno lo introduce a mano.

## 2. Hallazgos validados (18–19 oct 2026)

Todo el contenido necesario para planificar sale por la API REST de Canvas usando la cookie de sesión; solo el Pla docent queda fuera. Validado contra dos asignaturas de tipo opuesto: Àlgebra (cuestionarios) e Interacció persona ordinador (proyecto). Detalle en `docs/canvas-api-notes.md`.

| Pregunta | Resultado | Implicación |
| --- | --- | --- |
| ¿Qué LMS usa la UOC? | Canvas en `aula.uoc.edu` | API REST oficial `/api/v1/*` |
| ¿Tokens personales para estudiantes? | No | Autenticación por cookie de sesión |
| ¿Responde la API con la sesión del navegador? | Sí | Acceso de solo lectura viable |
| ¿Continguts es nativo de Canvas? | Sí: `Page`, `Assignment`, `Discussion`; sin `ExternalTool` | Retos legibles íntegramente |
| ¿El enunciado está completo? | Sí, en el `body` de `enunciat-de-lactivitat-rN` | Extractor con IA viable |
| ¿Las entregas traen fechas? | Sí: `unlock_at`, `due_at`, `lock_at` | Ventanas de trabajo exactas |
| ¿Pesos de evaluación? | `group_weight` inútil; `points_possible` suma 100 | Peso por reto calculable |
| ¿Feed de calendario sin login? | Sí, `/feeds/calendars/course_<uuid>.ics` | Fechas sin sesión |
| ¿Materiales externos? | `materials.campus.uoc.edu` y `design-toolkit.recursos.uoc.edu` públicos | Procesables |
| ¿Pla docent y Biblioteca? | LTI externas | Fuera de alcance |
| ¿Cuestionarios? | LTI de Wiris | Fechas y puntos sí; preguntas no |

**Patrones observados en Continguts**

- Cada reto es un módulo con: `Plantejament i competències Rn`, `Recursos d'aprenentatge Rn`, la página de enunciado, la entrega y el `Fòrum dubtes Rn`.
- El slug `enunciat-de-lactivitat-rN` se repite aunque el título cambie.
- Àlgebra añade `GES. Guies d'estudi setmanal Rn`; IPO no las tiene.
- Àlgebra añade `Enregistraments Rn`: vídeo de autoría (ventana de 15 min, solo si te convocan); no puntúa, pero suspenderlo suspende el reto.
- Q2–Q5 de Àlgebra comparten `due_at` (30 oct) pero indican "Setmana 3–6": plazo recomendado distinto del real.
- Enlaces del HTML copiados de semestres anteriores (cursos `78734`, `87921`): no fiables.
- Muchas entregas tienen `description: null`; el enunciado vive en la página.

**Pendiente de verificar:** rúbrica en `/assignments/968028`; `locked_for_user` en retos futuros; duración de la cookie.

## 3. Restricciones y decisiones clave

La herramienta es local-first y de solo lectura: cada alumno la ejecuta en su equipo con su propia sesión, y nunca se guarda una contraseña de la UOC.

| # | Decisión | Motivo | Descartado |
| --- | --- | --- | --- |
| D1 | Ejecución local (Docker Compose) | Clave del campus personal e individual | SaaS con credenciales |
| D2 | Login interactivo con Playwright y reutilización de la cookie | No hay tokens | Guardar contraseña; extensión (futuro) |
| D3 | Solo GET contra Canvas | Cero riesgo sobre entregas | POST/PUT al aula |
| D4 | La IA extrae requisitos, nunca genera contenido | Asignaturas que prohíben/limitan IA | Asistente que resuelve retos |
| D5 | Calendario vía feed ICS | Funciona en Apple, Google y Outlook | CalDAV a iCloud |
| D6 | Recordatorios vía CLI Swift + EventKit (macOS) | Sin API web | MCP de terceros |
| D7 | Una lista por asignatura; cada paso un recordatorio | EventKit no crea grupos ni subtareas | Subtareas nativas |
| D8 | Dos niveles de plazo: blando y duro (`due_at` − margen) | Evita acumular trabajo | Solo `due_at` |
| D9 | Sesión y datos personales cifrados en disco | La cookie da acceso total al aula | Ficheros en claro |

ADRs en `docs/adr/`.

## 4. Arquitectura

Un monolito modular en Spring Boot corre en el equipo del alumno junto a PostgreSQL y una UI en React; solo salen peticiones GET a Canvas, el texto de los enunciados a la API de Claude y el PDF por SMTP. El puente de Recordatorios es un binario Swift que el backend invoca por línea de comandos (solo macOS).

```
Equipo del alumno
  Backend (Spring Boot 3 + Java 21)
    auth ─ canvas ─ extraction ─ availability ─ planning ─ scheduling ─ output ─ web
  PostgreSQL + Flyway      UI React      reminders-bridge (Swift)
Salidas externas: aula.uoc.edu (GET + cookie), Claude API (enunciados), SMTP (PDF)
Consumidores: app de calendario (feed .ics), Recordatorios (EventKit)
```

| Módulo | Responsabilidad | Tecnología |
| --- | --- | --- |
| `auth` | Login interactivo, captura y renovación de cookie, cifrado | Playwright for Java |
| `canvas` | Cliente de la API, sync incremental, normalización | WebClient + Resilience4j |
| `extraction` | Enunciado HTML → `ChallengeSpec` JSON validado | SDK oficial de Anthropic para Java, Jsoup |
| `availability` | Perfil semanal, excepciones, check-ins → franjas libres | Dominio puro |
| `planning` | Subtareas + franjas + plazos → plan | Voraz (MVP), Timefold (fase 2) |
| `scheduling` | Sync periódica, check-ins, regeneración | `@Scheduled` |
| `output` | ICS, PDF, correo, Recordatorios | ical4j, OpenHTMLtoPDF, Spring Mail |
| `web` | API REST para la UI | Spring Web |

**Flujo principal**

1. Login en navegador controlado; `auth` guarda la cookie cifrada.
2. `canvas` sincroniza asignaturas, módulos, páginas y entregas; el alumno elige qué planificar.
3. `extraction` procesa cada enunciado nuevo o modificado (hash del `body`).
4. `availability` calcula franjas libres de las próximas semanas.
5. `planning` asigna subtareas a franjas.
6. `output` publica ICS, Recordatorios y PDF.
7. `scheduling` repite 2–6 a diario y lanza los check-ins.

## 5. Integración con Canvas

**Autenticación:** Chromium visible con Playwright → el alumno inicia sesión → al recibir 200 en `/api/v1/users/self` se capturan las cookies de `aula.uoc.edu` → se guardan cifradas (AES-GCM) → un 401 marca la sesión como caducada y pide nuevo login.

Peticiones con cabecera `Cookie`, `Accept: application/json+canvas-string-ids`, y eliminando el prefijo `while(1);`.

| Propósito | Endpoint | Frecuencia |
| --- | --- | --- |
| Validar sesión | `GET /api/v1/users/self` | Antes de cada sync |
| Asignaturas | `GET /api/v1/courses?enrollment_state=active&per_page=100` | Diaria |
| Retos | `GET /api/v1/courses/{id}/modules?include[]=items&per_page=100` | Diaria |
| Página | `GET /api/v1/courses/{id}/pages/{page_url}` | Si cambia `updated_at` |
| Entregas | `GET /api/v1/courses/{id}/assignments?per_page=100` | Diaria |
| Rúbrica | `GET /api/v1/courses/{id}/assignments/{aid}` | Si cambia `updated_at` |
| Fichero | `GET /api/v1/courses/{id}/files/{fid}` | Bajo demanda |
| Calendario (sin sesión) | `GET /feeds/calendars/course_{uuid}.ics` | Opcional |

Paginación por cabecera `Link`. Resilience4j: 2 req/s, reintento con backoff en 429/5xx, circuit breaker.

**Normalización**

- `Course` ← curso; se excluyen cursos con `enrollment_term_id` distinto del actual (Tutoria = term 1).
- `Challenge` ← módulo que empieza por "Repte" (fallback: módulo con entrega con `due_at`).
- `Statement` ← página con `page_url` que empieza por `enunciat-de-lactivitat`; si no, clasifica la IA.
- `StudyGuide` ← página con `page_url` que empieza por `ges-`.
- `Deliverable` ← cada `Assignment` del módulo (`unlock_at`, `due_at`, `lock_at`, `points_possible`, `submission_types`, `omit_from_final_grade`).
- `Resource` ← enlaces clasificados en fichero Canvas, material UOC público o web externa.

**Robustez**

- Entrega resuelta por `content_id` del módulo, nunca por enlaces del HTML.
- Enlace a `/courses/{otro_id}/` → `stale_link`, no se sigue.
- Peso del reto = puntos de entregas que cuentan / total del curso.
- Re-extracción solo si cambia el SHA-256 del `body` normalizado.
- `submission_types: ["external_tool"]` → duración por defecto, sin extracción.

## 6. Extractor de enunciados con IA

Convierte el HTML de un enunciado en un `ChallengeSpec` JSON validado (`docs/challenge-spec.schema.json`): subtareas con cantidades y horas, formato, política de IA, criterios y dependencias. Nunca produce contenido de la entrega. Prompt de sistema en `backend/src/main/resources/prompts/extraction-system.md`.

**Pipeline**

1. Limpieza con Jsoup (sin estilos; conserva encabezados, listas, tablas, negritas, enlaces).
2. Contexto: planteamiento, GES, rúbrica y fechas de las entregas del módulo.
3. Llamada a Claude (SDK oficial de Java, `claude-opus-5-5`, esfuerzo `medium`) con salida estructurada. Sin temperatura: el modelo no la admite (ADR 0006).
4. Validación: JSON Schema + reglas (horas > 0, `deliverableRef` existente, total razonable).
5. Un reintento con los errores, en una petición nueva; si falla, o si el modelo se niega → `NEEDS_REVIEW` y edición manual.
6. Las correcciones del alumno prevalecen en futuras re-extracciones.

Enumerados: `tasks[].kind` = WORK, READING, QUIZ, FORUM_POST, FORUM_REPLY, VIDEO_RECORDING, REVIEW, SUBMISSION · `deliverables[].type` = DOCUMENT, QUIZ, CODE, VIDEO, FORUM, PROTOTYPE, OTHER · `aiPolicy.level` = FORBIDDEN, LIMITED, ALLOWED, NOT_STATED · `externalResources[].kind` = CANVAS_FILE, UOC_PUBLIC, EXTERNAL, STALE_LINK.

Coste: ~4.000 tokens de entrada por enunciado; ~30 llamadas por semestre. Modelo configurable.

## 7. Disponibilidad y check-ins

Horas libres = semana tipo − bloques ocupados (trabajo, desplazamientos, hobbies, descanso) ± excepciones de cada check-in. Ejemplo de configuración: `config/availability.example.yml`.

| Bloque del perfil | Datos |
| --- | --- |
| Trabajo | Días, horario, modalidad por día (remoto u oficina) |
| Desplazamiento | Minutos por trayecto en días de oficina |
| Actividades fijas | Nombre, días, franja |
| Sueño y comidas | Horas de despertar, acostarse, pausas |
| Preferencias | Franjas preferidas, sesión mín./máx., carga máxima diaria |
| Margen | Días de antelación por defecto y por asignatura |
| Correo | Dirección UOC y personal |

| Check-in | Preguntas |
| --- | --- |
| Cada lunes | Días de oficina, planes nuevos, actividades canceladas, horas reales de la semana anterior |
| Día 1 de cada mes | Vacaciones, viajes, picos de trabajo, cambios en el perfil |

Sin respuesta en 24 h se usa la semana tipo y el plan queda "sin confirmar". Modelo: `WeeklyTemplate` (TimeBlocks), `Override` (BUSY / FREE / OFFICE_DAY), `FreeSlot`. Las horas reales ajustan un factor de rendimiento por asignatura.

## 8. Motor de planificación

Asigna cada subtarea a franjas libres entre su apertura y su plazo efectivo, priorizando lo que vence antes y pesa más. MVP voraz; fase 2 con Timefold Solver detrás de la interfaz `Planner`.

| Plazo | Cálculo | Tipo |
| --- | --- | --- |
| Apertura | `unlock_at` (o hoy) | Duro |
| Recomendado | Semana de la GES o del título ("Setmana 4"); si no, reparto proporcional | Blando |
| Efectivo | `due_at` − margen (por defecto 1 día) | Duro |
| Real | `due_at` | Solo alertas |

- Duras: sin solapes; respetar apertura, plazo efectivo, `dependsOn`, `minDaysAfterDependency`, carga máxima diaria y tamaño de sesión.
- Blandas: acabar antes del plazo recomendado; franjas favoritas; máx. 2 asignaturas por día; hueco de revisión antes de cada entrega con nota; carga uniforme.

**Voraz:** franjas de 6 semanas → ordenar por plazo efectivo, peso, orden → primera franja válida (partiendo sesiones largas) → tarea REVIEW de 30–60 min antes de cada entrega con nota → lo que no cabe marca el reto `AT_RISK`, nunca se recorta en silencio.

**Casos especiales:** VIDEO_RECORDING sin hora (aviso en la ventana); QUIZ externos 45 min por defecto; FORUM_REPLY al menos N días tras la publicación propia; al replanificar se mantienen tareas hechas y las de los próximos 2 días.

## 9. Salidas

- **ICS:** `GET /calendar/{token}.ics` (token aleatorio de 32 bytes); calendarios `UOC · Entregas` y `UOC · Plan de estudio`; UID estables `{tipo}-{id}@uoc-study-planner`.
- **Recordatorios:** `reminders-bridge` (JSON por stdin/stdout); comandos `ensure-list`, `upsert-reminder`, `complete-reminder`, `delete-missing`, `request-access`; una lista por asignatura; el grupo "UOC" se crea a mano.
- **PDF:** Thymeleaf → OpenHTMLtoPDF; semanal (7 días, entregas, retos en riesgo, checklist de formato, política de IA) y mensual.
- **Correo:** Spring Mail por SMTP del alumno (contraseña de aplicación de Gmail); lunes y día 1; a la dirección UOC y a la personal.

## 10. Modelo de datos

PostgreSQL 16 + Flyway. Fechas en `timestamptz`; `jsonb` solo para spec, rúbrica y respuestas de check-in.

| Tabla | Columnas clave |
| --- | --- |
| `course` | `canvas_id` PK, `code`, `name`, `term_id`, `ics_url`, `selected`, `margin_days`, `start_at` (V5) |
| `challenge` | `id`, `course_id`, `canvas_module_id`, `position`, `name`, `weight_pct`, `status` (SYNCED, EXTRACTED, NEEDS_REVIEW, AT_RISK, DONE) |
| `canvas_page` | `course_id`, `page_url`, `title`, `role`, `body_html`, `body_hash`, `updated_at` |
| `deliverable` | `canvas_assignment_id` PK, `challenge_id`, `name`, `unlock_at`, `due_at`, `lock_at`, `points`, `counts_for_grade`, `submission_types`, `rubric_json` |
| `challenge_spec` | `challenge_id`, `version`, `spec_json` (nulo si falla), `errors` (V4), `model`, `source_hash`, `user_edited`, `created_at` |
| `task` | `id`, `challenge_id`, `spec_task_id`, `title`, `kind`, `estimated_hours`, `soft_deadline`, `hard_deadline`, `depends_on` |
| `study_session` | `id`, `task_id`, `starts_at`, `ends_at`, `plan_version`, `state` (PLANNED, DONE, SKIPPED) |
| `plan_version` | `id`, `created_at`, `trigger` (SYNC, CHECKIN, MANUAL), `unscheduled_hours` |
| `plan_notice` (V5) | `plan_version`, `task_id`, `type` (VIDEO_WINDOW, MARGIN_SKIPPED), `starts_at`, `ends_at`, `message` |
| `availability_block` | `id`, `day_of_week`, `start_time`, `end_time`, `type`, `label` |
| `availability_override` | `id`, `date`, `start_time`, `end_time`, `effect` (BUSY, FREE, OFFICE_DAY) |
| `checkin` | `id`, `kind` (WEEKLY, MONTHLY), `period_start`, `answers_json`, `answered_at` |
| `external_ref` | `entity_type`, `entity_id`, `system`, `external_id` |
| `user_settings` | clave/valor cifrado |
| `session_secret` | `id`, `cookie_blob` (cifrado), `captured_at`, `last_valid_at`, `expired_at` (V2) |

## 11. Stack y repositorio

Java 21 (Temurin), Spring Boot 3.x + Spring Modulith, WebClient + Resilience4j, Playwright for Java, SDK de Anthropic para Java, Jsoup, Timefold (fase 2), PostgreSQL 16 + JPA + Flyway, ical4j, Thymeleaf, OpenHTMLtoPDF, Spring Mail, Swift 6 + EventKit (macOS 14+), React 19 + TypeScript + Vite + Tailwind + TanStack Query, JUnit 5, AssertJ, Mockito, Testcontainers, WireMock, ArchUnit, Spotless, JaCoCo, GitHub Actions, Docker Compose. Versiones exactas: la última estable al crear el proyecto.

Estructura: `backend/` (paquetes por módulo en `edu.uoc.planner`), `frontend/`, `reminders-bridge/`, `docs/`, `scripts/`, `config/`.

## 12. Seguridad y riesgos

La cookie equivale a la contraseña del aula: nunca sale del equipo, se guarda cifrada y no aparece en logs, commits ni fixtures. Clave fuera de la BD (`PLANNER_SECRET_KEY`). Filtro de logs que enmascara cookies, cabeceras y correos. Servidor solo en `127.0.0.1`. Solo GET contra Canvas (test). A Claude solo se envían enunciado y rúbrica.

| Riesgo | Prob. | Impacto | Mitigación |
| --- | --- | --- | --- |
| La UOC bloquea la API con cookie | Baja | Alto | Uso moderado; pedir developer key OAuth2 |
| Cambio en SSO/MFA | Media | Medio | Login siempre interactivo |
| La cookie caduca a menudo | Media | Bajo | Detección de 401 y aviso |
| Estructura distinta en otras asignaturas | Alta | Medio | Heurísticas + IA + edición manual |
| Estimaciones irreales | Alta | Medio | Edición manual y factor de rendimiento |
| Percibido como ayuda para hacer trampa | Baja | Alto | ADR 0003, política de IA visible |
| Exposición del feed ICS | Media | Bajo | Token aleatorio, sin datos sensibles |

## 13. Testing y calidad

Ningún test toca `aula.uoc.edu` ni la API de Claude: WireMock sobre fixtures reales anonimizados (`backend/src/test/resources/fixtures/canvas/`) y respuestas grabadas del modelo.

- Unitarios: normalización, pesos, franjas, planificador, ICS.
- Integración Canvas: paginación, `while(1);`, 401, 429 (WireMock).
- BD: Testcontainers. Arquitectura: Spring Modulith `verify()` y ArchUnit (solo GET).
- Extractor: esquema y reglas sobre respuestas grabadas; perfil `live-ai` opcional.
- Fase 2: Timefold `ConstraintVerifier`; XCTest en el puente; Vitest en la UI.
- CI: `mvn verify`, JaCoCo ≥ 70 % en `canvas`, `availability` y `planning`, Spotless, conventional commits.

## 14. Roadmap

| Fase | Contenido | Puerta |
| --- | --- | --- |
| 0 · Base | Monorepo, Spring Boot + Modulith, Docker Compose + Flyway, CI, fixtures | G1: CI en verde y fixtures parseados |
| 1 · MVP | Cliente Canvas, normalización, login, extractor, disponibilidad, planificador voraz, ICS + PDF por correo | G2: plan real útil de 1 semana para las 6 asignaturas |
| 2 · Plan fino | Timefold, check-ins, Recordatorios (Swift), horas reales | G3: 4 semanas de uso sin tocar la BD |
| 3 · Abierto | UI React, instalación sencilla, developer key UOC, publicación | — |

Pasos detallados en `docs/kickoff.md`.

## 15. Preguntas abiertas

- [ ] ¿Rúbrica adjunta a `GET /api/v1/courses/92868/assignments/968028`?
- [ ] ¿`locked_for_user` en retos aún no abiertos?
- [ ] ¿Cuánto dura la cookie de sesión?
- [ ] ¿El patrón `Repte N` + `enunciat-de-lactivitat-rN` se cumple en las otras 4 asignaturas?
- [ ] ¿Pedir developer key OAuth2 a la UOC (fase 3)?
- [ ] ¿Cómo exponer el feed ICS al móvil fuera de casa?
- [ ] Nombre definitivo y licencia (MIT propuesta).
