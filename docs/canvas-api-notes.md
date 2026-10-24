# Notas sobre la API de Canvas en aula.uoc.edu

Validado el 18–19 de octubre de 2026 con la sesión de un alumno del Grau en Enginyeria Informàtica,
contra Àlgebra (curso 94063, cuestionarios) e Interacció persona ordinador (curso 92868, proyecto).
Los JSON reales anonimizados están en `backend/src/test/resources/fixtures/canvas/`.

## Autenticación

- No hay tokens personales para estudiantes (no aparece "+ Nuevo token de acceso" en Settings).
- La API responde con la cookie de sesión del navegador.
- Con cookie, las respuestas JSON pueden empezar por `while(1);`. Quitar antes de parsear.

## Endpoints usados

| Endpoint | Para qué | Fixture |
|---|---|---|
| `GET /api/v1/users/self` | Validar sesión | `users-self.json` |
| `GET /api/v1/courses?enrollment_state=active&per_page=100` | Asignaturas | `courses.json` |
| `GET /api/v1/courses/{id}/tabs` | Secciones (Pla docent es LTI) | `algebra-tabs.json` |
| `GET /api/v1/courses/{id}/modules?include[]=items&per_page=100` | Retos y sus elementos | `algebra-modules.json`, `ipo-modules.json`, `lab-fc-modules.json`, `lab-proto-modules.json`, `english-modules.json` |
| `GET /api/v1/courses/{id}/pages/{page_url}` | Enunciado y guías | `algebra-page-enunciat-r1.json`, `ipo-page-enunciat-r1.json` |
| `GET /api/v1/courses/{id}/assignments?per_page=100` | Entregas, fechas, puntos | `algebra-assignments.json`, `lab-fc-assignments.json` y `lab-proto-assignments.json` (vacíos) |
| `GET /api/v1/courses/{id}/assignment_groups` | Grupos y pesos | `algebra-assignment-groups.json` |
| `GET /api/v1/courses/{id}/assignments/{aid}` | Rúbrica (`rubric`) | pendiente de capturar |
| `GET /feeds/calendars/course_{uuid}.ics` | Calendario, **sin sesión** | — |

## Cursos (semestre 2026-27/1, `enrollment_term_id` = 220)

| id | Asignatura | Notas |
|---|---|---|
| 94063 | 05.557 Àlgebra | Cuestionarios Wiris + entrega final R5 (40 %) + vídeos de autoría |
| 94782 | 05.562 Fonaments de computadors | 4 retos; entregas PAC1, PAC3 y Pràctica (sync real, 23/10/2026) |
| 92868 | 05.590 Interacció persona ordinador | 4 retos con entrega PDF |
| 90696 | 05.658 Laboratori Fonaments de computadors | Sin entregas: foros y herramientas (VerilUOC, PyPAC) |
| 89934 | 20.043 Laboratori de prototipat | Sin entregas: 3 fases con 7 bloques de páginas (Figma) |
| 95209 | 20.439 English B2.2 | Retos = módulos `CA1`…`CA5`; enunciado `caN-…-guidelines`; locale `en-GB` |
| 22184 | Tutoria Grau en Enginyeria Informàtica | `enrollment_term_id` = 1, excluir |

## Estructura de un reto (módulo)

Àlgebra, Repte 1 (`module_id` 565757):

| Elemento | `type` | Clave |
|---|---|---|
| Plantejament i competències R1 | Page | `page_url: plantejament-i-competencies-r1` |
| GES. Guies d'estudi setmanal R1 | Page | `ges-guies-destudi-setmanal-r1` |
| Recursos d'aprenentatge R1 | Page | `recursos-daprenentatge-r1` |
| Enunciat de l'activitat R1 | Page | `enunciat-de-lactivitat-r1` |
| QÜESTIONARI 1 - Setmana 2 | Assignment | `content_id: 979687` |
| Enregistraments R1 | Assignment | `content_id: 979674` |
| Fòrum dubtes R1 | Discussion | — |

IPO, Repte 1 (`module_id` 557028): mismo patrón pero sin GES, con una página extra de estudio de caso
y un foro evaluable ("Validació de perfil amb IA"). El título de la página de enunciado cambia
("Repte 1. Investigació") pero el `page_url` sigue siendo `enunciat-de-lactivitat-r1`.

## Otras estructuras (fixtures del 23/10/2026)

- **English B2.2**: no hay módulos "Repte". Cada reto es un módulo `CAn <tema>` con una página
  `caN-…-guidelines` (enunciado), `caN-learning-resources`, páginas `…-instructions` por actividad,
  cuestionarios ("Language quiz"), entregas ("submission"), foros y la herramienta externa Tandem.
  Los módulos con entregas con fecha se detectan con la regla alternativa del normalizador.
- **Laboratoris** (Fonaments, Prototipat): `assignments` vacío. Fonaments solo tiene foros de dudas y
  herramientas; sus PAC se entregan en el aula de Fonaments de computadors. Prototipat es material de
  autoestudio (bloques de Figma) sin fechas. No hay nada que planificar desde Canvas.

## Entregas (Àlgebra)

- Campos útiles: `unlock_at`, `due_at`, `lock_at`, `points_possible`, `omit_from_final_grade`,
  `submission_types`, `grading_type`.
- Puntos que cuentan: Q1 15 · Q2–Q5 3,75 cada uno · Q6–Q7 7,5 · Q8–Q11 3,75 · R5 40. Total 100.
- Peso por reto: R1 15 % · R2 15 % · R3 15 % · R4 15 % · R5 40 %.
- Q2–Q5 comparten `due_at` (2026-10-30T22:59:59Z) aunque cada uno se recomienda en una semana distinta.
- `Enregistraments Rn`: `omit_from_final_grade: true`, ventana `unlock_at`–`lock_at` de unos 5 días,
  solo si el profesorado convoca, 15 min para subir, máximo 5 min de vídeo. Suspenderlo suspende el reto.
- Cuestionarios: `submission_types: ["external_tool"]` (Wiris). `description` vacía o `null`.
- Muchas entregas tienen `description: null`: el enunciado vive en la página, no en la entrega.
- `assignment_groups`: "Activitats" (peso 0) y "AC" (peso 100). No sirve para pesos por reto.

## Trampas conocidas

- Enlaces en el HTML a cursos de semestres anteriores: `78734` (fòrum de vídeos de Àlgebra),
  `87921` (entrega y rúbrica de IPO). No seguirlos; resolver por `content_id` del módulo.
- Algunos enlaces usan `$CANVAS_OBJECT_REFERENCE$` sin resolver.
- El HTML de IPO trae estilos en línea muy pesados: limpiar con Jsoup antes de mandarlo a la IA.
- `materials.campus.uoc.edu` y `design-toolkit.recursos.uoc.edu` son públicos (cargan sin sesión).

## Pendiente de verificar

- Rúbrica adjunta a `GET /api/v1/courses/92868/assignments/968028`.
- `locked_for_user` en páginas de retos aún no abiertos.
- Duración de la cookie de sesión.
- Si los laboratorios tienen actividades evaluables fuera de Canvas.
