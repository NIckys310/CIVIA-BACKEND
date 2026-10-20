# Modelo de amenazas (STRIDE) — versión Java

Alcance: CIVIA Auth, API, archivos, capa de IA e interfaz web. Se revisa en cada versión.
Estándares de referencia: OWASP ASVS 5.0 (L2), API Security Top 10, Top 10 for LLM Applications.

## Activos

1. Planos, modelos y fotos de clientes (propiedad intelectual): **crítico**.
2. Credenciales y sesiones de usuarios.
3. Resultados de revisión y su trazabilidad.
4. Datos personales (Ley 1581 de 2012).

## Amenazas y controles

| STRIDE | Amenaza | Control implementado | Verificación |
|---|---|---|---|
| **S** Suplantación | Fuerza bruta / credential stuffing | Límite de 20 POST/min por IP en `/api/v1/auth`, tope de 50 fallos por IP en 15 min, bloqueo de cuenta 15 min tras 5 fallos, Argon2id, hash ficticio para igualar tiempos, mensaje genérico | `ApiFlowTest.weakPasswordsAndBadLoginsAreRejectedAndAccountLocks` |
| S | Robo de refresh token | Token opaco de 256 bits en cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, guardado como SHA-256, rotación en cada uso y revocación de la familia si se reutiliza | `ApiFlowTest.refreshTokenRotatesAndReuseRevokesFamily` |
| S | Falsificación de JWT | HS256 con clave de 512 bits, emisor fijo `civia-auth`, expiración de 15 min | `ApiFlowTest.protectedEndpointsNeedToken` |
| S | Robo de contraseña | TOTP (RFC 6238) opcional con semilla cifrada y 10 códigos de recuperación de un solo uso | `SecurityPrimitivesTest.totpMatchesRfc6238Vector` |
| S | Contraseña débil o reutilizada | Mínimo 8, sin máximo, lista de contraseñas comunes, sin el correo, historial de 5 | `ApiFlowTest.passwordsAreHashedWithArgon2AndHaveNoMaxLength` |
| S | Contraseña filtrada en otro sitio | Consulta a Have I Been Pwned con k-anonymity (solo salen 5 caracteres hex del SHA-1, con `Add-Padding`) al registrarse, cambiarla o restablecerla; fail-open con registro si el servicio no responde (`CIVIA_HIBP_ENABLED`) | `ApiFlowTest.breachedPasswordsAreRejected`, `PwnedPasswordsTest` |
| S | Toma de cuenta por recuperación de contraseña | Token aleatorio de 256 bits de un solo uso (consumo atómico), guardado como SHA-256 en `user_tokens`, vence en 30 min, pedir otro invalida los anteriores, máx. 3 correos por cuenta y hora; al usarlo se cierran todas las sesiones (`TokenService.revokeAll`), no inicia sesión (el segundo factor se sigue pidiendo) y se avisa por correo. El enlace usa `CIVIA_PUBLIC_URL`, nunca la cabecera `Host`; el token va en el fragmento `#` (no llega a registros ni a `Referer`) y la interfaz lo borra del historial | `ApiFlowTest.forgotPasswordAnswersTheSameAndResetIsSingleUseAndClosesSessions`, `ApiFlowTest.resetLinksExpireAndANewRequestInvalidatesThePreviousOne` |
| **T** Manipulación | Alterar auditoría | `audit_log` con cadena SHA-256 (cada fila incluye el hash de la anterior) | `AuditService.audit` |
| T | Archivo malicioso disfrazado | Lista de formatos, firma real (bytes mágicos), rechazo de HTML/JS, nombre aleatorio fuera de la carpeta web | `ApiFlowTest.fullProjectFlowWithUploadsAiAndLibrary` |
| T | XSS en la interfaz | CSP `script-src 'self'` (sin scripts en línea), plantillas con escape automático, Markdown que escapa antes de formatear, archivos servidos con `CSP: sandbox` y `nosniff` | `web/js/ui.js` |
| T | Datos sensibles alterados en BD | AES-256-GCM autenticado: una columna manipulada no se descifra | `SecurityPrimitivesTest.fieldEncryptionIsRandomizedAndTamperEvident` |
| **R** Repudio | Negar una acción | Auditoría encadenada, historial de seguridad visible al usuario, bitácora por proyecto | `/api/v1/auth/security-events` |
| **I** Divulgación | Acceso a proyectos o archivos ajenos (IDOR) | `ProjectAccess` valida la membresía en cada petición; recurso ajeno = 404 | `ApiFlowTest` (segundo usuario) |
| I | Enumeración de cuentas por la recuperación | `POST /password/forgot` responde siempre 202 con el mismo texto y procesa en segundo plano, así que el tiempo de respuesta no depende de que la cuenta exista | `ApiFlowTest.forgotPasswordAnswersTheSameAndResetIsSingleUseAndClosesSessions` |
| I | Enlaces de recuperación en los registros | En modo `log` (desarrollo) el correo se escribe en el log; con el perfil `prod` los enlaces se ocultan (`civia.mail.log-links=false`). En producción usar `CIVIA_MAIL_MODE=smtp` | `MailService` |
| I | Token en el navegador | Access token solo en memoria; nunca en `localStorage` | `web/js/api.js` |
| I | Datos sensibles en reposo | Teléfonos, matrícula, contactos y semillas MFA cifrados con AES-256-GCM | `EncryptedStringConverter` |
| I | Caché de datos privados | `Cache-Control: private` en archivos y `no-store` en tokens; el service worker nunca cachea `/api` | `web/sw.js` |
| I | Prompt injection en planos | Delimitadores aleatorios, contenido tratado como dato, salida sin veredictos | `SafetyTest` (CIVIA-IA) |
| D | Abuso del Copilot (costo de la API de Claude) | Cuota por usuario (`CIVIA_AI_REQUESTS_PER_MINUTE`, 20/min), mensajes de máx. 12 000 caracteres, memoria acotada (`CIVIA_AI_HISTORY_TURNS`), timeout del streaming de 5 min | `AiQuotaTest`, `CopilotStreamTest` |
| I | Ver o continuar conversaciones ajenas | Toda operación del historial (leer, renombrar, valorar, exportar, regenerar, borrar) valida que la conversación sea del usuario; si no, 404 | `CopilotStreamTest`, `CopilotConversationsTest` |
| **D** Denegación | Abuso de la API | 900 peticiones/min por IP | `RateLimitFilter` |
| **E** Elevación | Lector que edita | Roles por proyecto (OWNER, EDITOR, VIEWER) comprobados en el servidor | `ProjectAccess.edit/own` |

## Riesgos aceptados y pendientes

| Riesgo | Estado |
|---|---|
| El límite de peticiones vive en memoria (por instancia) | Aceptado con una instancia; con varias, mover a Redis o al proxy |
| HIBP es fail-open: si no responde, se acepta la contraseña | Aceptado para no bloquear registros; la falla queda en el log |
| Sin límite de longitud en textos (petición del producto) | Aceptado; las subidas tienen tope configurable (`CIVIA_MAX_UPLOAD`, 2 GB por defecto) |
| Archivos en disco sin cifrado propio | Mitigar con cifrado del volumen en el proveedor |
| `style-src 'unsafe-inline'` en la CSP | Necesario para estilos en atributos; no permite ejecutar scripts |
| Sin Row-Level Security en PostgreSQL | El aislamiento se hace en la capa de servicio y se prueba (ver ADR 0009) |

## Antes de producción

- [ ] Definir `CIVIA_JWT_SECRET` y `CIVIA_DATA_KEY` propios y guardarlos en un gestor de secretos.
- [ ] HTTPS obligatorio (HSTS ya se envía).
- [ ] Correo saliente: `CIVIA_MAIL_MODE=smtp`, `SPRING_MAIL_*` y `CIVIA_PUBLIC_URL` con el dominio real.
- [ ] Pentest externo.
