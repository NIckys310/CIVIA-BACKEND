"""Fixtures: PostgreSQL 16 + pgvector real (embebido con pgserver, sin Docker).

Las migraciones se aplican como superusuario y la API se conecta como `civia_app`,
igual que en producción, para que las pruebas ejerzan las políticas RLS reales.
"""

import os
import tempfile
from collections.abc import AsyncIterator, Iterator

import pgserver
import pytest
from alembic import command
from alembic.config import Config
from httpx import ASGITransport, AsyncClient

APP_PASSWORD = "test-app-password"  # solo existe en la BD efímera de pruebas
API_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


@pytest.fixture(scope="session")
def database_urls() -> Iterator[tuple[str, str]]:
    data_dir = tempfile.mkdtemp(prefix="civia-pg-")
    server = pgserver.get_server(data_dir, cleanup_mode="delete")
    admin_url = server.get_uri().replace("postgresql://", "postgresql+asyncpg://")

    cfg = Config(os.path.join(API_DIR, "alembic.ini"))
    cfg.attributes["url"] = admin_url
    command.upgrade(cfg, "head")
    server.psql(f"ALTER ROLE civia_app LOGIN PASSWORD '{APP_PASSWORD}';")

    app_url = admin_url.replace("postgres:@", f"civia_app:{APP_PASSWORD}@")
    yield admin_url, app_url
    server.cleanup()


@pytest.fixture(scope="session", autouse=True)
def _settings_env(database_urls: tuple[str, str]) -> Iterator[None]:
    admin_url, app_url = database_urls
    os.environ.update(
        {
            "ENVIRONMENT": "test",
            "DATABASE_URL": app_url,
            "MIGRATIONS_DATABASE_URL": admin_url,
            "HIBP_CHECK_ENABLED": "false",
        }
    )
    from civia_api.config import get_settings
    from civia_api.db import session

    get_settings.cache_clear()
    session.get_engine.cache_clear()
    session.get_sessionmaker.cache_clear()
    yield


@pytest.fixture
async def client() -> AsyncIterator[AsyncClient]:
    from civia_api.main import create_app

    transport = ASGITransport(app=create_app())
    async with AsyncClient(transport=transport, base_url="http://testserver") as c:
        yield c


@pytest.fixture(autouse=True)
def _fresh_rate_limiter() -> Iterator[None]:
    """Cada test empieza con contadores limpios (todos los logins vienen de la misma IP)."""
    from civia_api.security.ratelimit import get_rate_limiter

    get_rate_limiter.cache_clear()
    yield
