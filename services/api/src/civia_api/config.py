"""Configuración de la API, leída solo de variables de entorno (nunca del repo)."""

from functools import lru_cache
from typing import Literal
from urllib.parse import parse_qsl, quote, urlencode, urlsplit, urlunsplit

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# Parámetros de libpq que asyncpg no entiende (los proveedores como Neon los incluyen).
_LIBPQ_ONLY_PARAMS = {"sslmode", "channel_binding", "options"}


def normalize_database_url(url: str) -> str:
    """Acepta la URL tal como la entrega el proveedor (postgres://…?sslmode=require) y la
    convierte al formato de SQLAlchemy + asyncpg (postgresql+asyncpg://…?ssl=require)."""
    parts = urlsplit(url)
    scheme = parts.scheme
    if scheme in ("postgres", "postgresql", "postgresql+psycopg"):
        scheme = "postgresql+asyncpg"
    query = dict(parse_qsl(parts.query))
    sslmode = query.get("sslmode")
    query = {k: v for k, v in query.items() if k not in _LIBPQ_ONLY_PARAMS}
    if sslmode in ("require", "verify-ca", "verify-full") and "ssl" not in query:
        query["ssl"] = "require" if sslmode == "require" else sslmode
    return urlunsplit((scheme, parts.netloc, parts.path, urlencode(query), parts.fragment))


def with_credentials(url: str, user: str, password: str) -> str:
    """Misma base de datos y host, otro usuario (el rol civia_app sin privilegios)."""
    parts = urlsplit(url)
    host = parts.netloc.rsplit("@", 1)[-1]
    netloc = f"{quote(user, safe='')}:{quote(password, safe='')}@{host}"
    return urlunsplit((parts.scheme, netloc, parts.path, parts.query, parts.fragment))


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    environment: Literal["development", "test", "staging", "production"] = "development"

    # Rol de aplicación SIN privilegios de superusuario: así RLS se aplica siempre.
    database_url: str = "postgresql+asyncpg://civia_app:civia_app@127.0.0.1:5432/civia"
    # Rol propietario usado solo por las migraciones de Alembic.
    migrations_database_url: str | None = None
    # Si se define y DATABASE_URL no, la URL de la API se deriva de la de migraciones
    # cambiando el usuario por civia_app (despliegues donde solo hay una URL del proveedor).
    app_db_password: SecretStr | None = None

    redis_url: str | None = None

    # Clave privada Ed25519 en PEM para firmar JWT (EdDSA). En desarrollo/test,
    # si falta, se genera una efímera al arrancar (los tokens no sobreviven reinicios).
    jwt_private_key_pem: SecretStr | None = None
    # Alternativa al PEM: semilla Ed25519 de 32 bytes en base64 (p. ej. generada por la
    # plataforma de despliegue). Se ignora si hay JWT_PRIVATE_KEY_PEM.
    jwt_signing_seed: SecretStr | None = None
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
    # Solo para demos públicas sin servidor de correo: los correos se escriben en el log.
    # Desactiva la exigencia de SMTP en staging/production. Nunca en producción real.
    allow_console_email: bool = False

    @field_validator(
        "migrations_database_url",
        "app_db_password",
        "redis_url",
        "jwt_private_key_pem",
        "jwt_signing_seed",
        "data_encryption_key",
        "smtp_host",
        "smtp_username",
        "smtp_password",
        mode="before",
    )
    @classmethod
    def _blank_is_unset(cls, value: object) -> object:
        """`CLAVE=` vacío en .env significa "no definido", no un secreto vacío."""
        return None if isinstance(value, str) and not value.strip() else value

    @property
    def is_production_like(self) -> bool:
        return self.environment in ("staging", "production")

    @model_validator(mode="after")
    def _resolve_database_urls(self) -> "Settings":
        if self.migrations_database_url:
            self.migrations_database_url = normalize_database_url(self.migrations_database_url)
            if self.app_db_password and "database_url" not in self.model_fields_set:
                self.database_url = with_credentials(
                    self.migrations_database_url,
                    "civia_app",
                    self.app_db_password.get_secret_value(),
                )
        self.database_url = normalize_database_url(self.database_url)
        return self

    @model_validator(mode="after")
    def _require_secrets_outside_dev(self) -> "Settings":
        if self.is_production_like and not (self.jwt_private_key_pem or self.jwt_signing_seed):
            raise ValueError(
                "JWT_PRIVATE_KEY_PEM o JWT_SIGNING_SEED es obligatorio en staging/production"
            )
        if self.is_production_like and self.redis_url is None:
            raise ValueError("REDIS_URL es obligatorio en staging/production (rate limiting)")
        if self.is_production_like and self.data_encryption_key is None:
            raise ValueError("DATA_ENCRYPTION_KEY es obligatorio en staging/production")
        if (
            self.is_production_like
            and self.email_backend != "smtp"
            and not self.allow_console_email
        ):
            raise ValueError("EMAIL_BACKEND=smtp es obligatorio en staging/production")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
