# 0005 — Autenticación: JWT EdDSA de vida corta + refresh rotativo

- Estado: aceptado
- Fecha: 2026-10-08

## Decisión
- Access token **JWT EdDSA (Ed25519)**, 10 min, solo en memoria del cliente. Lleva `sid`
  (familia de sesión); cada petición verifica que la familia siga activa → el cierre de
  sesión remoto surte efecto de inmediato.
- Refresh token **opaco** (48 bytes aleatorios), guardado como SHA-256, rotado en cada uso.
  Reutilizar uno ya rotado (pasado un margen de 10 s para pestañas concurrentes) revoca
  toda la familia.
- Web: refresh en cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` + cabecera
  anti-CSRF. Móvil: refresh en Keychain/Keystore, enviado en el cuerpo con `X-Client: mobile`.
- Argon2id (RFC 9106, 64 MiB / t=3 / p=4) con rehash automático al cambiar parámetros.
- Roles como enum en Fase 0 (Admin, Ingeniero responsable, Revisor, Colaborador, Lector),
  matriz de permisos con denegación por defecto.

## Consecuencias
- La clave privada JWT es obligatoria en staging/producción (la app no arranca sin ella);
  rotación planificada con `kid` y gestor de secretos (KMS/Vault) antes de producción.
- MFA TOTP y verificación de correo se añaden en Fase 1 sobre este mismo modelo.
