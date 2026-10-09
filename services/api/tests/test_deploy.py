"""El paso de despliegue habilita el rol civia_app con una contraseña arbitraria (escapada)."""

from urllib.parse import urlsplit

import asyncpg

from civia_api.deploy import enable_app_role


async def test_enable_app_role_sets_escaped_password(database_urls: tuple[str, str]) -> None:
    admin_url, app_url = database_urls
    original = urlsplit(app_url).password or ""
    tricky = "p'w\"; DROP ROLE civia_app; --"
    try:
        await enable_app_role(admin_url, tricky)
        # La URL completa conserva el host (TCP en Windows, socket Unix en Linux); la
        # contraseña explícita reemplaza a la que trae la URL.
        dsn = app_url.replace("postgresql+asyncpg://", "postgresql://")
        conn = await asyncpg.connect(dsn, password=tricky)
        assert await conn.fetchval("SELECT current_user") == "civia_app"
        bypass = await conn.fetchval(
            "SELECT rolbypassrls FROM pg_roles WHERE rolname = 'civia_app'"
        )
        assert bypass is False
        await conn.close()
    finally:
        await enable_app_role(admin_url, original)
