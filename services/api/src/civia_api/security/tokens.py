"""Tokens de acceso (JWT EdDSA de vida corta) y tokens de refresco opacos."""

import hashlib
import logging
import secrets
import uuid
from datetime import UTC, datetime, timedelta
from functools import lru_cache
from typing import Any

import jwt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

from civia_api.config import get_settings

log = logging.getLogger(__name__)
ALGORITHM = "EdDSA"
TYP_ACCESS = "access"
TYP_MFA_CHALLENGE = "mfa_challenge"
MFA_CHALLENGE_TTL_SECONDS = 300


class InvalidTokenError(Exception):
    pass


@lru_cache
def _keys() -> tuple[Ed25519PrivateKey, Ed25519PublicKey]:
    settings = get_settings()
    if settings.jwt_private_key_pem is not None:
        key = serialization.load_pem_private_key(
            settings.jwt_private_key_pem.get_secret_value().encode(), password=None
        )
        if not isinstance(key, Ed25519PrivateKey):
            raise ValueError("JWT_PRIVATE_KEY_PEM debe ser una clave Ed25519")
    else:
        log.warning("JWT_PRIVATE_KEY_PEM no definido: usando clave efímera (solo desarrollo)")
        key = Ed25519PrivateKey.generate()
    return key, key.public_key()


def create_access_token(user_id: uuid.UUID, *, session_id: uuid.UUID) -> tuple[str, int]:
    """Devuelve (token, segundos de vida). `sid` enlaza el token con su familia de refresco."""
    settings = get_settings()
    now = datetime.now(UTC)
    ttl = settings.access_token_ttl_seconds
    claims: dict[str, Any] = {
        "sub": str(user_id),
        "sid": str(session_id),
        "iss": settings.jwt_issuer,
        "aud": settings.jwt_audience,
        "iat": now,
        "nbf": now,
        "exp": now + timedelta(seconds=ttl),
        "jti": secrets.token_hex(8),
        "typ": TYP_ACCESS,
    }
    return jwt.encode(claims, _keys()[0], algorithm=ALGORITHM), ttl


def _decode(token: str, *, typ: str, required: list[str]) -> dict[str, Any]:
    settings = get_settings()
    try:
        claims: dict[str, Any] = jwt.decode(
            token,
            _keys()[1],
            algorithms=[ALGORITHM],  # lista fija: impide ataques de confusión de algoritmo
            audience=settings.jwt_audience,
            issuer=settings.jwt_issuer,
            options={"require": ["exp", "iat", "sub", "aud", "iss", "typ", *required]},
        )
    except jwt.PyJWTError as exc:
        raise InvalidTokenError(str(exc)) from exc
    # Un token de un tipo nunca sirve para otro (p. ej. desafío MFA usado como acceso).
    if claims.get("typ") != typ:
        raise InvalidTokenError("Tipo de token incorrecto")
    return claims


def decode_access_token(token: str) -> dict[str, Any]:
    return _decode(token, typ=TYP_ACCESS, required=["sid"])


def create_mfa_challenge(user_id: uuid.UUID, *, device_label: str | None) -> str:
    """Token de corta vida que prueba que la contraseña ya fue verificada; solo canjeable
    en /auth/mfa/verify junto con un código TOTP o de recuperación."""
    settings = get_settings()
    now = datetime.now(UTC)
    claims: dict[str, Any] = {
        "sub": str(user_id),
        "iss": settings.jwt_issuer,
        "aud": settings.jwt_audience,
        "iat": now,
        "exp": now + timedelta(seconds=MFA_CHALLENGE_TTL_SECONDS),
        "jti": secrets.token_hex(8),
        "typ": TYP_MFA_CHALLENGE,
        "dev": device_label,
    }
    return jwt.encode(claims, _keys()[0], algorithm=ALGORITHM)


def decode_mfa_challenge(token: str) -> dict[str, Any]:
    return _decode(token, typ=TYP_MFA_CHALLENGE, required=[])


def new_refresh_token() -> tuple[str, str]:
    """Devuelve (token en claro para el cliente, SHA-256 para guardar en BD)."""
    token = secrets.token_urlsafe(48)
    return token, hash_refresh_token(token)


def hash_refresh_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()
