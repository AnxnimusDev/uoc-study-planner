# ADR 0009 · La cuota semanal cuenta solo lo que hay que avanzar esa semana

- Estado: aceptada
- Fecha: 2026-10-27
- Modifica: ADR 0008 (fórmula de `pendiente(c, s)` en la fase A)

## Contexto

ADR 0008 reparte cada semana entre las asignaturas en proporción a sus minutos pendientes. El pendiente
de una asignatura suma todas sus tareas abiertas esa semana, venzan cuando venzan. Una asignatura con
mucho trabajo a largo plazo se lleva la semana aunque casi nada le venza en ella.

Ejecución real del 26/10/2026, ya con las entregas leídas de Canvas (#23) y sin sesiones de tareas
entregadas (#24):

| Asignatura | Plazo más cercano | Minutos de lunes a jueves |
|---|---|---|
| IPO (05.590) | 15/11 | 210 |
| FC (05.562) | 04/11 | 150 |
| English (20.439) | 02/11 | 120 |
| Àlgebra (05.557) | 30/10 | 87 |

El QÜESTIONARI 5 de Àlgebra vence el viernes 30/10 (plazo efectivo el jueves, con 1 día de margen).
Necesita unas 6,5 h: guía GES 6 (3 h), repaso (30 min) y el cuestionario entero (3 h, ADR 0008). Entre
el martes y el jueves hay hueco para ello, pero IPO, que vence dentro de casi tres semanas, ocupa el
martes y el miércoles. El cuestionario queda en riesgo con 49 tareas más.

## Decisión

En la fase A de ADR 0008, cada tarea cuenta en la cuota de la semana `s` solo la parte que habría que
hacer en `s` para llegar a su plazo a ritmo constante:

```
desde(t, s)     = max(inicio de s, ahora, apertura de t)
días(t, s)      = días entre desde(t, s) y min(fin de s, plazo efectivo de t)
hasta_plazo(t, s) = días entre desde(t, s) y el plazo efectivo de t
urgente(t, s)   = minutos sin colocar de t × min(1, días(t, s) / hasta_plazo(t, s))

pendiente(c, s) = Σ tareas de c abiertas en s: urgente(t, s) × (1 + peso del reto / 100)
```

1. Si una tarea vence dentro de `s`, `días = hasta_plazo` y cuenta entera, como hasta ahora.
2. Si vence más tarde, cuenta la fracción de la semana que le toca. Ejemplo: IPO a 18 días de su plazo
   y con 6 días de la semana por delante cuenta un tercio de lo que le falta.
3. Una tarea sin plazo (`dueAt` nulo) cuenta repartida hasta el final del horizonte (6 semanas).
4. Los días se cuentan en fracciones (horas / 24) en la zona del perfil, para que un plazo a mitad de
   día no redondee a 0 ni a 1.
5. **Si una tarea vence dentro de la semana en la que puede empezar y no cabe en su cuota**, se intenta
   colocar lo que le falta sin cuota, en los huecos libres hasta su plazo y con las reglas de siempre.
   Si así cabe entera, se coloca. Si ni así cabe, se queda con lo que le dio su cuota y el resto pasa a
   la fase B y, si no, a riesgo. Como las tareas se recorren por plazo, lo que vence después todavía no
   ha ocupado esos días.
6. El resto no cambia: cuota = capacidad(s) × pendiente(c, s) / Σ pendiente(·, s), con el mínimo de una
   sesión mínima; el reparto solo se usa si la pasada normal deja algo en riesgo; la fase B rellena lo
   que sobre por orden de plazo; el tiempo semanal de ADR 0007 entra con peso 0 y su plazo es el
   domingo, así que cuenta entero en su semana.

## Alternativas descartadas

- **Dar prioridad absoluta a lo que vence esa semana y repartir el resto**: vuelve al problema que
  originó ADR 0008. Una semana con una entrega que no cabe deja a las demás asignaturas sin nada. El
  punto 5 solo salta la cuota cuando eso basta para terminar la tarea.
- **Solo la fórmula, sin el punto 5**: probado en `GreedyPlannerTest`. Una tarea de 3 h que vence el
  jueves recibe unas 2,8 h de cuota, coloca 2,5 h en la fase A, y el jueves ya lo ocupa en la misma fase
  una tarea a 20 días. Queda en riesgo por 30 min.
- **Repartir por número de tareas o a partes iguales**: ignora que una tarea de 3 h y otra de 30 min no
  pesan lo mismo.
- **Ponderar por 1 / días hasta el plazo**: favorece lo urgente, pero sin una cifra que se pueda
  explicar ("lo que te toca hacer esta semana"). La fracción de la semana sí se entiende y da el valor
  exacto cuando el plazo cae dentro.
- **Timefold (fase 2)**: sigue siendo la solución de fondo, por las mismas razones que en ADR 0008.

## Consecuencias

- En la semana del 26/10, el pendiente de Àlgebra cuenta entero (unos 390 min). Los de IPO y FC
  cuentan solo una fracción. Si el QÜESTIONARI 5 cabe entero antes del jueves, se coloca (punto 5).
- Una tarea que vence esa semana y no cabe ni sin cuota sigue compartiendo la semana: en
  `sharesTheWeekWhenNotEverythingFits`, "soon" (12 h en 3 días) ocupa de lunes a miércoles y "later"
  recibe tiempo de jueves a domingo, sin quedar en riesgo.
- Las asignaturas con plazos lejanos reciben un poco cada semana en vez de mucho al principio. Lo que
  quede libre lo sigue rellenando la fase B.
- Sin sobrecarga nada cambia: el reparto no se activa (ADR 0008, punto 1).
- `GreedyPlanner.shareOut(...)` cambia el cálculo de `pending` y añade el punto 5. Test:
  `givesTheWeekToWhatIsDueInItBeforeFarAwayWork`.
- ADR 0008 pasa a indicar "Modificada por 0009" en el cálculo del pendiente. El diseño (sección 8)
  cambia "minutos pendientes" por "minutos que tocan esa semana".
