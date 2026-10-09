"""Usuarios, organizaciones (tenants), membresías y tokens de refresco."""

import enum
import uuid
from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    Enum,
    ForeignKey,
    Index,
    String,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column

from civia_api.db.base import Base, SoftDelete, Timestamps, UUIDPrimaryKey


class Role(enum.StrEnum):
    """Roles RBAC. El orden refleja privilegio decreciente."""

    ADMIN = "admin"
    ENGINEER_IN_CHARGE = "engineer_in_charge"  # Ingeniero responsable
    REVIEWER = "reviewer"  # Revisor
    COLLABORATOR = "collaborator"  # Colaborador
    VIEWER = "viewer"  # Lector


role_enum = Enum(Role, name="role", values_callable=lambda e: [m.value for m in e])


class Organization(UUIDPrimaryKey, Timestamps, SoftDelete, Base):
    __tablename__ = "organizations"

    name: Mapped[str] = mapped_column(String(160))
    slug: Mapped[str] = mapped_column(String(80), unique=True)
    country_code: Mapped[str] = mapped_column(String(2), default="CO")
    default_norm_code: Mapped[str] = mapped_column(String(40), default="NSR-10")


class User(UUIDPrimaryKey, Timestamps, SoftDelete, Base):
    __tablename__ = "users"

    # Se guarda siempre en minúsculas (ver services.auth); índice único sobre el valor normalizado.
    email: Mapped[str] = mapped_column(String(254), unique=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    full_name: Mapped[str] = mapped_column(String(160))
    locale: Mapped[str] = mapped_column(String(10), default="es-CO")
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    email_verified_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_login_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    password_changed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    # MFA (TOTP): semilla cifrada con AES-256-GCM (security.crypto), nunca en claro.
    mfa_secret_enc: Mapped[str | None] = mapped_column(Text)
    mfa_enabled_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    mfa_last_step: Mapped[int | None] = mapped_column(BigInteger)  # anti-reutilización

    # Bloqueo de cuenta tras intentos fallidos consecutivos.
    failed_login_count: Mapped[int] = mapped_column(default=0, server_default="0")
    locked_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    @property
    def mfa_enabled(self) -> bool:
        return self.mfa_enabled_at is not None


class Membership(UUIDPrimaryKey, Timestamps, Base):
    __tablename__ = "memberships"
    __table_args__ = (UniqueConstraint("organization_id", "user_id"),)

    organization_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("organizations.id", ondelete="CASCADE"), index=True
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    role: Mapped[Role] = mapped_column(role_enum)


class RefreshToken(UUIDPrimaryKey, Base):
    """Token de refresco opaco. Solo se guarda su SHA-256.

    Todos los tokens de una misma sesión comparten `family_id`; si un token ya
    rotado se reutiliza, se revoca la familia completa (detección de robo).
    """

    __tablename__ = "refresh_tokens"
    __table_args__ = (Index("ix_refresh_tokens_user_family", "user_id", "family_id"),)

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    family_id: Mapped[uuid.UUID] = mapped_column()
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    rotated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    device_label: Mapped[str | None] = mapped_column(String(200))
    ip_address: Mapped[str | None] = mapped_column(String(45))


class TokenPurpose(enum.StrEnum):
    EMAIL_VERIFY = "email_verify"
    PASSWORD_RESET = "password_reset"  # noqa: S105  (propósito del token, no un secreto)


class UserToken(UUIDPrimaryKey, Base):
    """Token de un solo uso enviado por correo. Solo se guarda su SHA-256."""

    __tablename__ = "user_tokens"
    __table_args__ = (Index("ix_user_tokens_user_purpose", "user_id", "purpose"),)

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    purpose: Mapped[TokenPurpose] = mapped_column(
        Enum(TokenPurpose, name="token_purpose", values_callable=lambda e: [m.value for m in e])
    )
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class MfaRecoveryCode(UUIDPrimaryKey, Base):
    """Código de recuperación MFA de un solo uso (solo su SHA-256)."""

    __tablename__ = "mfa_recovery_codes"
    __table_args__ = (UniqueConstraint("user_id", "code_hash"),)

    user_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    code_hash: Mapped[str] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
