# CIVIA AI

**El copiloto digital del ingeniero civil.** Planos + IA + normativa + biblioteca técnica +
motor de cálculo + medición con celular + visualización 2D/3D + reportes.

```
IA → interpreta
Motor de ingeniería → calcula (determinista, sin LLM)
Normativa → establece criterios
Ingeniero → valida
```

> Toda salida de CIVIA requiere revisión y aprobación del profesional responsable.

**Estado:** Fase 0 — Fundaciones ✔ (auth, RBAC, multi-tenant con RLS, auditoría, web, PWA y app móvil).

## Estructura

```
apps/web            Next.js 16 · PWA (escritorio, tableta y móvil)
apps/mobile         Expo SDK 57 · Android (APK/AAB) e iOS (IPA)
services/api        FastAPI · PostgreSQL 16 + pgvector · Alembic
packages/ui         Design tokens + iconos de ingeniería (web y móvil)
packages/shared-types  Tipos TS generados del OpenAPI
packages/api-client Cliente tipado con refresco de sesión
infra/              docker-compose (Postgres, Redis, MinIO, API)
docs/               Arquitectura, ER, seguridad, ADRs y guías
```

## Requisitos

- Node.js ≥ 22 (probado con 24) y npm
- Python 3.12 vía [uv](https://docs.astral.sh/uv/) (`pip install uv`)
- Opcional: Docker Desktop (Postgres/Redis/MinIO). Sin Docker se usa Postgres embebido.

## Puesta en marcha

```bash
npm install
uv sync --python 3.12
cp .env.example .env        # y reemplaza los valores "change-me"
```

**Opción A — sin Docker (Postgres 16 + pgvector embebido):**

```bash
.venv/Scripts/python scripts/dev_api.py      # Windows (Linux/macOS: .venv/bin/python)
npm run dev -w @civia/web                    # http://localhost:3000
```

Si el puerto 8000 está ocupado: `CIVIA_API_PORT=8010` para la API y
`NEXT_PUBLIC_API_URL=http://localhost:8010` para la web.

**Opción B — con Docker:**

```bash
docker compose -f infra/docker-compose.yml --env-file .env up -d
npm run dev -w @civia/web
```

**App móvil:** `npm run dev -w @civia/mobile` (Expo Go o development build).
Para generar APK/IPA ver [docs/guides/mobile-builds.md](docs/guides/mobile-builds.md).

## Calidad

```bash
cd services/api && ../../.venv/Scripts/python -m pytest      # API: Postgres real + RLS
npx turbo run typecheck test                                  # TypeScript + tests de paquetes
uv run ruff check services/api && uv run mypy services/api/src
```

Contrato: después de cambiar la API, `python scripts/export_openapi.py` y
`npm run generate -w @civia/shared-types` (CI falla si están desincronizados).

## Documentación

- [Arquitectura](docs/architecture.md) · [Modelo de datos](docs/database/er.md)
- [Modelo de amenazas](docs/security/threat-model.md) · [Política de seguridad](SECURITY.md)
- [Decisiones de arquitectura (ADR)](docs/adr/)

## Convenciones

Conventional Commits, commits pequeños y atómicos; `main` ← `develop` ← `feat/<módulo>-<desc>`.
Nunca se commitean secretos, `.env`, datasets protegidos ni normas con derechos restringidos.
