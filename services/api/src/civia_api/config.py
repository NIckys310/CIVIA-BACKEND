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
    # Bloqueo de cuenta persistente (independiente del rate limit por IP/correo).
    lockout_threshold: int = 8
    lockout_seconds: int = 900

    # Clave AES-256 (32 bytes en base64 urlsafe) para cifrar en reposo secretos de usuario
    # como la semilla TOTP. En desarrollo, si falta, se usa una efímera (los MFA activados
    # dejan de validar al reiniciar). Genera con scripts/gen_data_key.py.
    data_encryption_key: SecretStr | None = None

    # Enlaces en correos (verificación, recuperación de contraseña).
    web_base_url: str = "http://localhost:3000"
    mfa_issuer: str = "CIVIA AI"

    # Correo saliente: "console" (desarrollo: se escribe en el log) o "smtp".
    email_backend: Literal["console", "smtp"] = "console"
    email_from: str = "CIVIA <no-reply@civia.example>"
    smtp_host: str | None = None
    smtp_port: int = 587
    smtp_username: str | None = None
    smtp_password: SecretStr | None = None

    @property
    def is_production_like(self) -> bool:
        return self.environment in ("staging", "production")

    @model_validator(mode="after")
    def _require_secrets_outside_dev(self) -> "Settings":
        if self.is_production_like and self.jwt_private_key_pem is None:
            raise ValueError("JWT_PRIVATE_KEY_PEM es obligatorio en staging/production")
        if self.is_production_like and self.redis_url is None:
            raise ValueError("REDIS_URL es obligatorio en staging/production (rate limiting)")
        if self.is_production_like and self.data_encryption_key is None:
            raise ValueError("DATA_ENCRYPTION_KEY es obligatorio en staging/production")
        if self.is_production_like and self.email_backend != "smtp":
            raise ValueError("EMAIL_BACKEND=smtp es obligatorio en staging/production")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
