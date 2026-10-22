# backend/

Spring Boot 3 + Java 21. Monolito modular con Spring Modulith. Build con Maven Wrapper (`./mvnw`).

## Arranque del proyecto (fase 0)

Si `pom.xml` todavía no existe, créalo con:

- `spring-boot-starter-parent` (última 3.x estable), Java 21.
- Starters: `web`, `webflux` (solo para `WebClient`), `data-jpa`, `validation`, `mail`, `thymeleaf`, `actuator`.
- `spring-modulith-starter-core` y `spring-modulith-starter-test`.
- `org.postgresql:postgresql`, `org.flywaydb:flyway-core`, `flyway-database-postgresql`.
- `io.github.resilience4j:resilience4j-spring-boot3`.
- `org.jsoup:jsoup`.
- Tests: `spring-boot-starter-test`, `testcontainers` (`postgresql`, `junit-jupiter`), `wiremock-standalone`, `archunit-junit5`.
- Plugins: `spotless-maven-plugin` (google-java-format), `jacoco-maven-plugin`.

Añade más adelante, cuando el módulo los necesite (no antes): `com.microsoft.playwright:playwright` (auth),
`com.anthropic:anthropic-java` (extraction, ADR 0006), `org.mnode.ical4j:ical4j` y `com.openhtmltopdf` (output),
`ai.timefold.solver` (planning, fase 2).

Comprueba la última versión estable de cada dependencia antes de fijarla.

## Layout

```
src/main/java/edu/uoc/planner/
  PlannerApplication.java
  auth/          canvas/        extraction/    availability/
  planning/      scheduling/    output/        web/          shared/
src/main/resources/
  application.yml               perfiles: default, dev, test
  db/migration/                 V1__init.sql, V2__...
  prompts/                      extraction-system.md
  templates/                    weekly-plan.html (Thymeleaf → PDF)
src/test/resources/fixtures/canvas/   JSON reales anonimizados (ver README de la carpeta)
```

Dentro de cada módulo: clases públicas en la raíz del paquete (API del módulo); el resto en
`<modulo>.internal`. Spring Modulith falla el test de arquitectura si otro módulo accede a `internal`.

## Reglas del cliente de Canvas

- Un solo `CanvasClient` construido sobre `WebClient`. Base URL configurable (`planner.canvas.base-url`,
  por defecto `https://aula.uoc.edu`) para que WireMock la sustituya en tests.
- Solo expone métodos de lectura. El `ExchangeFilterFunction` rechaza cualquier método distinto de GET
  y hay un test que lo verifica.
- Cabeceras: `Cookie` (desde `auth`), `Accept: application/json+canvas-string-ids`.
- Quita el prefijo `while(1);` antes de deserializar.
- Sigue `Link: <...>; rel="next"` hasta agotar páginas; `per_page=100`.
- 401 → `CanvasSessionExpiredException` (pausa la sync, no reintenta).
- 429 y 5xx → reintento con backoff (Resilience4j). Rate limiter: 2 req/s.
- Deserializa a `record`s propios del módulo (`CanvasCourse`, `CanvasModule`, `CanvasModuleItem`,
  `CanvasPage`, `CanvasAssignment`), con `@JsonIgnoreProperties(ignoreUnknown = true)`.

## Persistencia

- Esquema en Flyway, nunca `ddl-auto` distinto de `validate`.
- Una migración por cambio; jamás edites una migración ya aplicada.
- Tests de repositorio con Testcontainers (`@DataJpaTest` + `@ServiceConnection`).

## Tests

- Unitarios sin Spring cuando la lógica es de dominio puro (planificador, franjas, pesos).
- `@SpringBootTest` solo para integración real entre módulos.
- Fixtures: carga con un helper `Fixtures.json("canvas/algebra-modules.json")`.
- Perfil `live-ai` desactivado por defecto: los tests que llaman a la API real de Claude llevan
  `@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")` y `@Tag("live")`, y se
  excluyen de `mvn verify`.
- Tests con Chromium real (Playwright contra WireMock) llevan `@Tag("browser")` y también quedan fuera de
  `mvn verify`. Se lanzan con `./mvnw test -Dgroups=browser -Dsurefire.excludedGroups=`.

## Configuración

`application.yml` lee de variables de entorno (ver `.env.example` en la raíz):
`PLANNER_DB_URL`, `PLANNER_DB_USER`, `PLANNER_DB_PASSWORD`, `PLANNER_SECRET_KEY`,
`ANTHROPIC_API_KEY`, `PLANNER_SMTP_*`. El servidor escucha solo en `127.0.0.1:8080`.
