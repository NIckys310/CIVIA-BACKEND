"""Hash de contraseñas con Argon2id y política de contraseñas (ASVS 5.0 V6)."""

import hashlib

import httpx
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError

# Parámetros RFC 9106 (perfil de memoria baja): 64 MiB, 3 iteraciones, 4 hilos.
_hasher = PasswordHasher(time_cost=3, memory_cost=64 * 1024, parallelism=4)

# Hash ficticio para igualar el tiempo de respuesta cuando el usuario no existe
# (evita enumerar cuentas midiendo la latencia del login).
_DUMMY_HASH = _hasher.hash("civia-dummy-password-for-timing")

MIN_LENGTH = 12
MAX_LENGTH = 128


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password_hash: str | None, password: str) -> bool:
    try:
        return _hasher.verify(password_hash or _DUMMY_HASH, password) and password_hash is not None
    except (VerificationError, InvalidHashError):
        return False


def needs_rehash(password_hash: str) -> bool:
    return _hasher.check_needs_rehash(password_hash)


def password_policy_errors(password: str, *, email: str = "") -> list[str]:
    """Reglas de ASVS: longitud mínima 12, máximo 128, sin reglas de composición forzadas."""
    errors: list[str] = []
    if len(password) < MIN_LENGTH:
        errors.append(f"La contraseña debe tener al menos {MIN_LENGTH} caracteres.")
    if len(password) > MAX_LENGTH:
        errors.append(f"La contraseña no puede superar {MAX_LENGTH} caracteres.")
    local_part = email.split("@", 1)[0].lower()
    if len(local_part) >= 4 and local_part in password.lower():
        errors.append("La contraseña no debe contener tu correo.")
    return errors


async def is_breached_password(password: str, *, timeout_seconds: float = 3.0) -> bool:
    """Consulta Have I Been Pwned con k-anonymity: solo sale de aquí el prefijo SHA-1 de 5 hex.

    Si el servicio no responde se devuelve False (fail-open) para no bloquear registros;
    el evento se puede registrar aparte.
    """
    digest = hashlib.sha1(password.encode("utf-8"), usedforsecurity=False).hexdigest().upper()
    prefix, suffix = digest[:5], digest[5:]
    try:
        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            res = await client.get(
                f"https://api.pwnedpasswords.com/range/{prefix}", headers={"Add-Padding": "true"}
            )
            res.raise_for_status()
    except httpx.HTTPError:
        return False
    for line in res.text.splitlines():
        candidate, _, count = line.partition(":")
        if candidate == suffix and count.strip() != "0":
            return True
    return False
