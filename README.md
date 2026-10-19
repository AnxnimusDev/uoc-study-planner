# UOC Study Planner

Planificador de estudio para alumnos de la UOC. Lee tu aula Canvas con tu propia sesión, extrae los
requisitos de cada reto y reparte el trabajo en tus horas libres reales. Publica el plan como
calendario (`.ics`), Recordatorios de Apple y PDF semanal por correo.

> Estado: fase 0 (en construcción). Proyecto personal, no oficial de la UOC.

## Principios

- **Local**: corre en tu equipo; no hay servidor compartido.
- **Solo lectura**: nunca envía ni modifica nada en el aula.
- **Sin contraseñas**: inicias sesión tú en la web de la UOC; la herramienta solo guarda la cookie, cifrada.
- **Planifica, no resuelve**: la IA extrae requisitos de los enunciados, nunca genera contenido de entregas.

## Requisitos

Java 21, Docker, Make. Node 20+ (UI, fase 3). macOS 14+ y Xcode CLT para Recordatorios (fase 2).

## Puesta en marcha

```bash
cp .env.example .env              # y rellena los valores
cp config/availability.example.yml config/availability.yml
make up                           # PostgreSQL
make test                         # tests
make login                        # inicia sesión en la UOC (ventana del navegador)
make sync                         # sincroniza tus asignaturas
```

## Documentación

- `docs/design.md` · documento de diseño
- `docs/adr/` · decisiones de arquitectura
- `docs/canvas-api-notes.md` · cómo se comporta la API de Canvas en aula.uoc.edu
- `docs/kickoff.md` · cómo arrancar el proyecto con Claude Code

## Licencia

MIT (pendiente de confirmar).
