# ADR 0002 · Autenticación por cookie de sesión tras login interactivo

- Estado: aceptada
- Fecha: 2026-10-19

## Contexto

La UOC no permite a los estudiantes generar tokens de API en Canvas (no hay botón en Settings,
comprobado el 18/10/2026). Sin embargo, la API REST responde con la cookie de sesión del navegador.

## Decisión

1. `auth` abre Chromium visible con Playwright en `https://aula.uoc.edu/`.
2. El alumno inicia sesión él mismo en la web real de la UOC (SSO y MFA si los hubiera).
3. Cuando `/api/v1/users/self` devuelve 200, se capturan las cookies de `aula.uoc.edu` y se cierra
   el navegador.
4. Las cookies se guardan cifradas con AES-GCM; la clave vive fuera de la base de datos
   (`PLANNER_SECRET_KEY` o llavero del sistema).
5. Un 401 marca la sesión como caducada y pide nuevo login. Nunca se reintenta con credenciales.

La herramienta nunca ve ni almacena la contraseña.

## Alternativas descartadas

- Guardar usuario y contraseña y automatizar el formulario: viola la normativa y se rompe con MFA.
- Extensión de navegador: más limpia, pero saca el núcleo de Java. Posible en el futuro.
- Token de API: no disponible para estudiantes.

## Consecuencias

- El alumno tendrá que repetir el login cuando caduque la sesión (frecuencia por medir).
- La cookie da acceso completo al aula del alumno: nunca aparece en logs, commits ni fixtures.
