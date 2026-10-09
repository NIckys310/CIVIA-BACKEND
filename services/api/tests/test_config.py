import pytest

from civia_api.config import Settings


def test_blank_optional_secrets_are_treated_as_unset(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in ("JWT_PRIVATE_KEY_PEM", "DATA_ENCRYPTION_KEY", "SMTP_PASSWORD", "REDIS_URL"):
        monkeypatch.setenv(name, "")
    settings = Settings(environment="development")
    assert settings.jwt_private_key_pem is None
    assert settings.data_encryption_key is None
    assert settings.smtp_password is None
    assert settings.redis_url is None


def test_production_requires_secrets() -> None:
    with pytest.raises(ValueError, match="JWT_PRIVATE_KEY_PEM"):
        Settings(environment="production", jwt_private_key_pem="", redis_url="redis://x")


def test_provider_url_is_normalized_for_asyncpg() -> None:
    from civia_api.config import normalize_database_url

    url = normalize_database_url(
        "postgres://owner:pw@ep-x.sa-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require"
    )
    assert url == "postgresql+asyncpg://owner:pw@ep-x.sa-east-1.aws.neon.tech/neondb?ssl=require"


def test_app_url_is_derived_from_migrations_url_with_escaped_password(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("DATABASE_URL", raising=False)
    settings = Settings(
        environment="development",
        migrations_database_url="postgresql://owner:pw@db.example/civia?sslmode=require",
        app_db_password="a+b/c=",
    )
    assert settings.database_url == (
        "postgresql+asyncpg://civia_app:a%2Bb%2Fc%3D@db.example/civia?ssl=require"
    )
    assert settings.migrations_database_url == (
        "postgresql+asyncpg://owner:pw@db.example/civia?ssl=require"
    )


def test_explicit_database_url_wins_over_derivation() -> None:
    settings = Settings(
        database_url="postgresql+asyncpg://x:y@h/db",
        migrations_database_url="postgresql://owner:pw@h/db",
        app_db_password="z",
    )
    assert settings.database_url == "postgresql+asyncpg://x:y@h/db"


def test_signing_seed_or_console_email_satisfy_production_requirements() -> None:
    settings = Settings(
        environment="production",
        jwt_signing_seed="A" * 43 + "=",
        redis_url="redis://x",
        data_encryption_key="A" * 43 + "=",
        allow_console_email=True,
    )
    assert settings.jwt_private_key_pem is None
    with pytest.raises(ValueError, match="EMAIL_BACKEND"):
        Settings(
            environment="production",
            jwt_signing_seed="A" * 43 + "=",
            redis_url="redis://x",
            data_encryption_key="A" * 43 + "=",
        )
