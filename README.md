# CIVIA — Backend

API de **CIVIA AI · Arquitectura inteligente**: Java 21 + Spring Boot 3.5, H2/PostgreSQL y la capa
de IA de [CIVIA-IA](https://github.com/NIckys310/CIVIA-IA). También sirve la interfaz web.

> Toda salida de CIVIA requiere revisión y aprobación del profesional responsable.

| Repositorio | Contenido | Tecnología |
|---|---|---|
| [CIVIA-FRONTEND](https://github.com/NIckys310/CIVIA-FRONTEND) | Interfaz web (PWA, escritorio y móvil) y su servidor | HTML/CSS/JS + Three.js · servidor Java 21 |
| [CIVIA-BACKEND](https://github.com/NIckys310/CIVIA-BACKEND) | API, CIVIA Auth, base de datos, archivos | Java 21 · Spring Boot 3.5 · H2/PostgreSQL |
| [CIVIA-IA](https://github.com/NIckys310/CIVIA-IA) | Copilot, análisis de planos y modelos 3D, biblioteca | Java 21 · Claude (opcional) + motor local |

## Puesta en marcha (Windows, macOS o Linux)

Requisito: **Java 21**. No hace falta instalar Maven, Node ni una base de datos.

```bash
git clone https://github.com/NIckys310/CIVIA-IA.git
git clone https://github.com/NIckys310/CIVIA-BACKEND.git
git clone https://github.com/NIckys310/CIVIA-FRONTEND.git
```

```bash
cd CIVIA-BACKEND && ./run.sh        # Windows: .\run.ps1
```

Abre **http://localhost:8080**, crea tu cuenta y listo. La primera vez compila la capa de IA y
la API (unos minutos); después arranca en segundos.

Para activar Claude en el Copilot y en la lectura visual de planos, define `ANTHROPIC_API_KEY`
antes de arrancar. Con Claude, el Copilot conversa como un experto, responde cualquier pregunta y
escribe la respuesta en tiempo real. Sin clave, CIVIA usa su motor local (biblioteca con citas y
reglas de revisión).

## API

Documentación interactiva: `http://localhost:8080/api/docs` (OpenAPI en `/api/v1/openapi.json`).

| Prefijo | Qué hace |
|---|---|
| `/api/v1/auth` | **CIVIA Auth**: `register`, `token` (grant `password` / `refresh_token`), `revoke`, `user`, `password`, `sessions`, `mfa/*`, `security-events` |
| `/api/v1/projects` | Proyectos, miembros, fases, niveles, espacios, tareas, comentarios, bitácora, presupuesto |
| `/api/v1/projects/{id}/documents` · `/api/v1/documents` | Subida de planos, modelos 3D, fotos y documentos; versiones, vista previa, mediciones, notas 3D |
| `/api/v1/ai` | Copilot (`chat` y `chat/stream` en tiempo real), historial (buscar, renombrar, fijar, exportar, regenerar, valorar, borrar), análisis de planos y modelos, volumetría, recomendaciones del proyecto |
| `/api/v1/library` | 19 categorías de ingeniería civil y arquitectura, artículos (propios editables), artículo del día, estadísticas, búsqueda y búsquedas recientes, glosario por categoría, pregunta única (`ask`), favoritos |
| `/api/v1/dashboard`, `/notifications`, `/clients` | Panel, notificaciones y clientes |

## Seguridad

- **Contraseñas:** Argon2id (perfil OWASP), **sin longitud máxima**, política de fortaleza,
  historial de las 5 últimas y comparación en tiempo constante.
- **Sesiones (estilo OAuth 2.0):** `access_token` JWT HS256 de 15 min solo en memoria; `refresh_token`
  de 256 bits en cookie `HttpOnly; Secure; SameSite=Strict`, guardado como SHA-256, con rotación y
  detección de reutilización (revoca la familia).
- **Verificación en dos pasos** TOTP (RFC 6238) con códigos de recuperación.
- **Bloqueo** tras 5 intentos fallidos, límite de peticiones por IP y avisos de nuevo dispositivo.
- **Cifrado en reposo** AES-256-GCM de teléfonos, matrícula, semillas MFA y contactos.
- **Archivos:** lista de formatos permitidos, verificación de firma real, nombres aleatorios fuera de
  la carpeta web, SHA-256, `nosniff` y `CSP: sandbox` al servirlos.
- **Aislamiento:** un proyecto ajeno responde 404. Auditoría append-only con cadena de hashes.
- **Cabeceras:** CSP estricta, HSTS, `frame-ancestors 'none'`, `Permissions-Policy` (cámara solo propia).
- **IA:** el contenido de los documentos es dato, nunca instrucción (delimitadores aleatorios);
  respuestas con citas y sin veredictos de seguridad estructural.

## Configuración

Ver [.env.example](.env.example). Producción: perfil `prod` (PostgreSQL), `CIVIA_JWT_SECRET` y
`CIVIA_DATA_KEY` propios. Despliegue: [docs/guides/deploy.md](docs/guides/deploy.md).

## Calidad

```bash
./mvnw verify      # pruebas de integración (H2 en memoria)
```

[Arquitectura](docs/architecture.md) · [Modelo de datos](docs/database/er.md) ·
[Cómo contribuir](CONTRIBUTING.md) · [Seguridad](SECURITY.md) · [ADRs](docs/adr/)
