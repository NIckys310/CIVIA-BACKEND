"""Esquemas Pydantic de entrada/salida de la API v1 (contrato OpenAPI)."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field

from civia_api.models.identity import Role


class _Out(BaseModel):
    model_config = ConfigDict(from_attributes=True)


# --- Auth ---------------------------------------------------------------------------


class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=256)
    full_name: str = Field(min_length=2, max_length=160)
    organization_name: str = Field(min_length=2, max_length=160)


class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=256)
    device_label: str | None = Field(default=None, max_length=200)


class RefreshIn(BaseModel):
    """Solo clientes móviles envían el token en el cuerpo; la web usa cookie HttpOnly."""

    refresh_token: str | None = Field(default=None, max_length=200)


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int
    refresh_token: str | None = None


class SessionOut(BaseModel):
    id: uuid.UUID
    device_label: str | None
    ip_address: str | None
    started_at: datetime
    last_seen_at: datetime
    current: bool


# --- Usuario / organizaciones -------------------------------------------------------


class UserOut(_Out):
    id: uuid.UUID
    email: str
    full_name: str
    locale: str


class MembershipOut(BaseModel):
    organization_id: uuid.UUID
    organization_name: str
    country_code: str
    role: Role


class MeOut(BaseModel):
    user: UserOut
    memberships: list[MembershipOut]


# --- Proyectos ----------------------------------------------------------------------


class ProjectIn(BaseModel):
    name: str = Field(min_length=2, max_length=200)
    description: str | None = Field(default=None, max_length=4000)
    location: str | None = Field(default=None, max_length=200)


class ProjectOut(_Out):
    id: uuid.UUID
    code: str
    name: str
    description: str | None
    location: str | None
    status: str
    norm_code: str
    norm_version: str
    created_at: datetime
    updated_at: datetime
