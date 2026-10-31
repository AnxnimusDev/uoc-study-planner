# ADR 0008 · Reparto proporcional cuando no cabe todo y cuestionarios en una sola sesión

- Estado: aceptada
- Fecha: 2026-10-25
- Modificada por: ADR 0009 (cálculo del pendiente semanal)

## Contexto

El planificador voraz (`planning.internal.GreedyPlanner`, diseño sección 8) recorre las tareas en
orden de plazo objetivo, peso y posición, y coloca cada una entera en la primera franja válida antes de
pasar a la siguiente. Cuando hay tiempo para todo funciona bien. Cuando no lo hay, la tarea que vence
antes se lleva todas las franjas y las que vencen después se quedan sin hueco, aunque pesen más.

Ejecución real del 25/10/2026, con 24,5 h de estudio a la semana (topes por día, #20):

- Antes del 4/11 vencen Àlgebra R2 (26 h estimadas), English CA2 (17,5 h) y Fonaments R2 (30,5 h):
  unas 74 h para unas 35 h libres.
- Àlgebra ocupa sola de domingo a jueves. Fonaments recibe 3,5 h de las 30,5 de su R2. IPO (05.590)
  no aparece en los próximos 7 días.
- 47 tareas en riesgo y 94 h sin hueco; casi todas de Fonaments (retos 7 y 8) e IPO (reto 11).

Hay un segundo problema: `place(...)` parte cualquier tarea en sesiones de 30 a 180 minutos. "Resoldre
el QÜESTIONARI 3" (QUIZ, 3 h) sale en cuatro trozos de 30 minutos entre el lunes y el miércoles.
Un cuestionario se hace de una sentada, y varios son cronometrados con un único intento ("timed, one
attempt", "un único intento"). En el plan actual las 15 tareas de cuestionario son `kind: QUIZ`.

## Decisión

### 1. Reparto proporcional solo cuando no cabe todo

1. `GreedyPlanner` planifica primero como ahora. Si no queda ninguna tarea en riesgo (`atRisk` vacío),
   ese plan es el resultado: sin sobrecarga, nada cambia.
2. Si queda alguna en riesgo, se vuelve a planificar en dos fases sobre una capacidad nueva:
   - **Fase A, con cuotas.** Por cada semana ISO del horizonte (de lunes a domingo, en la zona del
     perfil) y cada asignatura con trabajo abierto esa semana, se calcula una cuota de minutos:

     ```
     capacidad(s)         = Σ días de s: min(minutos libres del día, tope del día)
     pendiente(c, s)      = Σ minutos sin colocar de las tareas de c abiertas en s
                            × (1 + peso del reto / 100)
     cuota(c, s)          = capacidad(s) × pendiente(c, s) / Σ pendiente(·, s),
                            con un mínimo de una sesión mínima (minSessionMinutes)
     ```

     Una tarea está abierta en s si se abre antes de que acabe s y su plazo efectivo es posterior al
     inicio de s. Las tareas se recorren en el orden de siempre y cada una solo puede ocupar, en cada
     semana, lo que le quede a la cuota de su asignatura. Lo que no quepa se queda pendiente para la
     fase B.
   - **Fase B, sin cuotas.** Se recorren otra vez las tareas en el mismo orden y se coloca lo
     pendiente en el tiempo que haya sobrado, con las reglas actuales (tiers de franjas preferidas,
     máximo de dos asignaturas al día, margen).
3. El tiempo semanal de ADR 0007 entra en el reparto como una asignatura más, con peso 0: en
   sobrecarga recibe su cuota en vez de quedarse siempre al final.
4. Lo que siga sin caber después de la fase B pasa a riesgo, como ahora (`AT_RISK`, nunca se recorta en
   silencio).

### 2. Los cuestionarios no se parten

1. Las tareas `QUIZ` se colocan en **una sola sesión** con su duración completa. No se les aplica
   `maxSessionMinutes`; sí el tope del día, el margen y las franjas libres.
2. Si antes del plazo no hay ningún hueco seguido de esa duración, la tarea pasa a riesgo con el motivo
   "No hay N min seguidos antes del plazo".
3. Al replanificar, una sesión mantenida de un `QUIZ` (las de los próximos 2 días) solo se conserva si
   cubre la tarea entera. Los trozos de planes anteriores se descartan.
4. El repaso previo (`REVIEW`) sigue siendo una tarea aparte y se puede partir, como hasta ahora.

## Alternativas descartadas

- **Adelantar Timefold (fase 2)** para optimizar el reparto: resuelve esto y más, pero añade una
  dependencia y un modelo nuevo antes de cerrar el MVP. La interfaz `Planner` permite cambiarlo
  después sin tocar el resto.
- **Reparto proporcional siempre**, haya sobrecarga o no: retrasaría tareas que caben sin problema
  por plazo y cambiaría planes que hoy salen bien.
- **Cuotas por día en vez de por semana**: con topes de 1 h 30 los lunes y martes, una cuota diaria
  daría trozos más pequeños que la sesión mínima.
- **Un campo nuevo en `ChallengeSpec` para "intento único"**: todos los casos reales ya llegan como
  `QUIZ`, así que basta con el tipo. Cambiar el esquema obligaría a volver a extraer.

## Consecuencias

- En sobrecarga, el riesgo se reparte: Àlgebra R2 puede pasar a riesgo en parte para que Fonaments R2
  e IPO R2 avancen. El plan lo muestra igual que ahora, en "En riesgo".
- Sin sobrecarga, el plan sale exactamente igual que antes: los tests actuales de `GreedyPlannerTest`
  deben pasar sin cambios.
- Planificar en sobrecarga cuesta hasta tres pasadas (normal, A y B). Con unas 150 tareas y 6 semanas
  sigue siendo cuestión de milisegundos.
- Un `QUIZ` largo necesita un hueco seguido. Los lunes y martes (tope de 1 h 30) no caben cuestionarios
  de 3 h, así que irán a otros días.
- El diseño (sección 8) se actualiza: la regla "Voraz" añade la sobrecarga y "Casos especiales" añade
  los QUIZ enteros. ADR 0007 sigue vigente; su punto 2 ("peso 0: va detrás") solo cambia en sobrecarga.
