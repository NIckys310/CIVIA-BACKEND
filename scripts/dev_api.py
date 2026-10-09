"""Levanta la API en local SIN Docker: Postgres 16 + pgvector embebido (pgserver).

Uso (desde la raíz del repo):  .venv/Scripts/python scripts/dev_api.py
Los datos persisten en .devdb/ (ignorado por git). Con Docker instalado, prefiere
`docker compose -f infra/docker-compose.yml --env-file .env up -d`.
"""

import os
import secrets
import sys
from pathlib import Path

import pgserver
import uvicorn
from alembic import command
from alembic.config import Config

ROOT = Path(__file__).resolve().parent.parent
API_DIR = ROOT / "services" / "api"
DATA_DIR = ROOT / ".devdb"
PASSWORD_FILE = DATA_DIR / "app_password.txt"


def main() -> None:
    DATA_DIR.mkdir(exist_ok=True)
    server = pgserver.get_server(DATA_DIR / "pg", cleanup_mode="stop")
    admin_url = server.get_uri().replace("postgresql://", "postgresql+asyncpg://")

    cfg = Config(str(API_DIR / "alembic.ini"))
    cfg.attributes["url"] = admin_url
    command.upgrade(cfg, "head")

    if not PASSWORD_FILE.exists():
        PASSWORD_FILE.write_text(secrets.token_urlsafe(24))
    app_password = PASSWORD_FILE.read_text().strip()
    server.psql(f"ALTER ROLE civia_app LOGIN PASSWORD '{app_password}';")

    os.environ.setdefault("ENVIRONMENT", "development")
    os.environ["DATABASE_URL"] = admin_url.replace("postgres:@", f"civia_app:{app_password}@")
    os.environ["MIGRATIONS_DATABASE_URL"] = admin_url
    os.environ.setdefault(
        "CORS_ORIGINS", '["http://localhost:3000","http://localhost:8081","http://127.0.0.1:3000"]'
    )
    sys.path.insert(0, str(API_DIR / "src"))
    port = int(os.environ.get("CIVIA_API_PORT", "8000"))
    print(f"Postgres embebido listo. API en http://localhost:{port}/api/docs")
    uvicorn.run("civia_api.main:app", host="127.0.0.1", port=port, reload=False)


if __name__ == "__main__":
    main()
