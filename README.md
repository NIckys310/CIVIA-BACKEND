# CIVIA — Backend

API de **CIVIA AI**, el copiloto digital del ingeniero civil: FastAPI + PostgreSQL 16 (pgvector,
Row-Level Security) + Redis + almacenamiento S3.

```
IA → interpreta · Motor de ingeniería → calcula · Normativa → establece criterios · Ingeniero → valida
```

> Toda salida de CIVIA requiere revisión y aprobación del profesional responsable.

| Repositorio | Contenido |
|---|---|
| [CIVIA-FRONTEND](https://github.com/NIckys310/CIVIA-FRONTEND) | Web (Next.js, PWA) y app móvil (Expo) |
| **CIVIA-BACKEND** (este) | API, base de datos, infraestructura, motor de cálculo (Fase 2) |
| [CIVIA-IA](https://github.com/NIckys310/CIVIA-IA) | Visión, OCR, RAG con citas y orquestación del LLM |

## Qué incluye

- Autenticación: Argon2id, JWT EdDSA de 10 min, refresh rotativo con detección de reutilización.
- **Verificación en dos pasos (TOTP)** con códigos de recuperación y anti-reutilización.
- Bloqueo de cuenta, verificación de correo, recuperación/cambio de contraseña, avisos de
  dispositivo nuevo e historial de seguridad.
- Multi-tenant con **RLS** (rol `civia_app` sin privilegios), RBAC de 5 roles.
- Auditoría append-only con cadena de hashes SHA-256.
- Secretos de usuario cifrados en reposo (AES-256-GCM).

## Requisitos

- Python 3.12 vía [uv](https://docs.astral.sh/uv/) (`pip install uv`)
- Opcional: Docker Desktop. Sin Docker se usa PostgreSQL 16 + pgvector embebido.

## Puesta en marcha

```bash
uv sync --python 3.12
cp .env.example .env              # reemplaza los valores "change-me"
```

Sin Docker (Postgres embebido en `.devdb/`):

```bash
.venv/Scripts/python scripts/dev_api.py            # Windows; Linux/macOS: .venv/bin/python
```

Con Docker:

```bash
docker compose -f infra/docker-compose.yml --env-file .env up -d
```

Documentación interactiva: `http://localhost:8000/api/docs`.
Si el puerto 8000 está ocupado: `CIVIA_API_PORT=8010`.

## Calidad

```bash
cd services/api && ../../.venv/Scripts/python -m pytest      # Postgres real, RLS incluido
uv run ruff check services/api scripts && uv run mypy services/api/src
```

## Contrato con el frontend

`services/api/openapi.json` es el contrato publicado. Si cambias la API:

```bash
.venv/Scripts/python scripts/export_openapi.py
```

y en CIVIA-FRONTEND ejecuta `npm run contract:sync` para regenerar los tipos.

## Documentación

[Arquitectura](docs/architecture.md) · [Modelo de datos](docs/database/er.md) ·
[Modelo de amenazas](docs/security/threat-model.md) · [ADRs](docs/adr/) ·
[Cómo contribuir](CONTRIBUTING.md) · [Seguridad](SECURITY.md)
