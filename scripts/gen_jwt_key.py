"""Genera una clave Ed25519 en PEM para JWT_PRIVATE_KEY_PEM (una por entorno).

Uso: python scripts/gen_jwt_key.py
Copia la salida a tu gestor de secretos o a .env (nunca al repositorio).
"""

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

pem = Ed25519PrivateKey.generate().private_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PrivateFormat.PKCS8,
    encryption_algorithm=serialization.NoEncryption(),
)
# En una sola línea con \n escapados para que quepa en un archivo .env.
print('JWT_PRIVATE_KEY_PEM="' + pem.decode().strip().replace("\n", "\\n") + '"')
