"""Escritura de eventos de auditoría (vía la función SECURITY DEFINER `audit_write`)."""

import json
import uuid
from typing import Any

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession


async def record_audit(
    session: AsyncSession,
    action: str,
    *,
    organization_id: uuid.UUID | None = None,
    actor_user_id: uuid.UUID | None = None,
    target_type: str | None = None,
    target_id: str | None = None,
    ip_address: str | None = None,
    details: dict[str, Any] | None = None,
) -> None:
    """Registra un evento. `details` nunca debe contener secretos ni contraseñas."""
    await session.execute(
        text(
            "SELECT audit_write(:org, :actor, :action, :ttype, :tid, :ip, CAST(:details AS jsonb))"
        ),
        {
            "org": organization_id,
            "actor": actor_user_id,
            "action": action,
            "ttype": target_type,
            "tid": target_id,
            "ip": ip_address,
            "details": json.dumps(details or {}),
        },
    )
