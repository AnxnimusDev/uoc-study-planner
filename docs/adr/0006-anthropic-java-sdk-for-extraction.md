# ADR 0006 · El extractor llama a Claude con el SDK oficial de Java, sin temperatura

- Estado: aceptada
- Fecha: 2026-10-22

## Contexto

El diseño (sección 6) y `CLAUDE.md` fijaban Spring AI con Anthropic y temperatura 0 para el extractor.
Al implementarlo (fase 1, punto 4) hay dos problemas:

- **Claude Opus 5.5** (`claude-opus-5-5`, el modelo actual) rechaza con un 400 `temperature`, `top_p` y
  `top_k`, y no permite desactivar el razonamiento. "Temperatura 0" no se puede pedir.
- **Spring AI 1.x**, la línea compatible con Spring Boot 3.5, va por detrás de la API: envía `temperature`
  por defecto y no expone la salida estructurada (`output_config.format`) ni el esfuerzo
  (`output_config.effort`). Spring AI 2.x exige Spring Boot 4.

## Decisión

1. `extraction` usa el SDK oficial `com.anthropic:anthropic-java` **2.75.0**, detrás de la interfaz
   interna `SpecModel`. Se descarta la 3.0.0 (publicada el 2026-10-21) hasta que sus cambios
   incompatibles estén documentados.
2. Modelo por defecto `claude-opus-5-5` con esfuerzo `medium`, configurables en
   `planner.extraction.model` y `planner.extraction.effort`. Sin `temperature` ni `thinking`.
3. La consistencia sale de la **salida estructurada**: se manda el esquema de
   `docs/challenge-spec.schema.json` sin las restricciones que la API no admite (`minimum`, `maximum`,
   `minLength`, `maxLength`, `pattern`...), y la respuesta se valida en local contra el esquema
   completo y las reglas de negocio.
4. El reintento único es una petición nueva con el JSON inválido y sus errores en el mensaje del
   usuario. No se reenvía el turno anterior del asistente: el modelo no admite reescribir el
   historial sin sus bloques de razonamiento.
5. Se activa el fallback del lado del servidor ante negativas del modelo (`fallbacks: "default"`),
   desactivable con `planner.extraction.refusal-fallback=false`. Una negativa sin fallback deja el reto
   en `NEEDS_REVIEW`.

## Alternativas descartadas

- **Spring AI 1.x**: obligaría a quitar `temperature` a mano y a pedir el JSON por prompt, sin la
  garantía de esquema de la API.
- **Spring AI 2.x**: implica migrar todo el proyecto a Spring Boot 4.
- **HTTP a mano con `WebClient`**: hay un SDK oficial con tipos y errores tipados; reimplementarlo no
  aporta nada.

## Consecuencias

- Una dependencia más (`anthropic-java`, con OkHttp), pero solo la usa `extraction`.
- Coste estimado: ~4.000 tokens de entrada y ~2.000 de salida por enunciado, unas 30 llamadas por
  semestre, del orden de 2 € por semestre con Opus 5.5.
- Al cambiar de modelo hay que revisar esta decisión: los parámetros admitidos cambian entre modelos.
