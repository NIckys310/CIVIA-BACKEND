"""Perfil del usuario autenticado y sus organizaciones."""

from fastapi import APIRouter
from sqlalchemy import select

from civia_api.api.deps import CurrentUserDep, SessionDep
from civia_api.models import Membership, Organization
from civia_api.schemas import MembershipOut, MeOut, UserOut

router = APIRouter(tags=["me"])


@router.get("/me", response_model=MeOut)
async def me(session: SessionDep, current: CurrentUserDep) -> MeOut:
    rows = await session.execute(
        select(Membership.organization_id, Organization.name, Organization.country_code, Membership.role)
        .join(Organization, Organization.id == Membership.organization_id)
        .where(Membership.user_id == current.user.id, Organization.deleted_at.is_(None))
        .order_by(Organization.name)
    )
    return MeOut(
        user=UserOut.model_validate(current.user),
        memberships=[
            MembershipOut(organization_id=oid, organization_name=name, country_code=cc, role=role)
            for oid, name, cc, role in rows.all()
        ],
    )
