# Modelo de datos (41 tablas)

Todas las tablas usan identificador UUID (no enumerable), `created_at` y `updated_at`. Los textos
escritos por usuarios no tienen límite de longitud (`text` en PostgreSQL). Las columnas marcadas
con 🔒 se cifran en reposo con AES-256-GCM.

## Identidad y seguridad (CIVIA Auth)

| Tabla | Contenido |
|---|---|
| `organizations` | Estudio o empresa |
| `users` | Correo, hash **Argon2id**, rol, estado, bloqueo, MFA (semilla 🔒), teléfono 🔒 |
| `user_profiles` | Biografía, empresa, matrícula profesional 🔒, ciudad, país, web |
| `user_preferences` | Tema, idioma, unidades, notificaciones |
| `memberships` | Usuario ↔ organización |
| `refresh_tokens` | SHA-256 del token, familia de rotación, dispositivo, IP, expiración, revocación |
| `login_attempts` | Intentos de inicio de sesión (bloqueo y auditoría) |
| `security_events` | Historial de seguridad visible para el usuario |
| `password_history` | Últimos hashes para impedir reutilización |
| `mfa_recovery_codes` | Códigos de recuperación (SHA-256) |
| `audit_log` | Auditoría append-only encadenada con SHA-256 |

## Proyectos

| Tabla | Contenido |
|---|---|
| `clients` | Clientes (correo 🔒, teléfono 🔒) |
| `projects` | Datos del proyecto, áreas, pisos, presupuesto estimado, fechas |
| `project_members` | Rol por proyecto: OWNER, EDITOR, VIEWER |
| `project_phases` | Concepto → Entrega |
| `tasks` | Kanban: estado, prioridad, responsable, fase, vencimiento |
| `comments` | Comentarios del proyecto o de un archivo |
| `tags`, `project_tags` | Etiquetas |
| `levels`, `rooms` | Niveles y programa arquitectónico |
| `invitations` | Invitaciones a proyectos |
| `activity_log` | Bitácora del proyecto |
| `notifications` | Notificaciones por usuario |

## Archivos, cámara y 3D

| Tabla | Contenido |
|---|---|
| `folders` | Carpetas |
| `documents` | Planos, modelos 3D, fotos y documentos (nombre aleatorio en disco, SHA-256, tipo verificado) |
| `document_versions` | Historial de versiones |
| `photo_metadata` | Ubicación, fecha y dispositivo de fotos de la cámara |
| `measurements` | Mediciones con referencia sobre fotos |
| `model_annotations` | Notas ancladas a puntos de modelos 3D |

## Inteligencia artificial y biblioteca

| Tabla | Contenido |
|---|---|
| `ai_analyses` | Análisis de planos, modelos y proyectos (resultado completo en JSON) |
| `ai_findings` | Hallazgos por verificación (OK / FALTA / REVISAR) y su resolución |
| `ai_conversations`, `ai_messages` | Conversaciones de Copilot con fuentes citadas; `pinned` y `last_message_at` ordenan la lista y `feedback` (1 / −1) guarda la valoración de cada respuesta |
| `library_articles` | Artículos aportados por el estudio (se suman a la búsqueda) |
| `library_favorites`, `library_search_log` | Favoritos y búsquedas |

## Presupuesto

| Tabla | Contenido |
|---|---|
| `budget_chapters`, `budget_items` | Capítulos e ítems (cantidad × valor unitario) |
| `budget_settings` | AIU: administración, imprevistos, utilidad, IVA sobre utilidad |
| `materials`, `suppliers` | Catálogo de materiales y proveedores (contacto 🔒) |

El esquema lo crea y actualiza Hibernate al arrancar (`ddl-auto=update`).
