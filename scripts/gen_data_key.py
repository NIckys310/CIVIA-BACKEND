"""Genera una clave AES-256 para DATA_ENCRYPTION_KEY (una por entorno).

Uso: python scripts/gen_data_key.py
Guárdala en el gestor de secretos o en .env (nunca en el repositorio). Si se pierde,
los secretos cifrados con ella (p. ej. semillas MFA) no se pueden recuperar.
"""

import base64
import secrets

print("DATA_ENCRYPTION_KEY=" + base64.urlsafe_b64encode(secrets.token_bytes(32)).decode())
