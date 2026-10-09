# 0003 — Arq + Redis para tareas en segundo plano

- Estado: aceptado
- Fecha: 2026-10-08

## Contexto
El análisis de planos (OCR, visión, informes) es largo y debe ejecutarse fuera de la petición HTTP.

## Decisión
Usar **Arq** (asyncio nativo) sobre Redis en lugar de Celery: la API ya es async (FastAPI +
SQLAlchemy async), Arq comparte el mismo modelo, tiene menos piezas y reintentos/timeout
por tarea. El progreso se publicará por SSE.

## Consecuencias
- Si en el futuro se necesitan flujos complejos (canvas, chords) se reevaluará Celery.
- El worker se implementa en la Fase 1 (`services/worker`).
