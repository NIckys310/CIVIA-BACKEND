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
