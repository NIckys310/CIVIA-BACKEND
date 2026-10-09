# 0008 — MFA TOTP, bloqueo de cuenta y recuperación

- Estado: aceptado
- Fecha: 2026-10-09

## Contexto
Los planos son propiedad intelectual de los clientes y las aprobaciones del ingeniero
responsable tienen valor legal. Una contraseña robada no debe bastar para entrar.

## Decisión
- **TOTP (RFC 6238)** con `pyotp`: semilla de 160 bits cifrada con AES-256-GCM usando como
  dato asociado el id del usuario; ventana ±1 paso; se guarda el último paso aceptado y
  nunca se acepta uno igual o anterior (anti-reutilización).
- **Login en dos pasos:** la contraseña correcta devuelve un `mfa_token` (JWT EdDSA, 5 min,
  `typ=mfa_challenge`). Todos los JWT llevan `typ` y se validan estrictamente, así un desafío
  nunca sirve como token de acceso.
- **10 códigos de recuperación** de un solo uso (SHA-256 en BD), mostrados una sola vez.
- **Bloqueo persistente** tras 8 fallos (contraseña o MFA) durante 15 min, con aviso por
  correo; el mensaje es igual al del rate limit para no revelar si la cuenta existe.
- **Enlaces por correo** (verificación, recuperación) de un solo uso y con vencimiento;
  restablecer la contraseña cierra todas las sesiones; cambiarla cierra las demás.
- WebAuthn/passkeys se evaluará después: requiere dominio definitivo.

## Consecuencias
- `DATA_ENCRYPTION_KEY` pasa a ser obligatoria en staging/producción; si se pierde, los
  usuarios con MFA deben usar códigos de recuperación y volver a enrolarse.
- El correo saliente (`EMAIL_BACKEND=smtp`) es obligatorio fuera de desarrollo.
