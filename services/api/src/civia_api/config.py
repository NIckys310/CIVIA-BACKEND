"""Configuración de la API, leída solo de variables de entorno (nunca del repo)."""

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    environment: Literal["development", "test", "staging", "production"] = "development"

    # Rol de aplicación SIN privilegios de superusuario: así RLS se aplica siempre.
    database_url: str = "postgresql+asyncpg://civia_app:civia_app@127.0.0.1:5432/civia"
    # Rol propietario usado solo por las migraciones de Alembic.
    migrations_database_url: str | None = None

    redis_url: str | None = None

    # Clave privada Ed25519 en PEM para firmar JWT (EdDSA). En desarrollo/test,
    # si falta, se genera una efímera al arrancar (los tokens no sobreviven reinicios).
    jwt_private_key_pem: SecretStr | None = None
    jwt_issuer: str = "civia-api"
    jwt_audience: str = "civia-clients"
    access_token_ttl_seconds: int = Field(default=600, ge=60, le=3600)
    refresh_token_ttl_seconds: int = Field(default=14 * 24 * 3600, ge=3600)

    cors_origins: list[str] = ["http://localhost:3000"]

    # Comprobación de contraseñas filtradas contra HIBP (k-anonymity, solo se envía
    # el prefijo de 5 caracteres del SHA-1). Desactivable en entornos sin red.
    hibp_check_enabled: bool = True

    login_max_attempts: int = 5
    login_window_seconds: int = 900

    @property
    def is_production_like(self) -> bool:
        return self.environment in ("staging", "production")

    @model_validator(mode="after")
    def _require_secrets_outside_dev(self) -> "Settings":
        if self.is_production_like and self.jwt_private_key_pem is None:
            raise ValueError("JWT_PRIVATE_KEY_PEM es obligatorio en staging/production")
        if self.is_production_like and self.redis_url is None:
            raise ValueError("REDIS_URL es obligatorio en staging/production (rate limiting)")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
