# frontend/

React 19 + TypeScript + Vite + Tailwind CSS + TanStack Query. Fase 3: no empieces aquí hasta que
el backend exponga la API REST del plan.

Pantallas previstas: cuestionario inicial de disponibilidad, selección de asignaturas, check-in
semanal y mensual, vista del plan (semana y mes), revisión y edición del `ChallengeSpec` de un reto.

Reglas:
- Solo habla con el backend local (`http://127.0.0.1:8080/api`). Nunca con Canvas directamente.
- Tipos de la API generados o escritos a mano en `src/api/types.ts`, alineados con los DTO del backend.
- Comandos: `npm run dev`, `npm run lint`, `npm run test` (Vitest), `npm run build`.
