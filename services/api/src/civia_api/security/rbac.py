"""Control de acceso basado en roles (RBAC). Deniega por defecto."""

import enum

from civia_api.models.identity import Role


class Permission(enum.StrEnum):
    ORG_MANAGE = "org:manage"
    MEMBERS_MANAGE = "members:manage"
    PROJECT_CREATE = "project:create"
    PROJECT_READ = "project:read"
    PROJECT_UPDATE = "project:update"
    PROJECT_DELETE = "project:delete"
    PLAN_UPLOAD = "plan:upload"
    ANALYSIS_RUN = "analysis:run"
    OBSERVATION_CREATE = "observation:create"
    REPORT_APPROVE = "report:approve"  # firma profesional: solo ingeniero responsable/admin
    AUDIT_READ = "audit:read"


_READ = {Permission.PROJECT_READ}
_CONTRIBUTE = _READ | {Permission.PLAN_UPLOAD, Permission.OBSERVATION_CREATE}
_REVIEW = _CONTRIBUTE | {Permission.ANALYSIS_RUN}
_ENGINEER = _REVIEW | {
    Permission.PROJECT_CREATE,
    Permission.PROJECT_UPDATE,
    Permission.REPORT_APPROVE,
}

ROLE_PERMISSIONS: dict[Role, frozenset[Permission]] = {
    Role.VIEWER: frozenset(_READ),
    Role.COLLABORATOR: frozenset(_CONTRIBUTE),
    Role.REVIEWER: frozenset(_REVIEW),
    Role.ENGINEER_IN_CHARGE: frozenset(_ENGINEER),
    Role.ADMIN: frozenset(Permission),
}


def has_permission(role: Role, permission: Permission) -> bool:
    return permission in ROLE_PERMISSIONS.get(role, frozenset())
