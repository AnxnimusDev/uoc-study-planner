---
description: Implementa una tarea del roadmap siguiendo el flujo leer → planificar → test → implementar → verificar
argument-hint: <descripción de la tarea o paso de docs/kickoff.md>
---

Tarea: $ARGUMENTS

Sigue estos pasos en orden y no te saltes ninguno:

1. **Contexto.** Lee `CLAUDE.md`, el `CLAUDE.md` de la carpeta afectada y la sección de
   `docs/design.md` que aplica. Si toca Canvas, revisa también `docs/canvas-api-notes.md` y los
   fixtures relevantes.
2. **Plan.** Escribe en 3–7 líneas qué vas a crear o cambiar, en qué módulo y con qué tests.
   Si la tarea contradice el diseño o un ADR, para aquí y pregunta.
3. **Tests primero.** Escribe los tests que describen el comportamiento esperado y comprueba que
   fallan por la razón correcta.
4. **Implementación mínima** para que pasen. Respeta los límites de módulo (nada de `internal` ajeno).
5. **Verificación.** Ejecuta `./mvnw spotless:apply` y `./mvnw verify` en `backend/`. Si falla,
   corrige y repite.
6. **Cierre.** Resume en pocas líneas qué cambió, qué tests cubren el cambio y qué queda pendiente.
   Propón el mensaje de commit (conventional commits). No hagas commit sin que te lo pida.
