import pytest

from civia_api.security.crypto import DecryptionError, decrypt_field, encrypt_field


def test_round_trip() -> None:
    sealed = encrypt_field("JBSWY3DPEHPK3PXP", context="user:1:mfa")
    assert sealed.startswith("v1.")
    assert "JBSWY3DPEHPK3PXP" not in sealed
    assert decrypt_field(sealed, context="user:1:mfa") == "JBSWY3DPEHPK3PXP"


def test_same_plaintext_encrypts_differently() -> None:
    assert encrypt_field("x", context="c") != encrypt_field("x", context="c")


def test_value_cannot_be_moved_to_another_user() -> None:
    sealed = encrypt_field("secreto", context="user:A:mfa")
    with pytest.raises(DecryptionError):
        decrypt_field(sealed, context="user:B:mfa")


def test_tampered_ciphertext_is_rejected() -> None:
    sealed = encrypt_field("secreto", context="c")
    tampered = sealed[:-4] + ("AAAA" if not sealed.endswith("AAAA") else "BBBB")
    with pytest.raises(DecryptionError):
        decrypt_field(tampered, context="c")
