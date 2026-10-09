"""Decodificación de secretos de 32 bytes entregados en base64 (estándar o URL-safe)."""

import base64
import binascii


def decode_key32(value: str, *, name: str) -> bytes:
    """Acepta base64 estándar (`+/`) o URL-safe (`-_`), con o sin relleno `=`."""
    text = value.strip().replace("+", "-").replace("/", "_")
    text += "=" * (-len(text) % 4)
    try:
        raw = base64.urlsafe_b64decode(text)
    except (binascii.Error, ValueError) as exc:
        raise ValueError(f"{name} no es base64 válido") from exc
    if len(raw) != 32:
        raise ValueError(f"{name} debe tener 32 bytes (tiene {len(raw)})")
    return raw
