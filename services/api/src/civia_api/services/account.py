"""Seguridad de la cuenta: bloqueo, MFA (TOTP + recuperación), verificación de correo,
recuperación y cambio de contraseña, y avisos de seguridad por correo.

Todas las operaciones que fallan por un intento inválido lanzan `AuthError` (que hereda de
CommitOnError), así el contador de intentos y la auditoría quedan guardados.
"""

import hashlib
import secrets
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete, func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from civia_api.config import get_settings
from civia_api.models import MfaRecoveryCode, RefreshToken, TokenPurpose, User, UserToken
from civia_api.security import passwords, totp
from civia_api.security.crypto import DecryptionError, decrypt_field, encrypt_field
from civia_api.security.ratelimit import get_rate_limiter
from civia_api.services.audit import record_audit
from civia_api.services.email import send_email
from civia_api.services.errors import AuthError

EMAIL_VERIFY_TTL = timedelta(hours=24)
PASSWORD_RESET_TTL = timedelta(minutes=30)
LOCKED_MESSAGE = "Demasiados intentos. Espera unos minutos e inténtalo de nuevo."


def _now() -> datetime:
    return datetime.now(UTC)


def _mfa_context(user_id: uuid.UUID) -> str:
    return f"user:{user_id}:mfa"


# --- Bloqueo de cuenta ----------------------------------------------------------------


def is_locked(user: User) -> bool:
    return user.locked_until is not None and user.locked_until > _now()


async def register_failure(session: AsyncSession, user: User, *, ip: str | None) -> None:
    """Suma un intento fallido (contraseña o MFA); al llegar al umbral bloquea la cuenta."""
    settings = get_settings()
    user.failed_login_count += 1
    if user.failed_login_count >= settings.lockout_threshold:
        user.locked_until = _now() + timedelta(seconds=settings.lockout_seconds)
        user.failed_login_count = 0
        await record_audit(session, "auth.account.locked", actor_user_id=user.id, ip_address=ip)
        await send_email(
            user.email,
            "CIVIA: bloqueamos temporalmente tu cuenta",
            "Detectamos varios intentos fallidos de inicio de sesión y bloqueamos tu cuenta "
            f"durante {settings.lockout_seconds // 60} minutos.\n"
            "Si no fuiste tú, te recomendamos cambiar tu contraseña y activar la verificación "
            "en dos pasos.",
        )


def clear_failures(user: User) -> None:
    user.failed_login_count = 0
    user.locked_until = None


# --- Tokens de un solo uso por correo ---------------------------------------------------


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


async def _issue_token(
    session: AsyncSession, user: User, purpose: TokenPurpose, ttl: timedelta
) -> str:
    # Solo el último enlace emitido es válido.
    await session.execute(
        update(UserToken)
        .where(
            UserToken.user_id == user.id,
            UserToken.purpose == purpose,
            UserToken.used_at.is_(None),
        )
        .values(used_at=func.now())
    )
    plain = secrets.token_urlsafe(32)
    session.add(
        UserToken(
            user_id=user.id, purpose=purpose, token_hash=_hash(plain), expires_at=_now() + ttl
        )
    )
    await session.flush()
    return plain


async def _consume_token(session: AsyncSession, token: str, purpose: TokenPurpose) -> User:
    record = await session.scalar(
        select(UserToken).where(UserToken.token_hash == _hash(token)).with_for_update()
    )
    if (
        record is None
        or record.purpose != purpose
        or record.used_at is not None
        or record.expires_at <= _now()
    ):
        raise AuthError("El enlace no es válido o ya expiró. Solicita uno nuevo.", status=400)
    record.used_at = _now()
    user = await session.get(User, record.user_id)
    if user is None or user.deleted_at is not None:
        raise AuthError("El enlace no es válido o ya expiró. Solicita uno nuevo.", status=400)
    return user


# --- Verificación de correo -------------------------------------------------------------


async def send_verification_email(session: AsyncSession, user: User) -> None:
    if user.email_verified_at is not None:
        return
    token = await _issue_token(session, user, TokenPurpose.EMAIL_VERIFY, EMAIL_VERIFY_TTL)
    link = f"{get_settings().web_base_url}/verify-email?token={token}"
    await send_email(
        user.email,
        "CIVIA: confirma tu correo",
        f"Hola {user.full_name},\n\nConfirma tu correo para proteger tu cuenta:\n{link}\n\n"
        "El enlace vence en 24 horas.",
    )


async def confirm_email(session: AsyncSession, token: str, *, ip: str | None) -> User:
    user = await _consume_token(session, token, TokenPurpose.EMAIL_VERIFY)
    user.email_verified_at = user.email_verified_at or _now()
    await record_audit(session, "auth.email.verified", actor_user_id=user.id, ip_address=ip)
    return user


# --- Contraseña -------------------------------------------------------------------------


async def _validate_new_password(password: str, email: str) -> None:
    if errors := passwords.password_policy_errors(password, email=email):
        raise AuthError(" ".join(errors), status=422)
    if get_settings().hibp_check_enabled and await passwords.is_breached_password(password):
        raise AuthError("Esta contraseña aparece en filtraciones públicas. Elige otra.", status=422)


async def _set_password(
    session: AsyncSession, user: User, password: str, *, keep_family: uuid.UUID | None
) -> None:
    user.password_hash = passwords.hash_password(password)
    user.password_changed_at = _now()
    clear_failures(user)
    # Cerrar todas las sesiones (salvo, opcionalmente, la actual).
    stmt = update(RefreshToken).where(
        RefreshToken.user_id == user.id, RefreshToken.revoked_at.is_(None)
    )
    if keep_family is not None:
        stmt = stmt.where(RefreshToken.family_id != keep_family)
    await session.execute(stmt.values(revoked_at=func.now()))
    await send_email(
        user.email,
        "CIVIA: tu contraseña cambió",
        "La contraseña de tu cuenta CIVIA se cambió y se cerraron las demás sesiones.\n"
        "Si no fuiste tú, recupera tu cuenta de inmediato.",
    )


async def request_password_reset(session: AsyncSession, email: str, *, ip: str | None) -> None:
    """Siempre responde igual exista o no la cuenta (no permite enumerar correos)."""
    email = email.strip().lower()
    limiter = get_rate_limiter()
    if not await limiter.hit(f"reset:email:{email}", limit=3, window_seconds=3600):
        return
    if not await limiter.hit(f"reset:ip:{ip}", limit=20, window_seconds=3600):
        return
    user = await session.scalar(select(User).where(User.email == email, User.deleted_at.is_(None)))
    if user is None or not user.is_active:
        return
    token = await _issue_token(session, user, TokenPurpose.PASSWORD_RESET, PASSWORD_RESET_TTL)
    await record_audit(
        session, "auth.password.reset_requested", actor_user_id=user.id, ip_address=ip
    )
    link = f"{get_settings().web_base_url}/reset-password?token={token}"
    await send_email(
        user.email,
        "CIVIA: restablece tu contraseña",
        f"Recibimos una solicitud para restablecer tu contraseña:\n{link}\n\n"
        "El enlace vence en 30 minutos y solo se puede usar una vez. "
        "Si no la solicitaste, ignora este correo.",
    )


async def reset_password(
    session: AsyncSession, token: str, new_password: str, *, ip: str | None
) -> None:
    user = await _consume_token(session, token, TokenPurpose.PASSWORD_RESET)
    await _validate_new_password(new_password, user.email)
    await _set_password(session, user, new_password, keep_family=None)
    # Si el usuario llegó por el correo, el correo queda verificado.
    user.email_verified_at = user.email_verified_at or _now()
    await record_audit(session, "auth.password.reset", actor_user_id=user.id, ip_address=ip)


async def change_password(
    session: AsyncSession,
    user: User,
    *,
    current: str,
    new: str,
    session_family: uuid.UUID,
    ip: str | None,
) -> None:
    if not passwords.verify_password(user.password_hash, current):
        await register_failure(session, user, ip=ip)
        raise AuthError("La contraseña actual no es correcta.", status=400)
    await _validate_new_password(new, user.email)
    await _set_password(session, user, new, keep_family=session_family)
    await record_audit(session, "auth.password.changed", actor_user_id=user.id, ip_address=ip)


# --- MFA (TOTP) -------------------------------------------------------------------------


@dataclass(frozen=True)
class MfaSetup:
    secret: str
    otpauth_uri: str


async def start_mfa_setup(session: AsyncSession, user: User) -> MfaSetup:
    if user.mfa_enabled:
        raise AuthError("La verificación en dos pasos ya está activa.", status=409)
    secret = totp.new_secret()
    user.mfa_secret_enc = encrypt_field(secret, context=_mfa_context(user.id))
    user.mfa_last_step = None
    await session.flush()
    uri = totp.provisioning_uri(secret, account=user.email, issuer=get_settings().mfa_issuer)
    return MfaSetup(secret=secret, otpauth_uri=uri)


def _secret(user: User) -> str:
    if user.mfa_secret_enc is None:
        raise AuthError(
            "Primero inicia la configuración de la verificación en dos pasos.", status=409
        )
    try:
        return decrypt_field(user.mfa_secret_enc, context=_mfa_context(user.id))
    except DecryptionError as exc:
        raise AuthError(
            "No se pudo validar el segundo factor. Contacta a soporte.", status=500
        ) from exc


def _check_totp(user: User, code: str) -> bool:
    step = totp.verify(_secret(user), code, last_step=user.mfa_last_step)
    if step is None:
        return False
    user.mfa_last_step = step
    return True


async def _new_recovery_codes(session: AsyncSession, user: User) -> list[str]:
    await session.execute(delete(MfaRecoveryCode).where(MfaRecoveryCode.user_id == user.id))
    codes = totp.new_recovery_codes()
    session.add_all(
        MfaRecoveryCode(user_id=user.id, code_hash=totp.hash_recovery_code(c)) for c in codes
    )
    await session.flush()
    return codes


async def _use_recovery_code(session: AsyncSession, user: User, code: str) -> bool:
    record = await session.scalar(
        select(MfaRecoveryCode)
        .where(
            MfaRecoveryCode.user_id == user.id,
            MfaRecoveryCode.code_hash == totp.hash_recovery_code(code),
            MfaRecoveryCode.used_at.is_(None),
        )
        .with_for_update()
    )
    if record is None:
        return False
    record.used_at = _now()
    return True


async def enable_mfa(session: AsyncSession, user: User, code: str, *, ip: str | None) -> list[str]:
    if user.mfa_enabled:
        raise AuthError("La verificación en dos pasos ya está activa.", status=409)
    if not _check_totp(user, code):
        raise AuthError("El código no es válido. Revisa la hora de tu teléfono.", status=400)
    user.mfa_enabled_at = _now()
    codes = await _new_recovery_codes(session, user)
    await record_audit(session, "auth.mfa.enabled", actor_user_id=user.id, ip_address=ip)
    await send_email(
        user.email,
        "CIVIA: verificación en dos pasos activada",
        "Activaste la verificación en dos pasos. Guarda tus códigos de recuperación en un "
        "lugar seguro.",
    )
    return codes


async def _verify_second_factor(
    session: AsyncSession, user: User, *, code: str | None, recovery_code: str | None
) -> bool:
    if code:
        return _check_totp(user, code)
    if recovery_code:
        return await _use_recovery_code(session, user, recovery_code)
    return False


async def disable_mfa(
    session: AsyncSession,
    user: User,
    *,
    password: str,
    code: str | None,
    recovery_code: str | None,
    ip: str | None,
) -> None:
    if not user.mfa_enabled:
        raise AuthError("La verificación en dos pasos no está activa.", status=409)
    ok_password = passwords.verify_password(user.password_hash, password)
    ok_factor = await _verify_second_factor(session, user, code=code, recovery_code=recovery_code)
    if not (ok_password and ok_factor):
        await register_failure(session, user, ip=ip)
        raise AuthError("Contraseña o código incorrectos.", status=400)
    user.mfa_enabled_at = None
    user.mfa_secret_enc = None
    user.mfa_last_step = None
    await session.execute(delete(MfaRecoveryCode).where(MfaRecoveryCode.user_id == user.id))
    await record_audit(session, "auth.mfa.disabled", actor_user_id=user.id, ip_address=ip)
    await send_email(
        user.email,
        "CIVIA: verificación en dos pasos desactivada",
        "Se desactivó la verificación en dos pasos de tu cuenta. Si no fuiste tú, cambia tu "
        "contraseña de inmediato.",
    )


async def regenerate_recovery_codes(
    session: AsyncSession, user: User, *, code: str, ip: str | None
) -> list[str]:
    if not user.mfa_enabled:
        raise AuthError("La verificación en dos pasos no está activa.", status=409)
    if not _check_totp(user, code):
        await register_failure(session, user, ip=ip)
        raise AuthError("El código no es válido.", status=400)
    codes = await _new_recovery_codes(session, user)
    await record_audit(
        session, "auth.mfa.recovery_regenerated", actor_user_id=user.id, ip_address=ip
    )
    return codes


async def remaining_recovery_codes(session: AsyncSession, user: User) -> int:
    return int(
        await session.scalar(
            select(func.count())
            .select_from(MfaRecoveryCode)
            .where(MfaRecoveryCode.user_id == user.id, MfaRecoveryCode.used_at.is_(None))
        )
        or 0
    )


async def check_login_second_factor(
    session: AsyncSession,
    user: User,
    *,
    code: str | None,
    recovery_code: str | None,
    ip: str | None,
) -> None:
    """Paso 2 del login. Los fallos cuentan para el bloqueo (6 dígitos son fuerza-brutables)."""
    limiter = get_rate_limiter()
    if is_locked(user) or not await limiter.hit(f"mfa:user:{user.id}", limit=5, window_seconds=300):
        raise AuthError(LOCKED_MESSAGE, status=429)
    if not await _verify_second_factor(session, user, code=code, recovery_code=recovery_code):
        await register_failure(session, user, ip=ip)
        await record_audit(session, "auth.mfa.failed", actor_user_id=user.id, ip_address=ip)
        raise AuthError("El código no es válido.")
    if recovery_code:
        await record_audit(session, "auth.mfa.recovery_used", actor_user_id=user.id, ip_address=ip)


# --- Avisos ------------------------------------------------------------------------------


async def notify_if_new_device(
    session: AsyncSession, user: User, *, device_label: str | None, ip: str | None
) -> None:
    """Avisa por correo cuando se inicia sesión desde un dispositivo no visto antes."""
    previous = await session.scalar(
        select(func.count()).select_from(RefreshToken).where(RefreshToken.user_id == user.id)
    )
    if not previous:
        return  # primer inicio de sesión de la cuenta
    seen = await session.scalar(
        select(func.count())
        .select_from(RefreshToken)
        .where(RefreshToken.user_id == user.id, RefreshToken.device_label == device_label)
    )
    if seen:
        return
    await record_audit(
        session,
        "auth.login.new_device",
        actor_user_id=user.id,
        ip_address=ip,
        details={"device": device_label},
    )
    await send_email(
        user.email,
        "CIVIA: nuevo inicio de sesión",
        f"Se inició sesión en tu cuenta desde un dispositivo nuevo:\n"
        f"  Dispositivo: {device_label or 'desconocido'}\n  IP: {ip or 'desconocida'}\n\n"
        "Si fuiste tú, no necesitas hacer nada. Si no, cierra esa sesión desde "
        "Perfil y seguridad y cambia tu contraseña.",
    )
