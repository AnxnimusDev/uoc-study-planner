# Fixtures de Canvas

Respuestas reales de `aula.uoc.edu` capturadas el 18–19 de octubre de 2026 y anonimizadas con
`scripts/anonymize_fixtures.py` (sin nombres, avatares, `secure_params` ni ID del alumno).

| Fichero | Endpoint | Curso |
|---|---|---|
| `users-self.json` | `/api/v1/users/self` | — |
| `courses.json` | `/api/v1/courses?enrollment_state=active` | 7 cursos (6 del term 220 + Tutoria) |
| `algebra-tabs.json` | `/api/v1/courses/94063/tabs` | Àlgebra |
| `algebra-modules.json` | `/api/v1/courses/94063/modules?include[]=items` | Àlgebra (5 retos) |
| `algebra-assignments.json` | `/api/v1/courses/94063/assignments` | Àlgebra (20 entregas) |
| `algebra-assignment-groups.json` | `/api/v1/courses/94063/assignment_groups` | Àlgebra |
| `algebra-page-enunciat-r1.json` | `/api/v1/courses/94063/pages/enunciat-de-lactivitat-r1` | Àlgebra R1 |
| `ipo-modules.json` | `/api/v1/courses/92868/modules?include[]=items` | IPO (4 retos) |
| `ipo-page-enunciat-r1.json` | `/api/v1/courses/92868/pages/enunciat-de-lactivitat-r1` | IPO R1 |

Valores útiles para aserciones:

- Àlgebra: pesos por reto 15/15/15/15/40; Q2–Q5 con `due_at` 2026-10-30T22:59:59Z.
- IPO R1: entrega `content_id` 968028; el HTML enlaza a la entrega del curso 87921 (enlace obsoleto).
- Àlgebra R1: el HTML enlaza al foro del curso 78734 (enlace obsoleto) y prohíbe la IA.

Para añadir uno nuevo: `/add-fixture <nombre>` en Claude Code.
