# ADR 0004 · Monolito modular con Spring Modulith

- Estado: aceptada
- Fecha: 2026-10-19

## Contexto

La herramienta corre en el equipo de un solo alumno. Microservicios añadirían despliegue, red y
consistencia eventual sin ningún beneficio. Aun así, interesa mantener límites claros entre Canvas,
extracción, disponibilidad, planificación y salidas, para poder cambiar piezas (por ejemplo, el
planificador voraz por Timefold) sin efectos colaterales.

## Decisión

Un único proceso Spring Boot con paquetes por módulo (`auth`, `canvas`, `extraction`, `availability`,
`planning`, `scheduling`, `output`, `web`, `shared`). Spring Modulith verifica en un test que ningún
módulo accede a clases internas de otro. La comunicación entre módulos usa su API pública o eventos
de dominio (`ApplicationEventPublisher`).

## Consecuencias

- Un solo artefacto que arrancar y depurar.
- Los límites se comprueban en CI, no por disciplina.
- `Planner` es una interfaz en `planning`; el voraz y Timefold son implementaciones intercambiables.
