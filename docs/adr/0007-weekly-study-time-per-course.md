# ADR 0007 · Tiempo semanal reservado por asignatura

- Estado: aceptada
- Fecha: 2026-10-24

## Contexto

El planificador solo crea tareas a partir de las entregas de Canvas (ADR 0003, diseño sección 8). Dos
asignaturas no tienen ninguna entrega en su aula (fixtures del 23/10/2026):

- **Laboratori de prototipat (20.043)**: 7 bloques de autoestudio de Figma, sin fechas. El
  profesorado indica en los anuncios qué hacer cada semana.
- **Laboratori de Fonaments (05.658)**: foros y herramientas; sus PAC se entregan en el aula de
  Fonaments de computadors.

Sin entregas, esas asignaturas nunca reciben tiempo en el plan, aunque el alumno quiera dedicarles un
rato cada semana para avanzar en los bloques y atender lo que se pida en los anuncios.

## Decisión

1. `config/availability.yml` admite un bloque opcional `weeklyStudy`:

   ```yaml
   weeklyStudy:
     - course: "20.043"   # código de la asignatura
       minutes: 60
       title: "Laboratori de prototipat: anuncios y bloques"   # opcional
   ```

2. `scheduling` añade, por cada entrada y cada semana del horizonte (6 semanas), una tarea:
   - id `weekly:<código>:<año>-W<semana>` (estable, para que el ICS actualice en lugar de duplicar);
   - tipo `READING`, duración `minutes`, sin repaso;
   - se abre el lunes a las 00:00 y vence el domingo a las 23:59 (hora del perfil), sin margen;
   - peso 0: va detrás de cualquier tarea con plazo antes, y respeta franjas preferidas, carga
     diaria y máximo de dos asignaturas por día como el resto.
3. Solo se generan para asignaturas seleccionadas del semestre actual. Un código desconocido se
   informa en `make plan`, no rompe el plan.
4. Si no cabe en su semana, se informa como "tiempo semanal sin hueco", **sin** marcar ningún reto
   `AT_RISK`: no hay reto ni nota en juego.
5. Modelo de datos (migración V6): `task.challenge_id` pasa a admitir nulo y se añade
   `task.course_id` (`REFERENCES course`), con `CHECK` para que una tarea tenga reto o asignatura.
   En el dominio de `planning`, `PlanTask.challengeId` es 0 para estas tareas.

## Alternativas descartadas

- **Actividad fija en `fixedActivities`**: ya funciona hoy, pero bloquea el tiempo como ocupado: no
  aparece en el plan, el ICS ni el PDF, y no respeta preferencias ni carga diaria.
- **Un reto ficticio por asignatura** en la tabla `challenge`: exige un `canvas_module_id` inventado
  y mezcla datos de Canvas con configuración del alumno.
- **Leer los anuncios de Canvas** para decidir qué hacer cada semana: posible más adelante (solo GET,
  contenido del profesorado), pero fuera de este cambio.

## Consecuencias

- Cualquier asignatura puede tener un rato fijo semanal, no solo las que no tienen entregas.
- `PlanStore` y las consultas de sesiones toman la asignatura de `task.course_id` cuando no hay reto.
- Las sesiones semanales aparecen en el calendario "UOC · Plan de estudio" y en el PDF.
