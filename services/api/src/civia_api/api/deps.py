"""Dependencias de autenticación, contexto de organización (tenant) y autorización."""

import uuid
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import Annotated

from fastapi import Depends, Header, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from civia_api.db.session import get_session, set_tenant_context
from civia_api.models import Membership, Role, User
from civia_api.security.rbac import Permission, has_permission
from civia_api.security.tokens import InvalidTokenError, decode_access_token
from civia_api.services.auth import is_session_active

SessionDep = Annotated[AsyncSession, Depends(get_session)]
_bearer = HTTPBearer(auto_error=False)

_UNAUTHORIZED = HTTPException(
    status_code=status.HTTP_401_UNAUTHORIZED,
    detail="Sesión no válida o expirada.",
    headers={"WWW-Authenticate": "Bearer"},
)


def client_ip(request: Request) -> str | None:
    # Detrás de un proxy, configurar uvicorn --proxy-headers con --forwarded-allow-ips
    # explícitas; nunca confiar en X-Forwarded-For de cualquier origen.
    return request.client.host if request.client else None


@dataclass(frozen=True)
class CurrentUser:
    user: User
    session_id: uuid.UUID


async def get_current_user(
    session: SessionDep,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
) -> CurrentUser:
    if credentials is None:
        raise _UNAUTHORIZED
    try:
        claims = decode_access_token(credentials.credentials)
        user_id, session_id = uuid.UUID(claims["sub"]), uuid.UUID(claims["sid"])
    except (InvalidTokenError, ValueError, KeyError):
        raise _UNAUTHORIZED from None

    # Revocación inmediata: si la sesión se cerró (logout remoto), el access token muere ya.
    if not await is_session_active(session, session_id):
        raise _UNAUTHORIZED
    user = await session.get(User, user_id)
    if user is None or not user.is_active or user.deleted_at is not None:
        raise _UNAUTHORIZED
    await set_tenant_context(session, user_id=user.id, org_id=None)
    return CurrentUser(user=user, session_id=session_id)


CurrentUserDep = Annotated[CurrentUser, Depends(get_current_user)]


@dataclass(frozen=True)
class OrgContext:
    user: User
    organization_id: uuid.UUID
    role: Role


async def get_org_context(
    session: SessionDep,
    current: CurrentUserDep,
    x_organization_id: Annotated[uuid.UUID, Header(alias="X-Organization-Id")],
) -> OrgContext:
    """Valida la membresía ANTES de fijar el tenant: RLS es la segunda barrera, no la única."""
    role = await session.scalar(
        select(Membership.role).where(
            Membership.user_id == current.user.id,
            Membership.organization_id == x_organization_id,
        )
    )
    if role is None:
        # 404 y no 403: no revelar si la organización existe.
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Organización no encontrada.")
    await set_tenant_context(session, user_id=current.user.id, org_id=x_organization_id)
    return OrgContext(user=current.user, organization_id=x_organization_id, role=role)


OrgContextDep = Annotated[OrgContext, Depends(get_org_context)]


def require(permission: Permission) -> Callable[[OrgContext], Awaitable[OrgContext]]:
    async def checker(ctx: OrgContextDep) -> OrgContext:
        if not has_permission(ctx.role, permission):
            raise HTTPException(
                status.HTTP_403_FORBIDDEN, "Tu rol no permite realizar esta acción."
            )
        return ctx

    return checker
