# Modelo de amenazas (STRIDE) — Fase 0

Alcance: autenticación, multi-tenant, API, web y app móvil. Se revisa al cerrar cada fase.
Estándares: OWASP ASVS 5.0 (L2), MASVS, API Security Top 10, Top 10 for LLM Apps (Fase 1).

## Activos

1. Planos y documentos de clientes (propiedad intelectual) — **crítico**.
2. Credenciales y sesiones de usuarios.
3. Resultados de revisión y su trazabilidad (valor legal: Ley 400/1997, Ley 1796/2016).
4. Datos personales (Ley 1581 de 2012).

## Amenazas y controles

| STRIDE | Amenaza | Control implementado | Verificación |
|---|---|---|---|
| **S** Suplantación | Fuerza bruta / credential stuffing | Rate limit por IP y por correo (Redis), Argon2id (64 MiB), tiempo constante con hash ficticio, mensaje genérico | `test_login_is_rate_limited`, `test_wrong_password_and_unknown_user_look_identical` |
| S | Robo de refresh token | Rotación en cada uso + detección de reutilización → se revoca la familia completa; token opaco guardado como SHA-256 | `test_refresh_rotation_and_reuse_detection` |
| S | Falsificación de JWT | EdDSA (Ed25519), lista fija de algoritmos (`none`/HS256 rechazados), `aud`/`iss`/`exp` obligatorios | `test_alg_none_and_hs256_are_rejected` |
| S | Contraseñas filtradas | Consulta HIBP con k-anonymity (solo 5 hex del SHA-1 salen del servidor) | `is_breached_password` |
| S | Robo de contraseña | MFA TOTP opcional (semilla cifrada AES-256-GCM, anti-reutilización por paso, 10 códigos de recuperación de un solo uso); desafío MFA de 5 min con `typ` propio que no sirve como token de acceso | `test_mfa_login_flow_with_totp_and_replay_protection`, `test_mfa_challenge_cannot_be_used_as_access_token` |
| S | Fuerza bruta distribuida (evade el rate limit por IP) | Bloqueo de cuenta persistente en BD tras 8 fallos (contraseña o MFA) + correo de aviso | `test_account_locks_after_repeated_failures` |
| S | Toma de cuenta por recuperación | Enlace de un solo uso (SHA-256 en BD, 30 min), respuesta idéntica exista o no la cuenta, cierra TODAS las sesiones | `test_forgot_password_is_generic_and_reset_revokes_sessions` |
| **T** Manipulación | Alterar auditoría | `audit_log` append-only por trigger + cadena SHA-256 + `audit_write()` como única vía | `test_audit_log_hash_chain_detects_tampering`, `test_app_role_cannot_insert_audit_rows_directly` |
| T | CSRF en refresco con cookie | Cookie `SameSite=Strict; HttpOnly; Secure; Path=/api/v1/auth` + cabecera `X-Requested-With` obligatoria (fuerza preflight CORS) | `test_web_refresh_uses_httponly_cookie_and_requires_csrf_header` |
| T | XSS en la web | CSP con nonce por petición + `strict-dynamic`, React escapa por defecto, sin `dangerouslySetInnerHTML` salvo script de tema con nonce | Cabeceras en `src/proxy.ts` |
| R | Uso no detectado de la cuenta | Aviso por correo de dispositivo nuevo, historial de seguridad propio (`audit_read_own`) | `test_new_device_alert_and_security_history` |
| **R** Repudio | Negar una acción | Auditoría encadenada con actor, IP y objetivo; login, logout, revocaciones, creación de proyectos | `audit_log_verify()` |
| **I** Divulgación | Acceso entre organizaciones (IDOR) | Membresía validada antes de fijar tenant (404) + RLS `FORCE` con rol sin privilegios | `test_projects_are_isolated_*`, `test_cannot_insert_project_into_other_organization` |
| I | Token en almacenamiento del navegador | Access token solo en memoria; refresh en cookie HttpOnly; nunca localStorage | `packages/api-client` |
| I | Token en el móvil | Keychain/Keystore `WHEN_UNLOCKED_THIS_DEVICE_ONLY`, `allowBackup=false` | `apps/mobile/src/secure-storage.ts` |
| I | Enumeración de cuentas | Registro y login con mensajes genéricos | `test_duplicate_registration_is_generic` |
| I | Cacheo de datos confidenciales | `Cache-Control: no-store` en la API; el service worker nunca cachea la API | `public/sw.js` |
| **D** Denegación | Abuso de login | Rate limiting; límites de longitud en todos los campos (Pydantic) | — |
| **E** Elevación | Rol insuficiente ejecuta acción | RBAC con denegación por defecto, matriz probada (permisos monótonos por rol) | `test_rbac_ratelimit.py` |
| E | Escalar a la BD | API con rol `civia_app` (NOSUPERUSER, NOBYPASSRLS); migraciones con otro rol | `conftest.py` usa el mismo esquema que producción |

## Riesgos aceptados / pendientes

| Riesgo | Decisión | Revisión |
|---|---|---|
| `braces` (dev dependency de `eslint-config-next`) con aviso *high* sin versión corregida | Solo herramienta de lint local, no llega a producción. CI audita con `--omit=dev`. | Dependabot semanal |
| `style-src 'unsafe-inline'` en CSP | Necesario para atributos `style` de componentes accesibles; no permite ejecutar scripts | Fase 1 |
| Certificate pinning, Play Integrity / App Attest | Requieren dominio y claves de producción | Fase 4 (app móvil) |
| Rate limit en memoria si no hay `REDIS_URL` | Solo en desarrollo; staging/producción fallan al arrancar sin Redis | — |
| HIBP *fail-open* si el servicio no responde | Evita bloquear registros; se registrará el evento | Fase 1 |

## Checklist de cierre de Fase 0

- [x] Tests de aislamiento entre organizaciones (API y base de datos)
- [x] Gitleaks en pre-commit y CI
- [x] SAST (Bandit, Semgrep) y SCA (pip-audit, npm audit) en CI
- [x] Escaneo de contenedor (Trivy) en CI
- [x] Contrato OpenAPI verificado en CI
- [ ] Pentest externo (antes de producción)
