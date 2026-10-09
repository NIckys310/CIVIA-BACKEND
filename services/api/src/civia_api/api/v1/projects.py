"""Proyectos de la organización activa (cabecera X-Organization-Id)."""

import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy import func, select

from civia_api.api.deps import OrgContext, SessionDep, client_ip, require
from civia_api.models import Project, ProjectMember
from civia_api.schemas import ProjectIn, ProjectOut
from civia_api.security.rbac import Permission
from civia_api.services.audit import record_audit

router = APIRouter(prefix="/projects", tags=["projects"])

CanRead = Annotated[OrgContext, Depends(require(Permission.PROJECT_READ))]
CanCreate = Annotated[OrgContext, Depends(require(Permission.PROJECT_CREATE))]


@router.get("", response_model=list[ProjectOut])
async def list_projects(session: SessionDep, ctx: CanRead) -> list[ProjectOut]:
    # RLS ya limita a la organización activa; el filtro explícito es defensa en profundidad.
    projects = await session.scalars(
        select(Project)
        .where(Project.organization_id == ctx.organization_id, Project.deleted_at.is_(None))
        .order_by(Project.updated_at.desc())
    )
    return [ProjectOut.model_validate(p) for p in projects]


@router.get("/{project_id}", response_model=ProjectOut)
async def get_project(project_id: uuid.UUID, session: SessionDep, ctx: CanRead) -> ProjectOut:
    project = await session.scalar(
        select(Project).where(
            Project.id == project_id,
            Project.organization_id == ctx.organization_id,
            Project.deleted_at.is_(None),
        )
    )
    if project is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Proyecto no encontrado.")
    return ProjectOut.model_validate(project)


@router.post("", response_model=ProjectOut, status_code=status.HTTP_201_CREATED)
async def create_project(
    data: ProjectIn, request: Request, session: SessionDep, ctx: CanCreate
) -> ProjectOut:
    count = await session.scalar(
        select(func.count())
        .select_from(Project)
        .where(Project.organization_id == ctx.organization_id)
    )
    project = Project(
        organization_id=ctx.organization_id,
        code=f"PRJ-{(count or 0) + 1:04d}",
        name=data.name.strip(),
        description=data.description,
        location=data.location,
        created_by=ctx.user.id,
    )
    session.add(project)
    await session.flush()
    session.add(
        ProjectMember(
            organization_id=ctx.organization_id,
            project_id=project.id,
            user_id=ctx.user.id,
            role=ctx.role,
        )
    )
    await record_audit(
        session,
        "project.created",
        organization_id=ctx.organization_id,
        actor_user_id=ctx.user.id,
        target_type="project",
        target_id=str(project.id),
        ip_address=client_ip(request),
    )
    await session.refresh(project)
    return ProjectOut.model_validate(project)
