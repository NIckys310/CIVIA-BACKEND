"""Cifrado de campos sensibles en reposo con AES-256-GCM.

Formato: "v1." + base64url(nonce de 12 bytes || texto cifrado + tag).
`context` se usa como dato asociado (AAD): un valor cifrado para el usuario A no se puede
copiar en la fila del usuario B, porque el descifrado falla al no coincidir el contexto.
"""

import base64
import logging
import secrets
from functools import lru_cache

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from civia_api.config import get_settings
from civia_api.security.keys import decode_key32

log = logging.getLogger(__name__)
_PREFIX = "v1."


class DecryptionError(Exception):
    pass


@lru_cache
def _aead() -> AESGCM:
    configured = get_settings().data_encryption_key
    if configured is None:
        log.warning("DATA_ENCRYPTION_KEY no definido: clave efímera (solo desarrollo)")
        return AESGCM(AESGCM.generate_key(bit_length=256))
    return AESGCM(decode_key32(configured.get_secret_value(), name="DATA_ENCRYPTION_KEY"))


def encrypt_field(plaintext: str, *, context: str) -> str:
    nonce = secrets.token_bytes(12)
    sealed = _aead().encrypt(nonce, plaintext.encode(), context.encode())
    return _PREFIX + base64.urlsafe_b64encode(nonce + sealed).decode()


def decrypt_field(token: str, *, context: str) -> str:
    if not token.startswith(_PREFIX):
        raise DecryptionError("Formato de campo cifrado desconocido")
    raw = base64.urlsafe_b64decode(token[len(_PREFIX) :])
    try:
        return _aead().decrypt(raw[:12], raw[12:], context.encode()).decode()
    except InvalidTag as exc:
        raise DecryptionError("No se pudo descifrar (clave o contexto incorrectos)") from exc
