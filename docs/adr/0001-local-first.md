# ADR 0001 · Ejecución local, sin servicio compartido

- Estado: aceptada
- Fecha: 2026-10-19

## Contexto

La herramienta necesita acceder al aula de la UOC con la identidad del alumno. La normativa de
convivencia de la UOC establece que la clave de acceso al Campus Virtual es personal e individual.
Un servicio web que recogiera credenciales o sesiones de varios alumnos los obligaría a cederlas a un
tercero y convertiría al proyecto en custodio de datos académicos ajenos.

## Decisión

Cada alumno ejecuta la herramienta en su propio equipo (Docker Compose + backend local). No existe
servidor compartido. El backend y la UI escuchan solo en `127.0.0.1`.

## Consecuencias

- Sin coste de infraestructura ni responsabilidad sobre datos de terceros.
- La distribución a otros alumnos es "instala esto", no "regístrate aquí".
- Suscribirse al feed ICS desde el móvil fuera de casa requiere exponer el endpoint (túnel); se
  resolverá en fase 3.
- Si la UOC concede algún día una developer key OAuth2, se podrá revisar esta decisión.
