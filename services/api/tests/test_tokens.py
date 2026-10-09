import uuid

import jwt
import pytest

from civia_api.security.tokens import (
    InvalidTokenError,
    create_access_token,
    decode_access_token,
    hash_refresh_token,
    new_refresh_token,
)


def test_access_token_round_trip() -> None:
    user, sid = uuid.uuid4(), uuid.uuid4()
    token, ttl = create_access_token(user, session_id=sid)
    claims = decode_access_token(token)
    assert claims["sub"] == str(user)
    assert claims["sid"] == str(sid)
    assert 60 <= ttl <= 3600


def test_tampered_token_is_rejected() -> None:
    token, _ = create_access_token(uuid.uuid4(), session_id=uuid.uuid4())
    header, payload, signature = token.split(".")
    forged = f"{header}.{payload}.{signature[::-1]}"
    with pytest.raises(InvalidTokenError):
        decode_access_token(forged)


def test_alg_none_and_hs256_are_rejected() -> None:
    claims = {"sub": "x", "sid": "y", "aud": "civia-clients", "iss": "civia-api"}
    for token in (
        jwt.encode(claims, key=None, algorithm="none"),
        jwt.encode(claims, key="secret-guessed-by-attacker-0123456789", algorithm="HS256"),
    ):
        with pytest.raises(InvalidTokenError):
            decode_access_token(token)


def test_refresh_tokens_are_random_and_hashed() -> None:
    a, a_hash = new_refresh_token()
    b, _ = new_refresh_token()
    assert a != b
    assert len(a) >= 64
    assert a_hash == hash_refresh_token(a) != a


def test_mfa_challenge_cannot_be_used_as_access_token() -> None:
    from civia_api.security.tokens import create_mfa_challenge, decode_mfa_challenge

    challenge = create_mfa_challenge(uuid.uuid4(), device_label="Web")
    assert decode_mfa_challenge(challenge)["dev"] == "Web"
    with pytest.raises(InvalidTokenError):
        decode_access_token(challenge)


def test_access_token_cannot_be_used_as_mfa_challenge() -> None:
    from civia_api.security.tokens import decode_mfa_challenge

    token, _ = create_access_token(uuid.uuid4(), session_id=uuid.uuid4())
    with pytest.raises(InvalidTokenError):
        decode_mfa_challenge(token)
