"""Actividad reciente de la organización activa (lectura del audit_log bajo RLS)."""

from typing import Annotated

from fastapi import APIRouter, Depends, Query
from sqlalchemy import or_, select

from civia_api.api.deps import OrgContext, SessionDep, require
from civia_api.models import AuditLog, User
from civia_api.schemas import ActivityOut
from civia_api.security.rbac import Permission

router = APIRouter(prefix="/activity", tags=["activity"])

CanRead = Annotated[OrgContext, Depends(require(Permission.PROJECT_READ))]

# Solo eventos de trabajo; los eventos de seguridad se consultan aparte (permiso audit:read).
VISIBLE_PREFIXES = ("project.", "plan.", "analysis.", "observation.", "report.")
VISIBLE_ACTIONS = ("auth.register",)


@router.get("", response_model=list[ActivityOut])
async def recent_activity(
    session: SessionDep, ctx: CanRead, limit: Annotated[int, Query(ge=1, le=50)] = 10
) -> list[ActivityOut]:
    rows = await session.execute(
        select(
            AuditLog.seq,
            AuditLog.occurred_at,
            AuditLog.action,
            User.full_name,
            AuditLog.target_type,
            AuditLog.target_id,
        )
        .outerjoin(User, User.id == AuditLog.actor_user_id)
        .where(AuditLog.organization_id == ctx.organization_id)
        .where(
            or_(
                *(AuditLog.action.startswith(p) for p in VISIBLE_PREFIXES),
                AuditLog.action.in_(VISIBLE_ACTIONS),
            )
        )
        .order_by(AuditLog.seq.desc())
        .limit(limit)
    )
    return [
        ActivityOut(
            seq=seq, occurred_at=at, action=action, actor_name=name, target_type=tt, target_id=tid
        )
        for seq, at, action, name, tt, tid in rows.all()
    ]
