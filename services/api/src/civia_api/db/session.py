"""Motor asíncrono y sesiones. El contexto de tenant se fija por transacción para RLS."""

import uuid
from collections.abc import AsyncIterator
from functools import lru_cache

from sqlalchemy import text
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from civia_api.config import get_settings


@lru_cache
def get_engine() -> AsyncEngine:
    return create_async_engine(get_settings().database_url, pool_pre_ping=True)


@lru_cache
def get_sessionmaker() -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(get_engine(), expire_on_commit=False)


class CommitOnError(Exception):
    """Excepción de dominio cuyos efectos previos SÍ deben persistir.

    Ejemplo: un login fallido se rechaza, pero su evento de auditoría y el contador de
    intentos deben quedar guardados; un refresh reutilizado se rechaza, pero la
    revocación de la familia de tokens debe confirmarse.
    """


async def get_session() -> AsyncIterator[AsyncSession]:
    """Dependencia FastAPI: una transacción por petición (commit al final, rollback si falla)."""
    async with get_sessionmaker()() as session:
        try:
            yield session
            await session.commit()
        except CommitOnError:
            await session.commit()
            raise
        except BaseException:
            await session.rollback()
            raise


async def set_tenant_context(
    session: AsyncSession, *, user_id: uuid.UUID | None, org_id: uuid.UUID | None
) -> None:
    """Fija las variables que leen las políticas RLS. `is_local=true` → solo esta transacción."""
    await session.execute(
        text("SELECT set_config('app.user_id', :u, true), set_config('app.org_id', :o, true)"),
        {"u": str(user_id) if user_id else "", "o": str(org_id) if org_id else ""},
    )
