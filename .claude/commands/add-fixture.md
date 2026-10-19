---
description: Añade una respuesta real de Canvas como fixture anonimizado
argument-hint: <nombre-del-fixture> (el JSON crudo debe estar en backend/src/test/resources/fixtures/raw/)
---

Fixture nuevo: $ARGUMENTS

1. Pídeme que guarde el JSON crudo de Canvas en
   `backend/src/test/resources/fixtures/raw/$ARGUMENTS.json` (esa carpeta está en `.gitignore` y no
   puedes leerla directamente).
2. Ejecuta `python3 scripts/anonymize_fixtures.py backend/src/test/resources/fixtures/raw/$ARGUMENTS.json
   backend/src/test/resources/fixtures/canvas/$ARGUMENTS.json`.
3. Lee el fixture anonimizado y confirma que no contiene nombres de personas, correos, avatares,
   `secure_params` ni URLs firmadas.
4. Añádelo a la tabla de `docs/canvas-api-notes.md` y al test parametrizado de fixtures.
5. Ejecuta `./mvnw verify` en `backend/`.
