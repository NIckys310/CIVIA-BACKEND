from civia_api.security.passwords import (
    hash_password,
    password_policy_errors,
    verify_password,
)


def test_hash_is_argon2id_and_verifies() -> None:
    h = hash_password("viga-columna-zapata")
    assert h.startswith("$argon2id$")
    assert verify_password(h, "viga-columna-zapata")
    assert not verify_password(h, "otra-contraseña-123")


def test_verify_with_missing_hash_is_false() -> None:
    assert not verify_password(None, "civia-dummy-password-for-timing")


def test_policy_requires_min_length() -> None:
    assert password_policy_errors("corta")
    assert password_policy_errors("suficientemente-larga") == []


def test_policy_rejects_password_containing_email() -> None:
    assert password_policy_errors("ingeniera2026!!", email="ingeniera@obra.co")
