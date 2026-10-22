# ADR 0005 · `canvas` define el puerto de sesión; `auth` lo implementa

- Estado: aceptada
- Fecha: 2026-10-21

## Contexto

`CanvasClient` necesita la cookie de `aula.uoc.edu` en cada petición, y `auth` es quien la captura y
la guarda cifrada en `session_secret` (ADR 0002). A la vez, solo `canvas` ve el 401 que indica que la
sesión ha caducado, y `auth` tiene que enterarse para marcarla y pedir un nuevo login.

La información va en los dos sentidos (cookie de `auth` a `canvas`, caducidad de `canvas` a `auth`),
pero Spring Modulith (ADR 0004) no admite ciclos entre módulos: una de las dos dependencias tiene que
ir por un puerto o por un evento.

## Decisión

1. `canvas` publica en la raíz de su paquete (API del módulo):
   - `CanvasSessionProvider`: interfaz con `Optional<String> currentCookie()`, que devuelve el valor de
     la cabecera `Cookie` ya descifrado, o vacío si no hay sesión.
   - `CanvasSessionExpiredEvent`: `record` con `Instant detectedAt`. No lleva la cookie ni ningún dato
     de la respuesta.
2. `CanvasClient` pide la cookie al proveedor antes de cada petición, no la cachea. Si viene vacía,
   lanza `CanvasSessionExpiredException` sin llegar a la red.
3. Al recibir un 401, `CanvasClient` publica `CanvasSessionExpiredEvent` con
   `ApplicationEventPublisher`, lanza `CanvasSessionExpiredException` y no reintenta.
4. `auth` implementa `CanvasSessionProvider` (lee `session_secret` y lo descifra) y escucha el evento
   con un `@EventListener` síncrono que rellena `expired_at` (migración V2).
   *Corrección (2026-10-22):* la versión inicial decía `@ApplicationModuleListener`, pero es un
   listener transaccional y `CanvasClient` publica fuera de una transacción, así que nunca se
   dispararía. El síncrono, además, deja la sesión marcada antes de que la sync reciba la excepción.
5. Dependencia permitida: `auth → canvas`. `canvas` no importa nada de `auth`, y
   `ApplicationModules.verify()` lo comprueba en CI.
6. Mientras `auth` no exista (hasta el punto 3 de la fase 1), los tests de `canvas` usan un proveedor
   falso, y en ejecución no hay ningún bean: el contexto no lo exige hasta que haya un consumidor real.

## Alternativas descartadas

- **`auth` expone la cookie y `canvas` depende de `auth`.** Obliga a avisar del 401 con un evento que
  escucha el propio `auth`, y `canvas` acaba ligado a un módulo que arrastra Playwright, el cifrado y
  la persistencia de la sesión. Con un puerto en `canvas`, el cliente se testea con WireMock y un
  proveedor falso, sin nada de `auth`.
- **El evento lo define `shared`.** Funciona, pero el evento solo tiene sentido para la API de Canvas;
  `shared` queda para tipos comunes de verdad (cifrado, reloj).

## Consecuencias

- `canvas` se puede desarrollar y probar antes que `auth`.
- Cambiar el mecanismo de sesión (por ejemplo, un token OAuth2 si la UOC concede una developer key)
  solo cambia la implementación de `auth`, no el cliente.
- La cookie descifrada solo existe en memoria durante la petición. El evento no la transporta, así
  que el registro de publicaciones de Modulith tampoco la guarda.
