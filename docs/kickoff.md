# Arranque con Claude Code

Guía para empezar el proyecto con Claude Code, paso a paso. Cada bloque es un prompt para pegar tal
cual en una sesión nueva o continuar en la misma. Revisa el resultado y haz commit entre pasos.

## Antes de empezar (manual, 10 minutos)

1. `docs/design.md` ya contiene el diseño (sin diagramas). La versión editable con diagramas está en Claude Docs.
2. `git init && git add . && git commit -m "chore: bootstrap docs and Claude Code config"`.
3. Crea el repositorio en GitHub (`AnxnimusDev/uoc-study-planner`) y haz push.
4. Comprueba que tienes Java 21, Maven, Docker y Node 20+ instalados.
5. Copia `.env.example` a `.env` y rellena `PLANNER_DB_PASSWORD` y `PLANNER_SECRET_KEY`
   (`openssl rand -base64 32`). `ANTHROPIC_API_KEY` no hace falta hasta la fase 1.

## Fase 0 · Base

### Paso 0.1 · Esqueleto del backend

```
Lee CLAUDE.md, backend/CLAUDE.md y docs/adr/0004-modular-monolith.md.
Crea el proyecto Maven en backend/ con Maven Wrapper, Java 21 y la última Spring Boot 3.x estable,
con las dependencias de "Arranque del proyecto (fase 0)" de backend/CLAUDE.md (solo esas).
Crea PlannerApplication y los 9 paquetes de módulo con un package-info.java cada uno
(@ApplicationModule donde aplique). Añade un test ModularityTest con
ApplicationModules.of(PlannerApplication.class).verify() y un test de contexto que arranque con
Testcontainers. Configura Spotless y JaCoCo. Al final ejecuta ./mvnw verify y dime el resultado.
```

### Paso 0.2 · Base de datos

```
Lee la sección 10 (Modelo de datos) de docs/design.md.
Crea docker-compose.yml en la raíz con PostgreSQL 16 (puerto 5432 solo en 127.0.0.1, volumen en
./data/postgres) y la migración Flyway V1__init.sql con las tablas course, challenge, canvas_page,
deliverable, challenge_spec, task, study_session, plan_version, availability_block,
availability_override, checkin, external_ref, user_settings y session_secret.
Usa timestamptz para fechas y jsonb solo donde dice el diseño. application.yml con perfiles default,
dev y test leyendo de variables de entorno. Test de migraciones con Testcontainers. ./mvnw verify.
```

### Paso 0.3 · Fixtures y su test

```
Los fixtures anonimizados ya están en backend/src/test/resources/fixtures/canvas/.
Crea un helper de test Fixtures.json(path) y un test parametrizado que deserialice cada fixture
en los records del módulo canvas (créalos en canvas.internal.api: CanvasCourse, CanvasModule,
CanvasModuleItem, CanvasPage, CanvasAssignment, CanvasAssignmentGroup, CanvasTab) con
@JsonIgnoreProperties(ignoreUnknown = true). El test debe fallar si un campo obligatorio
(id, name/title, type, due_at cuando exista) llega a null. ./mvnw verify.
```

### Paso 0.4 · CI

```
Crea .github/workflows/ci.yml: en push y pull_request, Java 21 (Temurin), caché de Maven,
./mvnw -B verify en backend/. Sube el informe de JaCoCo como artefacto. Sin pasos de despliegue.
```

**Puerta G1:** CI en verde y el test de fixtures pasando.

## Fase 1 · MVP (resumen; un prompt por punto)

1. **canvas · cliente.** `CanvasClient` sobre WebClient: solo GET (con test que lo verifica), prefijo
   `while(1);`, paginación `Link`, 401 → `CanvasSessionExpiredException`, 429/5xx con Resilience4j.
   Tests con WireMock sirviendo los fixtures.
2. **canvas · normalización.** De `CanvasModule` a `Challenge`, `Statement`, `StudyGuide`,
   `Deliverable` según las reglas de la sección 5 del diseño. Cálculo de pesos. Tests con los
   fixtures de Àlgebra (pesos 15/15/15/15/40) e IPO.
3. **auth.** Login con Playwright (`make login`), captura de cookies, cifrado AES-GCM, persistencia
   en `session_secret`. Comando de comprobación `make sync` que lista asignaturas.
4. **extraction.** Limpieza con Jsoup, prompt en `prompts/extraction-system.md`, salida validada
   contra `docs/challenge-spec.schema.json`, reintento único, estado `NEEDS_REVIEW`. Tests con
   respuestas grabadas; test `live` opcional.
5. **availability.** Lectura de `config/availability.yml` y cálculo de `FreeSlot` con overrides.
6. **planning.** Interfaz `Planner` + implementación voraz (sección 8). Tests de dominio exhaustivos.
7. **output.** Feed ICS (dos calendarios, UID estables) y PDF semanal con envío por correo.

**Puerta G2:** un plan generado desde tu aula real cubre la semana siguiente para tus 6
asignaturas, sin solapes ni plazos incumplidos.

## Consejos para trabajar con Claude Code en este repo

- Empieza cada sesión con: "Lee CLAUDE.md y dime en qué fase estamos y cuál es el siguiente paso".
- Usa `/implement-task` con la descripción del paso para que siga el flujo leer → planificar →
  test → implementar → verificar.
- Pide un plan antes de tareas grandes (modo plan) y corrígelo antes de que escriba código.
- Si cambia una decisión, `/new-adr` antes de tocar código.
- Captura respuestas nuevas de Canvas con `/add-fixture` en lugar de inventar JSON.
