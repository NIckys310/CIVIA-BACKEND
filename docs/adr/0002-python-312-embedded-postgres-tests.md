# 0002 — Python 3.12 y tests contra PostgreSQL real embebido

- Estado: aceptado
- Fecha: 2026-10-08

## Contexto
Las garantías críticas (RLS, triggers de auditoría, pgvector) solo se pueden probar en
PostgreSQL de verdad. Docker no siempre está disponible en los equipos de desarrollo.

## Decisión
- Python **3.12** (versión del prompt) gestionado por uv.
- Los tests levantan **PostgreSQL 16 + pgvector embebido** con `pgserver` (Linux, macOS y
  Windows), aplican las migraciones como superusuario y conectan la API como `civia_app`,
  igual que en producción.
- `scripts/dev_api.py` usa el mismo mecanismo para desarrollo sin Docker.

## Consecuencias
- Sin SQLite ni mocks de base de datos: lo que pasa en CI pasa en producción.
- `pgserver` publica wheels hasta Python 3.12; subir de versión de Python requiere revisar
  esta dependencia (o usar Docker/testcontainers).
