# syntax=docker/dockerfile:1.7
# Imagen mínima de la API: multi-stage, dependencias fijadas por uv.lock, usuario sin root.

FROM python:3.14-slim-bookworm AS build
COPY --from=ghcr.io/astral-sh/uv:0.8.22 /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never
WORKDIR /app
COPY pyproject.toml uv.lock ./
COPY services/api/pyproject.toml services/api/pyproject.toml
RUN uv sync --frozen --no-dev --no-install-workspace --package civia-api
COPY services/api services/api
RUN uv sync --frozen --no-dev --package civia-api

FROM python:3.14-slim-bookworm AS runtime
RUN groupadd --system civia && useradd --system --gid civia --no-create-home civia
WORKDIR /app
COPY --from=build --chown=civia:civia /app /app
ENV PATH="/app/.venv/bin:$PATH" PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
USER civia
WORKDIR /app/services/api
EXPOSE 8000
ENV PORT=8000 FORWARDED_ALLOW_IPS=127.0.0.1
HEALTHCHECK --interval=15s --timeout=3s CMD python -c "import os, urllib.request; urllib.request.urlopen(f'http://127.0.0.1:{os.environ[\"PORT\"]}/api/v1/health')"
# 1) migraciones + rol civia_app (idempotente)  2) API. PORT lo fija la plataforma (Render, etc.).
# FORWARDED_ALLOW_IPS: solo las IP del proxy de la plataforma pueden fijar X-Forwarded-For.
CMD ["sh", "-c", "python -m civia_api.deploy && exec uvicorn civia_api.main:app --host 0.0.0.0 --port \"$PORT\" --proxy-headers --forwarded-allow-ips \"$FORWARDED_ALLOW_IPS\""]
