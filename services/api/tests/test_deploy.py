"""El paso de despliegue habilita el rol civia_app con una contraseña arbitraria (escapada)."""

from urllib.parse import urlsplit

import asyncpg

from civia_api.deploy import enable_app_role


async def test_enable_app_role_sets_escaped_password(database_urls: tuple[str, str]) -> None:
    admin_url, app_url = database_urls
    original = urlsplit(app_url).password or ""
    parts = urlsplit(app_url)
    tricky = "p'w\"; DROP ROLE civia_app; --"
    try:
        await enable_app_role(admin_url, tricky)
        conn = await asyncpg.connect(
            host=parts.hostname,
            port=parts.port,
            user="civia_app",
            password=tricky,
            database=parts.path.lstrip("/"),
        )
        assert await conn.fetchval("SELECT current_user") == "civia_app"
        bypass = await conn.fetchval(
            "SELECT rolbypassrls FROM pg_roles WHERE rolname = 'civia_app'"
        )
        assert bypass is False
        await conn.close()
    finally:
        await enable_app_role(admin_url, original)
