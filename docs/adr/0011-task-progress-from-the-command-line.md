# ADR 0011 · El alumno registra el avance de cada tarea desde la línea de comandos

- Estado: aceptada
- Fecha: 2026-11-02

## Contexto

El plan solo descuenta trabajo cuando se ha entregado en Canvas (#23). Todo lo demás se planifica
siempre con la estimación completa de la IA:

- `PlanStore.sessionsToKeep(now, KEEP_WINDOW)` solo devuelve sesiones que empiezan en `[now, now + 2
  días)`. Una sesión que ya ha pasado no cuenta para nada: el plan siguiente vuelve a pedir la tarea
  entera, se haya estudiado o no.
- `study_session.state` admite `DONE` y `SKIPPED`, pero nada lo escribe y solo cubre sesiones
  planificadas. El alumno también estudia fuera de ellas (el CA2 de English, el 02/11 durante el día).
- La sección 7 del diseño prevé check-ins sobre disponibilidad (oficina, planes, horas reales de la
  semana anterior), no sobre el avance de cada tarea.
- Las estimaciones de la IA suelen ser de 3 h por tarea (`7:t1`…`7:t9` de la PAC2 de FC: 3 h cada una).

Ejecución real del 02/11/2026: 44 tareas en riesgo y 5.505 minutos sin hueco. No se sabe qué parte es
trabajo que ya se ha hecho. Los ADR 0008, 0009 y 0010 ajustan el reparto en sobrecarga sobre estas
cifras.

## Decisión

### 1. Dos comandos

```bash
make done TASK=7:t1              # tarea hecha
make left TASK=7:t3 HOURS=1.5    # le quedan 1 h 30
```

- Igual que `make plan`, arrancan la aplicación con `--planner.command=progress` y los argumentos
  `--planner.task=<id>` y, en `left`, `--planner.hours=<h>`. Hasta la fase 3 no hay UI.
- `HOURS` admite decimales y se guarda redondeado a minutos. `left` con `HOURS=0` equivale a `done`.
- El id es el mismo que usa el plan (`PlanTask.id`): `<reto>:<tarea>` (`7:t1`), `quiz:<assignment>` o
  `review:<reto>:assignment:<id>`. El tiempo semanal (`weekly:…`, ADR 0007) se reparte cada semana, así
  que no admite avance: el comando lo rechaza con un mensaje.
- Si el id no está en el último plan, el comando falla con "No hay ninguna tarea <id> en el plan
  actual" y no guarda nada.
- Al terminar, el comando imprime "<id> · <título>: hecha" o "…: quedan 1 h 30". No replanifica: el
  siguiente `make plan` ya lo tiene en cuenta.

### 2. Tabla `task_progress` (V8)

```sql
CREATE TABLE task_progress (
  task_id           text PRIMARY KEY,           -- PlanTask.id
  challenge_id      bigint REFERENCES challenge(id),
  spec_version      integer,                    -- versión de challenge_spec al registrarlo
  remaining_minutes integer NOT NULL CHECK (remaining_minutes >= 0),
  reported_at       timestamptz NOT NULL
);
```

- Un registro por tarea. Un registro nuevo de la misma tarea sustituye al anterior.
- Pertenece al módulo `scheduling`, que es quien monta las tareas del plan (`PlanTaskAdapter`), y lo
  gestiona un `ProgressStore` en `scheduling.internal`.

### 3. Cómo lo usa el plan

`PlanRun` lee el avance y se lo pasa al planificador en `PlanRequest.progress` (minutos que quedan por
id). El planificador lo aplica antes de añadir los repasos, porque los repasos (`review:…`) los genera
él (`withReviews`) y el adaptador no los conoce:

- `remaining_minutes = 0`: la tarea no se planifica. Pasa lo mismo que con una entrega ya hecha en
  Canvas: cuenta como hecha para sus dependientes, y en `GreedyPlanner` sus dependencias se dan por
  cumplidas. Un repaso hecho no se añade.
- `remaining_minutes > 0`: la estimación de la tarea pasa a ser esa cifra.
- Un reto con todas sus tareas en 0 se trata como entregado en lo que toca al plan, pero no cambia el
  estado del reto en Canvas, que es de solo lectura.

### 4. Si cambia el enunciado

Los ids `<reto>:tN` los genera la extracción. Si un reto tiene una versión de `challenge_spec` más
nueva que la `spec_version` guardada, `t3` podría ser otra tarea:

- Su avance se descarta y se borra del almacén.
- `make plan` lo avisa: "Se ha vuelto a extraer el enunciado de <reto>: revisa el avance de sus
  tareas."

El avance de `quiz:…` y de `review:…` no depende de la extracción y se mantiene.

### 5. Salida de `make plan`

- Cada sesión de "Plan de los próximos 7 días" y cada tarea en riesgo muestran el id con el que se usa
  `TASK=`:

  ```
  22:58–23:58 · 05.562 · Estudiar el Mòdul 3: portes lògiques… [7:t1]
  · [7:t2] Estudiar funcions lògiques… (05.562, vence 04/11): faltan 150 min (Depende de 7:t1…)
  ```

- Las tareas en riesgo dejan de mostrar solo el id: muestran también título, asignatura y plazo.
- Los avisos iguales (mismo tipo y mismo mensaje) se muestran una vez, con el número de tareas: "Graba
  el vídeo dentro de la ventana… (8 tareas)".

## Alternativas descartadas

- **Marcar sesiones como `DONE` o `SKIPPED`**: solo cubre el tiempo planificado. Si una sesión de 1 h
  se hace en 2 h, o la tarea se hace otro día, no hay manera de decirlo. La tarea es lo que el alumno
  sabe cuantificar ("me queda una hora").
- **Contar como hechas las sesiones pasadas**: supone que se cumplió el plan, que es justo lo que no se
  sabe. Con planes que cambian cada día, daría por hecho trabajo que no se ha hecho.
- **Porcentaje en vez de horas**: con estimaciones poco fiables, "un 50 %" de una tarea de 3 h no dice
  cuánto falta. Las horas que quedan son lo que necesita el planificador.
- **Esperar a la UI (fase 3) o a los check-ins del diseño (fase 2)**: el plan seguiría con cifras
  infladas todo el semestre, que es lo que más lo estropea hoy.
- **Un fichero YAML de avance, como `config/availability.yml`**: los ids cambian al volver a extraer, y
  un fichero editado a mano no se puede invalidar como en el punto 4.

## Consecuencias

- **Módulo `scheduling`:**
  - `ProgressCommand` (comando `progress`) valida el id contra las tareas actuales
    (`PlanRun.currentTasks`); un `review:…` vale si su reto tiene esa entrega con nota;
  - `ProgressStore` y la migración `V8__task_progress.sql`;
  - `PlanRun` pasa el avance al planificador e invalida el de los enunciados re-extraídos.
- **Módulo `planning`:** `PlanRequest` añade `progress`, y `AtRiskTask` añade título, asignatura y
  plazo para la salida. `GreedyPlanner.withProgress` quita las tareas hechas y ajusta las demás antes
  de los repasos; no cambia nada más.
- **`Makefile`:** objetivos `done` y `left`, que `make help` lista.
- **Tests:**
  - `GreedyPlannerTest.Progress`: tarea hecha, tarea a medias y repaso hecho o a medias;
  - `ProgressCommandTest`: id desconocido, `weekly:` rechazado, horas no válidas, repaso y versión del
    enunciado;
  - `TaskProgressIntegrationTest` (Testcontainers): avance aplicado, registro sustituido y avance
    descartado al re-extraer;
  - salida de `PlanCommand`: ids, riesgo legible y avisos agrupados.
- **Diseño:**
  - sección 7: "Avance de tareas" con los dos comandos;
  - sección 10: tabla `task_progress`;
  - sección 8, "Casos especiales": una tarea con avance se planifica con lo que le queda.
- **El check-in semanal del diseño (fase 2) sigue pendiente.** Cuando exista, podrá preguntar por el
  avance y escribir en esta misma tabla.
