# reminders-bridge/

CLI en Swift que crea y actualiza recordatorios de Apple vía EventKit. Solo macOS 14+. Fase 2:
no empieces aquí hasta que el MVP (fase 1) funcione.

## Contrato

El backend lo invoca como proceso: JSON por stdin, JSON por stdout, código de salida 0 si va bien.

```json
{ "command": "upsert-reminder",
  "list": "UOC · Àlgebra",
  "externalId": null,
  "title": "[R2] Qüestionari 3 - Setmana 4",
  "notes": "Repte 2 · 45 min · Termini: 30/10",
  "dueDate": "2026-10-27T18:00:00+01:00",
  "priority": "medium" }
```

Comandos: `request-access`, `ensure-list`, `upsert-reminder`, `complete-reminder`, `delete-missing`.
Respuesta: `{ "ok": true, "externalId": "<calendarItemIdentifier>" }` o `{ "ok": false, "error": "..." }`.

## Límites de EventKit (no intentes rodearlos)

- No se pueden crear grupos de listas: el alumno crea el grupo "UOC" a mano.
- No hay subtareas: cada paso es un recordatorio independiente.
- La hora vive en `dueDateComponents` y en el `EKAlarm`: al mover una fecha, actualiza ambos.
- Construye las fechas por componentes, nunca parseando cadenas sin zona.
- Pide acceso con `requestFullAccessToReminders()` y documenta el permiso TCC en el README.

## Build y tests

`swift build -c release`, `swift test`. Los tests cubren la codificación JSON y el mapeo a modelos;
EventKit real solo se prueba a mano.
