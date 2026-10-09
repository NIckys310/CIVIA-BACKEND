"""Proyectos de obra y sus miembros. Tablas aisladas por organización (RLS)."""

import uuid

from sqlalchemy import ForeignKey, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from civia_api.db.base import Base, SoftDelete, Timestamps, UUIDPrimaryKey
from civia_api.models.identity import Role, role_enum


class Project(UUIDPrimaryKey, Timestamps, SoftDelete, Base):
    __tablename__ = "projects"
    __table_args__ = (UniqueConstraint("organization_id", "code"),)

    organization_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("organizations.id", ondelete="CASCADE"), index=True
    )
    code: Mapped[str] = mapped_column(String(40))  # p. ej. "PRJ-0001"
    name: Mapped[str] = mapped_column(String(200))
    description: Mapped[str | None] = mapped_column(Text)
    location: Mapped[str | None] = mapped_column(String(200))
    status: Mapped[str] = mapped_column(String(20), default="active")
    # Normativa con la que se revisa el proyecto (trazabilidad de cada verificación).
    norm_code: Mapped[str] = mapped_column(String(40), default="NSR-10")
    norm_version: Mapped[str] = mapped_column(String(40), default="2010")
    created_by: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id"))


class ProjectMember(UUIDPrimaryKey, Timestamps, Base):
    __tablename__ = "project_members"
    __table_args__ = (UniqueConstraint("project_id", "user_id"),)

    organization_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("organizations.id", ondelete="CASCADE"), index=True
    )
    project_id: Mapped[uuid.UUID] = mapped_column(
        ForeignKey("projects.id", ondelete="CASCADE"), index=True
    )
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    role: Mapped[Role] = mapped_column(role_enum)
