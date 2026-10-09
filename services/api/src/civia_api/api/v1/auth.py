"""Endpoints de autenticación.

Web: el refresh token viaja en cookie `HttpOnly; Secure; SameSite=Strict` limitada a
/api/v1/auth y las peticiones que la usan exigen la cabecera `X-Requested-With: civia`
(fuerza preflight CORS → protección CSRF).
Móvil (`X-Client: mobile`): el refresh token va en el cuerpo y se guarda en SecureStore.
"""

import uuid
from typing import Annotated

from fastapi import APIRouter, Cookie, Header, HTTPException, Request, Response, status

from civia_api.api.deps import CurrentUserDep, SessionDep, client_ip
from civia_api.config import get_settings
from civia_api.schemas import (
    LoginIn,
    LoginOut,
    MfaVerifyIn,
    RefreshIn,
    RegisterIn,
    SessionOut,
    TokenOut,
    UserOut,
)
from civia_api.security.tokens import create_access_token
from civia_api.services import auth
from civia_api.services.audit import record_audit

router = APIRouter(prefix="/auth", tags=["auth"])

REFRESH_COOKIE = "civia_refresh"
COOKIE_PATH = "/api/v1/auth"

ClientHeader = Annotated[str | None, Header(alias="X-Client")]
RequestedWith = Annotated[str | None, Header(alias="X-Requested-With")]


def _is_mobile(x_client: str | None) -> bool:
    return x_client == "mobile"


def _token_response(response: Response, issued: auth.IssuedSession, *, mobile: bool) -> TokenOut:
    access, ttl = create_access_token(issued.user_id, session_id=issued.family_id)
    if mobile:
        return TokenOut(access_token=access, expires_in=ttl, refresh_token=issued.refresh_token)
    response.set_cookie(
        REFRESH_COOKIE,
        issued.refresh_token,
        max_age=get_settings().refresh_token_ttl_seconds,
        path=COOKIE_PATH,
        httponly=True,
        secure=True,
        samesite="strict",
    )
    return TokenOut(access_token=access, expires_in=ttl)


def _require_csrf_header(requested_with: str | None) -> None:
    if requested_with != "civia":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Cabecera X-Requested-With requerida.")


@router.post("/register", response_model=UserOut, status_code=status.HTTP_201_CREATED)
async def register(data: RegisterIn, request: Request, session: SessionDep) -> UserOut:
    user = await auth.register(session, data, ip=client_ip(request))
    return UserOut.model_validate(user)


@router.post("/login", response_model=LoginOut)
async def login(
    data: LoginIn,
    request: Request,
    response: Response,
    session: SessionDep,
    x_client: ClientHeader = None,
) -> LoginOut:
    """Paso 1. Si la cuenta tiene MFA, devuelve `mfa_required` y un `mfa_token` de 5 min."""
    device = data.device_label or request.headers.get("user-agent", "")[:200] or None
    result = await auth.login(
        session,
        email=data.email,
        password=data.password,
        ip=client_ip(request),
        device_label=device,
    )
    if isinstance(result, auth.MfaRequired):
        return LoginOut(mfa_required=True, mfa_token=result.challenge_token)
    tokens = _token_response(response, result, mobile=_is_mobile(x_client))
    return LoginOut(**tokens.model_dump())


@router.post("/mfa/verify", response_model=TokenOut)
async def verify_mfa(
    data: MfaVerifyIn,
    request: Request,
    response: Response,
    session: SessionDep,
    x_client: ClientHeader = None,
) -> TokenOut:
    """Paso 2 del login: código TOTP de 6 dígitos o un código de recuperación."""
    issued = await auth.verify_mfa_login(
        session,
        challenge_token=data.mfa_token,
        code=data.code,
        recovery_code=data.recovery_code,
        ip=client_ip(request),
    )
    return _token_response(response, issued, mobile=_is_mobile(x_client))


@router.post("/refresh", response_model=TokenOut)
async def refresh(
    request: Request,
    response: Response,
    session: SessionDep,
    body: RefreshIn | None = None,
    x_client: ClientHeader = None,
    requested_with: RequestedWith = None,
    cookie_token: Annotated[str | None, Cookie(alias=REFRESH_COOKIE)] = None,
) -> TokenOut:
    mobile = _is_mobile(x_client)
    if mobile:
        token = body.refresh_token if body else None
    else:
        _require_csrf_header(requested_with)
        token = cookie_token
    if not token:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Sesión expirada.")
    issued = await auth.rotate(session, token, ip=client_ip(request))
    return _token_response(response, issued, mobile=mobile)


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(
    request: Request,
    response: Response,
    session: SessionDep,
    current: CurrentUserDep,
) -> None:
    await auth.revoke_family(session, current.user.id, current.session_id)
    await record_audit(
        session,
        "auth.logout",
        actor_user_id=current.user.id,
        ip_address=client_ip(request),
        target_type="session",
        target_id=str(current.session_id),
    )
    response.delete_cookie(REFRESH_COOKIE, path=COOKIE_PATH, secure=True, httponly=True)


@router.get("/sessions", response_model=list[SessionOut])
async def sessions(session: SessionDep, current: CurrentUserDep) -> list[SessionOut]:
    rows = await auth.list_sessions(session, current.user.id)
    return [
        SessionOut(
            id=fid,
            device_label=device,
            ip_address=ip,
            started_at=started,
            last_seen_at=last,
            current=fid == current.session_id,
        )
        for fid, device, ip, started, last in rows
    ]


@router.delete("/sessions/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def revoke_session(
    session_id: uuid.UUID, request: Request, session: SessionDep, current: CurrentUserDep
) -> None:
    """Cierre de sesión remoto (p. ej. un celular perdido)."""
    if not await auth.revoke_family(session, current.user.id, session_id):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Sesión no encontrada.")
    await record_audit(
        session,
        "auth.session.revoked",
        actor_user_id=current.user.id,
        ip_address=client_ip(request),
        target_type="session",
        target_id=str(session_id),
    )
