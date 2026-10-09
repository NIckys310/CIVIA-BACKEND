"""Seguridad de la cuenta: MFA, verificación de correo, contraseña e historial."""

from typing import Annotated

from fastapi import APIRouter, Query, Request, status
from sqlalchemy import select

from civia_api.api.deps import CurrentUserDep, SessionDep, client_ip
from civia_api.models import AuditLog
from civia_api.schemas import (
    ChangePasswordIn,
    EmailTokenIn,
    ForgotPasswordIn,
    MfaCodeIn,
    MfaDisableIn,
    MfaSetupOut,
    MfaStatusOut,
    RecoveryCodesOut,
    ResetPasswordIn,
    SecurityEventOut,
)
from civia_api.services import account

router = APIRouter(prefix="/account", tags=["account"])


# --- MFA ---------------------------------------------------------------------------------


@router.get("/mfa", response_model=MfaStatusOut)
async def mfa_status(session: SessionDep, current: CurrentUserDep) -> MfaStatusOut:
    return MfaStatusOut(
        enabled=current.user.mfa_enabled,
        recovery_codes_remaining=await account.remaining_recovery_codes(session, current.user),
    )


@router.post("/mfa/setup", response_model=MfaSetupOut)
async def mfa_setup(session: SessionDep, current: CurrentUserDep) -> MfaSetupOut:
    """Genera la semilla (cifrada en BD). No queda activo hasta confirmar un código."""
    setup = await account.start_mfa_setup(session, current.user)
    return MfaSetupOut(secret=setup.secret, otpauth_uri=setup.otpauth_uri)


@router.post("/mfa/enable", response_model=RecoveryCodesOut)
async def mfa_enable(
    data: MfaCodeIn, request: Request, session: SessionDep, current: CurrentUserDep
) -> RecoveryCodesOut:
    """Activa MFA y devuelve los códigos de recuperación UNA sola vez."""
    codes = await account.enable_mfa(session, current.user, data.code, ip=client_ip(request))
    return RecoveryCodesOut(codes=codes)


@router.post("/mfa/disable", status_code=status.HTTP_204_NO_CONTENT)
async def mfa_disable(
    data: MfaDisableIn, request: Request, session: SessionDep, current: CurrentUserDep
) -> None:
    await account.disable_mfa(
        session,
        current.user,
        password=data.password,
        code=data.code,
        recovery_code=data.recovery_code,
        ip=client_ip(request),
    )


@router.post("/mfa/recovery-codes", response_model=RecoveryCodesOut)
async def mfa_recovery_codes(
    data: MfaCodeIn, request: Request, session: SessionDep, current: CurrentUserDep
) -> RecoveryCodesOut:
    codes = await account.regenerate_recovery_codes(
        session, current.user, code=data.code, ip=client_ip(request)
    )
    return RecoveryCodesOut(codes=codes)


# --- Correo ------------------------------------------------------------------------------


@router.post("/email/verification", status_code=status.HTTP_202_ACCEPTED)
async def resend_verification(session: SessionDep, current: CurrentUserDep) -> None:
    await account.send_verification_email(session, current.user)


@router.post("/email/verify", status_code=status.HTTP_204_NO_CONTENT)
async def verify_email(data: EmailTokenIn, request: Request, session: SessionDep) -> None:
    """Público: se abre desde el enlace del correo (no requiere sesión)."""
    await account.confirm_email(session, data.token, ip=client_ip(request))


# --- Contraseña --------------------------------------------------------------------------


@router.post("/password/forgot", status_code=status.HTTP_202_ACCEPTED)
async def forgot_password(data: ForgotPasswordIn, request: Request, session: SessionDep) -> None:
    """Siempre 202, exista o no la cuenta."""
    await account.request_password_reset(session, data.email, ip=client_ip(request))


@router.post("/password/reset", status_code=status.HTTP_204_NO_CONTENT)
async def reset_password(data: ResetPasswordIn, request: Request, session: SessionDep) -> None:
    """Cambia la contraseña con el enlace del correo y cierra TODAS las sesiones."""
    await account.reset_password(session, data.token, data.password, ip=client_ip(request))


@router.post("/password/change", status_code=status.HTTP_204_NO_CONTENT)
async def change_password(
    data: ChangePasswordIn, request: Request, session: SessionDep, current: CurrentUserDep
) -> None:
    """Cambia la contraseña y cierra las demás sesiones (conserva la actual)."""
    await account.change_password(
        session,
        current.user,
        current=data.current_password,
        new=data.new_password,
        session_family=current.session_id,
        ip=client_ip(request),
    )


# --- Historial de seguridad ----------------------------------------------------------------


@router.get("/security-events", response_model=list[SecurityEventOut])
async def security_events(
    session: SessionDep,
    current: CurrentUserDep,
    limit: Annotated[int, Query(ge=1, le=100)] = 30,
) -> list[SecurityEventOut]:
    """Eventos de seguridad del propio usuario (RLS: política `audit_read_own`)."""
    rows = await session.scalars(
        select(AuditLog)
        .where(AuditLog.actor_user_id == current.user.id, AuditLog.action.startswith("auth."))
        .order_by(AuditLog.seq.desc())
        .limit(limit)
    )
    return [
        SecurityEventOut(
            occurred_at=r.occurred_at,
            action=r.action,
            ip_address=r.ip_address,
            device=r.details.get("device") if isinstance(r.details, dict) else None,
        )
        for r in rows
    ]
