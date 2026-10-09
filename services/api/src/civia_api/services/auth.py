"""Lógica de autenticación: registro, login, rotación de refresh tokens y sesiones."""

import re
import secrets
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from civia_api.config import get_settings
from civia_api.db.ids import uuid7
from civia_api.db.session import CommitOnError, set_tenant_context
from civia_api.models import Membership, Organization, RefreshToken, Role, User
from civia_api.schemas import RegisterIn
from civia_api.security import passwords
from civia_api.security.ratelimit import get_rate_limiter
from civia_api.security.tokens import hash_refresh_token, new_refresh_token
from civia_api.services.audit import record_audit

# Margen para refrescos concurrentes legítimos (p. ej. dos pestañas a la vez).
REUSE_GRACE = timedelta(seconds=10)


class AuthError(CommitOnError):
    """Error de autenticación con mensaje seguro para mostrar al usuario.

    Hereda de CommitOnError: la auditoría y las revocaciones previas al error persisten.
    Por eso todas las validaciones de `register` ocurren antes de escribir nada.
    """

    def __init__(self, message: str, *, status: int = 401) -> None:
        super().__init__(message)
        self.message = message
        self.status = status


@dataclass(frozen=True)
class IssuedSession:
    user_id: uuid.UUID
    family_id: uuid.UUID
    refresh_token: str


def normalize_email(email: str) -> str:
    return email.strip().lower()


def _slugify(name: str) -> str:
    base = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")[:60] or "org"
    return f"{base}-{secrets.token_hex(3)}"


async def register(session: AsyncSession, data: RegisterIn, *, ip: str | None) -> User:
    email = normalize_email(data.email)
    if errors := passwords.password_policy_errors(data.password, email=email):
        raise AuthError(" ".join(errors), status=422)
    if get_settings().hibp_check_enabled and await passwords.is_breached_password(data.password):
        raise AuthError(
            "Esta contraseña aparece en filtraciones públicas. Elige otra.", status=422
        )
    if await session.scalar(select(User.id).where(User.email == email)):
        # Mensaje genérico: no confirma si el correo ya existe.
        raise AuthError("No fue posible completar el registro con esos datos.", status=409)

    user = User(
        id=uuid7(),
        email=email,
        password_hash=passwords.hash_password(data.password),
        full_name=data.full_name.strip(),
    )
    session.add(user)
    await session.flush()

    org_id = uuid7()
    await set_tenant_context(session, user_id=user.id, org_id=org_id)
    session.add(Organization(id=org_id, name=data.organization_name.strip(), slug=_slugify(data.organization_name)))
    await session.flush()
    session.add(Membership(organization_id=org_id, user_id=user.id, role=Role.ADMIN))
    await record_audit(
        session, "auth.register", organization_id=org_id, actor_user_id=user.id, ip_address=ip
    )
    return user


async def login(
    session: AsyncSession, *, email: str, password: str, ip: str | None, device_label: str | None
) -> IssuedSession:
    settings = get_settings()
    email = normalize_email(email)
    limiter = get_rate_limiter()
    window = settings.login_window_seconds
    allowed_ip = await limiter.hit(f"login:ip:{ip}", limit=settings.login_max_attempts * 4, window_seconds=window)
    allowed_email = await limiter.hit(f"login:email:{email}", limit=settings.login_max_attempts, window_seconds=window)
    if not (allowed_ip and allowed_email):
        await record_audit(session, "auth.login.rate_limited", ip_address=ip, details={"email": email})
        raise AuthError("Demasiados intentos. Espera unos minutos e inténtalo de nuevo.", status=429)

    user = await session.scalar(
        select(User).where(User.email == email, User.deleted_at.is_(None))
    )
    # verify_password usa un hash ficticio si el usuario no existe (tiempo constante).
    valid = passwords.verify_password(user.password_hash if user else None, password)
    if not user or not valid or not user.is_active:
        await record_audit(
            session,
            "auth.login.failed",
            actor_user_id=user.id if user else None,
            ip_address=ip,
            details={"email": email},
        )
        raise AuthError("Correo o contraseña incorrectos.")

    if passwords.needs_rehash(user.password_hash):
        user.password_hash = passwords.hash_password(password)
    user.last_login_at = datetime.now(UTC)
    await limiter.reset(f"login:email:{email}")

    issued = await _issue(session, user.id, family_id=uuid7(), ip=ip, device_label=device_label)
    await record_audit(
        session, "auth.login.succeeded", actor_user_id=user.id, ip_address=ip,
        target_type="session", target_id=str(issued.family_id),
    )
    return issued


async def _issue(
    session: AsyncSession,
    user_id: uuid.UUID,
    *,
    family_id: uuid.UUID,
    ip: str | None,
    device_label: str | None,
) -> IssuedSession:
    plain, digest = new_refresh_token()
    session.add(
        RefreshToken(
            user_id=user_id,
            family_id=family_id,
            token_hash=digest,
            expires_at=datetime.now(UTC) + timedelta(seconds=get_settings().refresh_token_ttl_seconds),
            device_label=device_label,
            ip_address=ip,
        )
    )
    await session.flush()
    return IssuedSession(user_id=user_id, family_id=family_id, refresh_token=plain)


async def rotate(session: AsyncSession, refresh_token: str, *, ip: str | None) -> IssuedSession:
    """Canjea un refresh token por uno nuevo de la misma familia.

    Si llega un token ya rotado fuera del margen de gracia, se asume robo: se revoca
    la familia completa y el usuario debe iniciar sesión de nuevo.
    """
    record = await session.scalar(
        select(RefreshToken)
        .where(RefreshToken.token_hash == hash_refresh_token(refresh_token))
        .with_for_update()
    )
    now = datetime.now(UTC)
    if record is None or record.revoked_at is not None or record.expires_at <= now:
        raise AuthError("Sesión expirada. Inicia sesión de nuevo.")

    if record.rotated_at is not None:
        if now - record.rotated_at <= REUSE_GRACE:
            raise AuthError("Sesión en uso por otra petición. Reintenta.", status=409)
        await revoke_family(session, record.user_id, record.family_id)
        await record_audit(
            session, "auth.refresh.reuse_detected", actor_user_id=record.user_id, ip_address=ip,
            target_type="session", target_id=str(record.family_id),
        )
        raise AuthError("Sesión revocada por seguridad. Inicia sesión de nuevo.")

    user = await session.get(User, record.user_id)
    if user is None or not user.is_active or user.deleted_at is not None:
        raise AuthError("Sesión expirada. Inicia sesión de nuevo.")

    record.rotated_at = now
    return await _issue(
        session, record.user_id, family_id=record.family_id, ip=ip, device_label=record.device_label
    )


async def revoke_family(session: AsyncSession, user_id: uuid.UUID, family_id: uuid.UUID) -> int:
    result = await session.execute(
        update(RefreshToken)
        .where(
            RefreshToken.user_id == user_id,
            RefreshToken.family_id == family_id,
            RefreshToken.revoked_at.is_(None),
        )
        .values(revoked_at=func.now())
    )
    return result.rowcount  # type: ignore[attr-defined, no-any-return]


async def is_session_active(session: AsyncSession, family_id: uuid.UUID) -> bool:
    return bool(
        await session.scalar(
            select(func.count())
            .select_from(RefreshToken)
            .where(
                RefreshToken.family_id == family_id,
                RefreshToken.revoked_at.is_(None),
                RefreshToken.expires_at > func.now(),
            )
        )
    )


async def list_sessions(
    session: AsyncSession, user_id: uuid.UUID
) -> list[tuple[uuid.UUID, str | None, str | None, datetime, datetime]]:
    """Familias activas del usuario: (id, dispositivo, ip, inicio, última actividad)."""
    rows = await session.execute(
        select(
            RefreshToken.family_id,
            func.max(RefreshToken.device_label),
            func.max(RefreshToken.ip_address),
            func.min(RefreshToken.created_at),
            func.max(RefreshToken.created_at),
        )
        .where(RefreshToken.user_id == user_id)
        .group_by(RefreshToken.family_id)
        .having(
            func.bool_or(
                RefreshToken.revoked_at.is_(None) & (RefreshToken.expires_at > func.now())
            )
        )
        .order_by(func.max(RefreshToken.created_at).desc())
    )
    return [tuple(r) for r in rows.all()]  # type: ignore[misc]
