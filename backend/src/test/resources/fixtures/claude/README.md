# Respuestas de Claude para los tests del extractor

**Sintéticas.** Escritas a mano el 2026-10-22 a partir de los enunciados de `../canvas/`, porque aún no
había `ANTHROPIC_API_KEY` para grabar respuestas reales. Siguen el esquema
`docs/challenge-spec.schema.json` y solo contienen requisitos, nunca soluciones (ADR 0003).

Los tests las envuelven en una respuesta de la API de Mensajes (`content[].text`) servida por WireMock.
Cuando haya clave, `ChallengeExtractorLiveTest` (etiqueta `live`) valida una extracción real; sustituye
estos ficheros por su salida.

| Fichero | Contenido |
|---|---|
| `algebra-r1-spec.json` | `ChallengeSpec` válido de Àlgebra R1 (cuestionario Wiris + vídeo de autoría, IA prohibida) |
| `algebra-r1-spec-invalid.json` | El mismo con tres errores: propiedad no prevista, entrega inexistente y tarea de 5 h |
