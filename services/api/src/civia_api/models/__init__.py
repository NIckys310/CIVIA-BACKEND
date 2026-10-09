"""Importa todos los modelos para que Alembic los registre en `Base.metadata`."""

from civia_api.models.audit import AuditLog
from civia_api.models.identity import (
    Membership,
    MfaRecoveryCode,
    Organization,
    RefreshToken,
    Role,
    TokenPurpose,
    User,
    UserToken,
)
from civia_api.models.projects import Project, ProjectMember

__all__ = [
    "AuditLog",
    "Membership",
    "MfaRecoveryCode",
    "Organization",
    "Project",
    "ProjectMember",
    "RefreshToken",
    "Role",
    "TokenPurpose",
    "User",
    "UserToken",
]
