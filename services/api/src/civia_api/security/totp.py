"""TOTP (RFC 6238) para MFA, con protección contra reutilización de códigos.

Se acepta el paso actual ±1 (30 s de tolerancia de reloj). Cada código verificado
guarda su número de paso; un código de un paso igual o anterior se rechaza aunque siga
siendo válido en el tiempo (evita la reutilización de un código interceptado).
"""

import hashlib
import hmac
import secrets
import time

import pyotp

STEP_SECONDS = 30
VALID_WINDOW = 1
RECOVERY_CODE_COUNT = 10


def new_secret() -> str:
    return pyotp.random_base32(length=32)  # 160 bits, recomendado por RFC 4226


def provisioning_uri(secret: str, *, account: str, issuer: str) -> str:
    return pyotp.TOTP(secret).provisioning_uri(name=account, issuer_name=issuer)


def current_step(now: float | None = None) -> int:
    return int((now if now is not None else time.time()) // STEP_SECONDS)


def verify(
    secret: str, code: str, *, last_step: int | None, now: float | None = None
) -> int | None:
    """Devuelve el paso aceptado o None. Nunca acepta un paso ≤ `last_step`."""
    code = code.strip().replace(" ", "")
    if len(code) != 6 or not code.isdigit():
        return None
    totp = pyotp.TOTP(secret)
    base = current_step(now)
    for offset in range(-VALID_WINDOW, VALID_WINDOW + 1):
        step = base + offset
        if last_step is not None and step <= last_step:
            continue
        expected = totp.generate_otp(step)
        if hmac.compare_digest(expected, code):
            return step
    return None


def new_recovery_codes() -> list[str]:
    """10 códigos de un solo uso, formato legible XXXXX-XXXXX (sin caracteres ambiguos)."""
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    codes = []
    for _ in range(RECOVERY_CODE_COUNT):
        raw = "".join(secrets.choice(alphabet) for _ in range(10))
        codes.append(f"{raw[:5]}-{raw[5:]}")
    return codes


def hash_recovery_code(code: str) -> str:
    normalized = code.strip().upper().replace("-", "").replace(" ", "")
    return hashlib.sha256(normalized.encode()).hexdigest()
