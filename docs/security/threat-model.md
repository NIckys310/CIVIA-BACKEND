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
| **T** Manipulación | Alterar auditoría | `audit_log` con cadena SHA-256 (cada fila incluye el hash de la anterior) | `AuditService.audit` |
| T | Archivo malicioso disfrazado | Lista de formatos, firma real (bytes mágicos), rechazo de HTML/JS, nombre aleatorio fuera de la carpeta web | `ApiFlowTest.fullProjectFlowWithUploadsAiAndLibrary` |
| T | XSS en la interfaz | CSP `script-src 'self'` (sin scripts en línea), plantillas con escape automático, Markdown que escapa antes de formatear, archivos servidos con `CSP: sandbox` y `nosniff` | `web/js/ui.js` |
| T | Datos sensibles alterados en BD | AES-256-GCM autenticado: una columna manipulada no se descifra | `SecurityPrimitivesTest.fieldEncryptionIsRandomizedAndTamperEvident` |
| **R** Repudio | Negar una acción | Auditoría encadenada, historial de seguridad visible al usuario, bitácora por proyecto | `/api/v1/auth/security-events` |
| **I** Divulgación | Acceso a proyectos o archivos ajenos (IDOR) | `ProjectAccess` valida la membresía en cada petición; recurso ajeno = 404 | `ApiFlowTest` (segundo usuario) |
| I | Token en el navegador | Access token solo en memoria; nunca en `localStorage` | `web/js/api.js` |
| I | Datos sensibles en reposo | Teléfonos, matrícula, contactos y semillas MFA cifrados con AES-256-GCM | `EncryptedStringConverter` |
| I | Caché de datos privados | `Cache-Control: private` en archivos y `no-store` en tokens; el service worker nunca cachea `/api` | `web/sw.js` |
| I | Prompt injection en planos | Delimitadores aleatorios, contenido tratado como dato, salida sin veredictos | `SafetyTest` (CIVIA-IA) |
| **D** Denegación | Abuso de la API | 900 peticiones/min por IP | `RateLimitFilter` |
| **E** Elevación | Lector que edita | Roles por proyecto (OWNER, EDITOR, VIEWER) comprobados en el servidor | `ProjectAccess.edit/own` |

## Riesgos aceptados y pendientes

| Riesgo | Estado |
|---|---|
| El límite de peticiones vive en memoria (por instancia) | Aceptado con una instancia; con varias, mover a Redis o al proxy |
| No hay recuperación de contraseña por correo (no hay SMTP configurado) | Pendiente: requiere proveedor de correo |
| No se consulta HIBP (contraseñas filtradas) | Pendiente |
| Sin límite de longitud en textos (petición del producto) | Aceptado; las subidas tienen tope configurable (`CIVIA_MAX_UPLOAD`, 2 GB por defecto) |
| Archivos en disco sin cifrado propio | Mitigar con cifrado del volumen en el proveedor |
| `style-src 'unsafe-inline'` en la CSP | Necesario para estilos en atributos; no permite ejecutar scripts |
| Sin Row-Level Security en PostgreSQL | El aislamiento se hace en la capa de servicio y se prueba (ver ADR 0009) |

## Antes de producción

- [ ] Definir `CIVIA_JWT_SECRET` y `CIVIA_DATA_KEY` propios y guardarlos en un gestor de secretos.
- [ ] HTTPS obligatorio (HSTS ya se envía).
- [ ] Pentest externo.
