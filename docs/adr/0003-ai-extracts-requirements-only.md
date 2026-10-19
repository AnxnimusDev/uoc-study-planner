# ADR 0003 · La IA solo extrae requisitos, nunca genera contenido de entregas

- Estado: aceptada
- Fecha: 2026-10-19

## Contexto

Los enunciados de la UOC fijan políticas de IA distintas por asignatura. Àlgebra prohíbe la IA en la
actividad; Interacció persona ordinador solo permite usos concretos y obliga a citarlos. Una
herramienta que ayudara a resolver retos facilitaría conductas irregulares.

## Decisión

- El extractor convierte el enunciado en un `ChallengeSpec` (subtareas, cantidades, formato, rúbrica,
  fechas, política de IA). Nunca propone soluciones, ejemplos de respuesta ni redacción.
- El prompt de sistema lo prohíbe explícitamente y la validación rechaza campos no previstos en el
  esquema (`additionalProperties: false`).
- La política de IA de cada reto (`aiPolicy`) se muestra en el plan, en el PDF y en las notas de los
  recordatorios, con la frase literal del enunciado como evidencia.
- A la API de Claude solo se envía el contenido del enunciado, del planteamiento, de la guía GES y de la
  rúbrica. Nunca foros, nombres ni notas.

## Consecuencias

- La herramienta es defendible ante la universidad: planifica, no hace trabajos.
- Cualquier petición futura de "ayúdame a resolver X" queda fuera de alcance del proyecto.
