"""Preparación de la base de datos al desplegar: `python -m civia_api.deploy`.

1. Aplica las migraciones de Alembic con el rol propietario (MIGRATIONS_DATABASE_URL).
2. Si APP_DB_PASSWORD está definido, habilita el inicio de sesión del rol `civia_app`
   (sin privilegios, sujeto a RLS) con esa contraseña. La API se conecta con ese rol.

Es idempotente: se puede ejecutar en cada despliegue.
"""

import asyncio
import logging
from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from civia_api.config import get_settings

log = logging.getLogger("civia_api.deploy")


def _alembic_ini() -> Path:
    # En el contenedor el directorio de trabajo es services/api; en desarrollo, el repo.
    for candidate in (
        Path.cwd() / "alembic.ini",
        Path(__file__).resolve().parents[2] / "alembic.ini",
    ):
        if candidate.is_file():
            return candidate
    raise SystemExit("No se encontró alembic.ini")


async def enable_app_role(owner_url: str, password: str) -> None:
    engine = create_async_engine(owner_url)
    try:
        async with engine.begin() as conn:
            # format(%L) escapa la contraseña como literal SQL (sin interpolación manual).
            statement = await conn.scalar(
                text("SELECT format('ALTER ROLE civia_app LOGIN PASSWORD %L', CAST(:pw AS text))"),
                {"pw": password},
            )
            await conn.execute(text(str(statement)))
    finally:
        await engine.dispose()


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
    settings = get_settings()
    if not settings.migrations_database_url:
        raise SystemExit("MIGRATIONS_DATABASE_URL es obligatorio para desplegar")

    log.info("Aplicando migraciones…")
    command.upgrade(Config(str(_alembic_ini())), "head")

    if settings.app_db_password is not None:
        log.info("Habilitando el rol civia_app…")
        asyncio.run(
            enable_app_role(
                settings.migrations_database_url, settings.app_db_password.get_secret_value()
            )
        )
    log.info("Base de datos lista")


if __name__ == "__main__":
    main()
