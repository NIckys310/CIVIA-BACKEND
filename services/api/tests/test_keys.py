import base64
import secrets

import pytest

from civia_api.security.keys import decode_key32


def test_accepts_standard_and_urlsafe_base64_with_or_without_padding() -> None:
    raw = secrets.token_bytes(32)
    for text in (
        base64.b64encode(raw).decode(),
        base64.urlsafe_b64encode(raw).decode(),
        base64.urlsafe_b64encode(raw).decode().rstrip("="),
    ):
        assert decode_key32(text, name="K") == raw


def test_rejects_wrong_length_and_garbage() -> None:
    with pytest.raises(ValueError, match="32 bytes"):
        decode_key32(base64.b64encode(b"short").decode(), name="K")
    with pytest.raises(ValueError):
        decode_key32("%%%no-es-base64%%%", name="K")


def test_seed_produces_a_stable_signing_key(monkeypatch: pytest.MonkeyPatch) -> None:
    from civia_api.config import get_settings
    from civia_api.security import tokens

    seed = base64.b64encode(secrets.token_bytes(32)).decode()
    monkeypatch.setenv("JWT_SIGNING_SEED", seed)
    get_settings.cache_clear()
    tokens._keys.cache_clear()
    try:
        first = tokens._keys()[1]
        tokens._keys.cache_clear()
        assert tokens._keys()[1].public_bytes_raw() == first.public_bytes_raw()
    finally:
        monkeypatch.delenv("JWT_SIGNING_SEED")
        get_settings.cache_clear()
        tokens._keys.cache_clear()
