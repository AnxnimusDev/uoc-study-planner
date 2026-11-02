# ADR 0010 · Si una cadena no cabe, se reserva primero la entrega evaluable

- Estado: aceptada
- Fecha: 2026-11-01

## Contexto

`GreedyPlanner` recorre las tareas en orden de dependencias. Cuando una tarea no cabe entera, pasa a
riesgo, y todas las que dependen de ella pasan también a riesgo con el motivo "Depende de X, que no
se ha podido planificar". La entrega que puntúa (QUIZ o SUBMISSION con nota) va al final de su
cadena, detrás de un repaso (`withReviews`) que depende de todo el trabajo previo. Si el estudio no
cabe, la entrega no se planifica nunca, aunque haya hueco para ella.

Ejecución real del 01/11/2026 a las 18:24 (148 sesiones, 59 tareas en riesgo):

| Reto | Vence | Falta | Hueco hasta el plazo efectivo | Entregas evaluables colocadas |
|---|---|---|---|---|
| FC Repte 2 (PAC2), reto 7 | mié 4/11 23:59 | ~31 h | lun y mar: 1 h 30 + 1 h 30 | ninguna: `7:t10` (QUIZ, 3 h) y `7:t11` (SUBMISSION) en riesgo por dependencia |
| English CA2, reto 15 | lun 2/11 23:59 | ~11 h | domingo desde las 18:24: 4 h | ninguna: `15:t12` (QUIZ), `15:t17` y `15:t18` (QUIZ, 1 h) en riesgo |

El plan usa las 3 h de FC en `7:t1` y `7:t2` (estudiar el módulo 3) y deja sin hueco el cuestionario
de la PAC2, que es lo único que cuenta para la nota. En English, las 4 h del domingo van a recursos y
foros, y el Listening (`15:t18`, sin dependencias de estudio) queda en riesgo porque su repaso no cabe.

## Decisión

Solo cuando el plan normal deja algo en riesgo (como ADR 0008), antes de las fases A y B:

1. **Qué se reserva.** Cada tarea `graded` de tipo `QUIZ` o `SUBMISSION` (`FINAL_KINDS`) que tenga
   `dueAt` dentro del horizonte, que no se haya colocado entera en el plan normal y que tenga en riesgo
   ella misma o algo de su cadena (sus dependencias, directas o no).
2. **Orden.** Por el orden de prioridad de siempre (`priority`): plazo objetivo, peso, reto y posición.
3. **Dónde.** Lo más tarde posible dentro de su ventana: desde su apertura (o ahora) hasta su plazo
   efectivo, sin tener en cuenta sus dependencias. Así, el tiempo anterior queda para el trabajo previo.
   Se aplican las reglas de siempre: sin solapes, con el tope del día y el máximo de asignaturas al día,
   y probando primero las franjas preferidas. Un `QUIZ` va en una sola sesión (ADR 0008).
4. **Margen.** Si no cabe antes del plazo efectivo, se reintenta hasta el plazo real (`dueAt`) y se
   añade un aviso `MARGIN_SKIPPED`: "No queda margen antes del plazo: se planifica hasta la hora de
   entrega." Si tampoco cabe así, pasa a riesgo como ahora.
5. **Después.** Las reservas se tratan como sesiones mantenidas en las fases A y B. Ocupan capacidad y
   se descuentan de la cuota de su asignatura en su semana. La entrega reservada cuenta como colocada y
   ya no está bloqueada por sus dependencias. El resto de la cadena se planifica en el orden de siempre,
   pero cada tarea solo hasta el inicio de la reserva: el estudio y el repaso van antes del cuestionario.
   Entre ellas, las dependencias siguen siendo duras: si `7:t1` no cabe, `7:t2` sigue en riesgo.
6. **Sin sobrecarga, nada cambia.** El plan normal sin riesgo es el resultado (ADR 0008, punto 1).

Con los datos de arriba:

- **FC.** `7:t10` (3 h seguidas) no cabe el lunes ni el martes, con un tope de 1 h 30. Por el punto 4,
  se reserva el miércoles de 18:30 a 21:30. El lunes y el martes siguen para `7:t1` y `7:t2`, y el resto
  de la cadena queda en riesgo.
- **English.** `15:t18` (1 h) y `15:t12` (30 min) se reservan el domingo. Quedan 2 h 30 para los
  recursos y los foros.

## Alternativas descartadas

- **Dependencias blandas en toda la cadena** (planificar cada tarea aunque falte la anterior): rompe una
  regla dura del diseño (`dependsOn`). Estudiar el Karnaugh antes del módulo 3 no tiene sentido; el
  cuestionario sí puede hacerse aunque falte estudio, y lo decide el alumno.
- **Reservar también el repaso**: con 3 h para 31 h, 1 h de repaso se come un tercio del estudio. El
  repaso se planifica en su orden si queda hueco antes de la reserva.
- **No usar nunca el margen (sin el punto 4)**: en el caso real, el cuestionario de FC seguiría en
  riesgo, porque el lunes y el martes no caben 3 h seguidas. El margen protege de imprevistos, pero
  perder la entrega es peor que hacerla el último día, y el aviso lo deja claro.
- **Colocar la entrega lo antes posible**: ocuparía el tiempo que necesita el trabajo previo y
  obligaría a hacer el cuestionario antes de estudiar.
- **Timefold (fase 2)**: sigue siendo la solución de fondo, por las mismas razones que en ADR 0008.

## Consecuencias

- En sobrecarga, cada entrega con nota que tenga hueco aparece en el plan, aunque falte parte del
  trabajo previo. Lo que falta sigue en "En riesgo".
- El cuestionario puede quedar el mismo día del plazo, sin margen, con el aviso `MARGIN_SKIPPED`.
- Planificar en sobrecarga cuesta una pasada más (reservas, A y B). Sigue siendo cuestión de
  milisegundos.
- `GreedyPlanner` gana una pasada de reservas (`reserveFinals`, `placeLast`), y `Capacity.findLast`
  busca el último hueco libre de N minutos antes de un instante. En `plan(...)` y en `shareOut(...)`,
  cada tarea de la cadena acaba como mucho 15 minutos (`Capacity.BREAK`) antes de la reserva.
- Una entrega que depende de otra entrega de su cadena (enviar la PAC después de su cuestionario)
  espera a la reserva de esa entrega; si esa no tiene hueco, tampoco se reserva.
- Tests nuevos en `GreedyPlannerTest`:
  - `reservesTheGradedQuizWhenItsChainDoesNotFit`;
  - `prerequisitesGoBeforeTheReservedQuiz`;
  - `usesTheMarginForAReservedQuizOnlyWhenNothingElseFits`;
  - `doesNotReserveASubmissionBeforeTheQuizItDependsOn`.

  Los tests sin sobrecarga deben pasar sin cambios.
- El diseño (sección 8, "Voraz") añade la reserva. La regla dura del plazo efectivo se matiza: solo una
  entrega reservada puede usar el margen, y siempre con aviso.
