# Arquitectura de CIVIA AI

```
Navegador (PWA)                      CIVIA-BACKEND (Spring Boot, Java 21)              Proveedores
┌──────────────────────┐   HTTPS    ┌──────────────────────────────────────┐
│ web/ (CIVIA-FRONTEND)│──────────▶│ /api/v1/auth    CIVIA Auth (JWT)      │
│  · vistas en módulos │  mismo    │ /api/v1/projects, documents, budget…  │──▶ PostgreSQL / H2
│  · Three.js (3D)     │  origen   │ /api/v1/ai      ┐                     │──▶ Disco (archivos)
│  · cámara (WebRTC)   │◀──────────│ /api/v1/library ┘ civia-ai (CIVIA-IA) │──▶ Claude (opcional)
│  · service worker    │           │ Sirve también la interfaz estática    │
└──────────────────────┘           └──────────────────────────────────────┘
```

- **Un solo origen.** El backend sirve la interfaz (o `CIVIA-FRONTEND/server/CiviaWeb.java` actúa
  de proxy). Así la cookie de sesión es `SameSite=Strict`, no hay CORS y la CSP es estricta
  (`script-src 'self'`, sin scripts en línea).
- **Capa de IA como librería.** `civia-ai` se compila en CIVIA-IA y se incluye en la API como
  dependencia Maven: sin servicio extra ni latencia de red.
- **Motor dual.** Con `ANTHROPIC_API_KEY`, Claude lee planos (PDF e imágenes) y conversa; sin ella
  (o si falla), el motor local responde con la biblioteca y reglas deterministas.

## Módulos de la interfaz

| Ruta | Vista |
|---|---|
| `#/` | Panel: KPI, proyectos recientes, tareas, actividad, pregunta rápida |
| `#/projects/:id/(overview·files·tasks·budget·spaces·team·log)` | Proyecto: revisión IA, archivos y versiones, kanban, presupuesto con AIU, programa, equipo, bitácora |
| `#/viewer/:doc` · `#/massing/:project` | Visor 3D (GLB/glTF/OBJ/STL): medir, notas, sección, sol, análisis IA · volumetría con IA exportable a GLB |
| `#/camera/:project` | Cámara: foto de obra, foto de plano (con análisis), medición con referencia |
| `#/copilot/:conv` | Chat en tiempo real (streaming) con contexto de proyecto y planos adjuntos; historial con búsqueda, fijadas, exportar y valorar |
| `#/library/…` | Biblioteca de ingeniería civil (19 categorías): artículo del día, pregunta única, categorías, artículos propios, glosario por categoría, favoritos |
| `#/profile` | Perfil, contraseña, verificación en dos pasos, sesiones, historial |

## Rendimiento al entrar

- Sin framework ni paso de compilación: 3 módulos pequeños precargados (`modulepreload`), vistas y
  Three.js cargados bajo demanda.
- Service worker: la interfaz abre desde caché y se actualiza en segundo plano (nunca cachea `/api`).
- Restauración de sesión en una sola petición, y solo si hubo sesión antes en ese navegador.
- Compresión gzip/HTTP2, `Cache-Control` largo para librerías e imágenes y revalidación (304) para el código.
- Medido en local: panel de proyecto completo (sesión + datos + recomendaciones IA) en ≈ 0,2 s.

## Seguridad

Ver [docs/security/threat-model.md](security/threat-model.md) (backend) y el README de CIVIA-BACKEND.
