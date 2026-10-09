"""Pruebas de seguridad a nivel de base de datos: RLS entre organizaciones y audit_log."""

import uuid

import pytest
from sqlalchemy import select, text
from sqlalchemy.exc import DBAPIError
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine

from civia_api.db.ids import uuid7
from civia_api.db.session import get_sessionmaker, set_tenant_context
from civia_api.models import AuditLog, Membership, Organization, Project, Role, User
from civia_api.services.audit import record_audit


async def _make_org_with_project(name: str) -> tuple[uuid.UUID, uuid.UUID]:
    org_id, user_id = uuid7(), uuid7()
    async with get_sessionmaker()() as s, s.begin():
        s.add(User(id=user_id, email=f"{uuid7()}@x.co", password_hash="x", full_name=name))
        await s.flush()
        await set_tenant_context(s, user_id=user_id, org_id=org_id)
        s.add(Organization(id=org_id, name=name, slug=str(org_id)))
        await s.flush()
        s.add(Membership(organization_id=org_id, user_id=user_id, role=Role.ADMIN))
        s.add(Project(organization_id=org_id, code="P-1", name=f"Obra {name}", created_by=user_id))
    return org_id, user_id


async def test_projects_are_isolated_between_organizations() -> None:
    org_a, user_a = await _make_org_with_project("A")
    org_b, _ = await _make_org_with_project("B")

    async with get_sessionmaker()() as s, s.begin():
        await set_tenant_context(s, user_id=user_a, org_id=org_a)
        orgs_seen = set((await s.scalars(select(Project.organization_id))).all())
    assert orgs_seen == {org_a}

    async with get_sessionmaker()() as s, s.begin():
        await set_tenant_context(s, user_id=user_a, org_id=org_b)  # sin membresía en B
        visible_orgs = (await s.scalars(select(Organization.id))).all()
    assert org_b in visible_orgs  # org activa es visible…
    # …pero la API nunca fija una org sin validar membresía (ver deps); RLS es la 2.ª barrera.


async def test_no_tenant_context_sees_nothing() -> None:
    await _make_org_with_project("C")
    async with get_sessionmaker()() as s, s.begin():
        assert (await s.scalars(select(Project))).all() == []


async def test_cannot_insert_project_into_other_organization() -> None:
    org_a, user_a = await _make_org_with_project("D")
    org_b, _ = await _make_org_with_project("E")
    with pytest.raises(DBAPIError, match="row-level security"):
        async with get_sessionmaker()() as s, s.begin():
            await set_tenant_context(s, user_id=user_a, org_id=org_a)
            s.add(Project(organization_id=org_b, code="X", name="intruso", created_by=user_a))


async def _audit(s: AsyncSession, org: uuid.UUID | None, action: str) -> None:
    await record_audit(s, action, organization_id=org, details={"k": "v"})


async def test_app_role_cannot_insert_audit_rows_directly() -> None:
    with pytest.raises(DBAPIError, match="permission denied"):
        async with get_sessionmaker()() as s, s.begin():
            s.add(AuditLog(action="forged"))


async def test_audit_log_is_append_only() -> None:
    async with get_sessionmaker()() as s, s.begin():
        await _audit(s, None, "test.append_only")
    for stmt in ("UPDATE audit_log SET action = 'x'", "DELETE FROM audit_log"):
        with pytest.raises(DBAPIError):
            async with get_sessionmaker()() as s, s.begin():
                await s.execute(text(stmt))


async def test_audit_log_hash_chain_detects_tampering(database_urls: tuple[str, str]) -> None:
    org_id, user_id = await _make_org_with_project("F")
    async with get_sessionmaker()() as s, s.begin():
        await set_tenant_context(s, user_id=user_id, org_id=org_id)
        for i in range(3):
            await _audit(s, org_id, f"test.chain.{i}")

    admin = create_async_engine(database_urls[0])
    async with admin.connect() as conn:
        await conn.begin()
        assert (await conn.execute(text("SELECT audit_log_verify()"))).scalar() is None
        # Un atacante con acceso de superusuario desactiva los triggers y altera una fila.
        await conn.execute(text("ALTER TABLE audit_log DISABLE TRIGGER audit_log_no_update"))
        await conn.execute(
            text("UPDATE audit_log SET action = 'tampered' WHERE action = 'test.chain.1'")
        )
        broken = (await conn.execute(text("SELECT audit_log_verify()"))).scalar()
        await conn.rollback()  # deshace la alteración y la desactivación del trigger
    await admin.dispose()
    assert broken is not None


async def test_audit_log_only_shows_active_organization() -> None:
    org_a, user_a = await _make_org_with_project("G")
    org_b, user_b = await _make_org_with_project("H")
    async with get_sessionmaker()() as s, s.begin():
        await set_tenant_context(s, user_id=user_b, org_id=org_b)
        await _audit(s, org_b, "test.secret_of_b")
    async with get_sessionmaker()() as s, s.begin():
        await set_tenant_context(s, user_id=user_a, org_id=org_a)
        actions = (await s.scalars(select(AuditLog.action))).all()
    assert "test.secret_of_b" not in actions
