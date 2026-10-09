# Arquitectura de CIVIA

## Principio rector

```
IA → interpreta
Motor de ingeniería → calcula (determinista, sin LLM)
Normativa → establece criterios
Ingeniero → valida
```

Ningún cálculo crítico lo hace un LLM. La IA interpreta y redacta; `civia-engine` calcula;
cada resultado guarda la normativa, la versión, las entradas y el usuario (trazabilidad).
Toda salida lleva: *"Requiere revisión y aprobación del profesional responsable."*

## Módulos

```mermaid
flowchart LR
  subgraph Clientes
    WEB["apps/web<br/>Next.js 16 · PWA"]
    MOB["apps/mobile<br/>Expo · Android/iOS"]
  end
  subgraph Compartido
    UI["packages/ui<br/>tokens + iconos"]
    TYPES["packages/shared-types<br/>generado de OpenAPI"]
    CLIENT["packages/api-client<br/>cliente tipado"]
  end
  subgraph Backend
    API["services/api<br/>FastAPI · /api/v1"]
    WORKER["services/worker<br/>Arq: OCR, visión, informes (Fase 1)"]
    AI["services/ai<br/>LLM + RAG con citas (Fase 1)"]
    ENGINE["packages/engine<br/>civia-engine, determinista (Fase 2)"]
  end
  subgraph Datos
    PG[("PostgreSQL 16<br/>pgvector · RLS")]
    REDIS[("Redis<br/>colas · rate limit")]
    S3[("S3 / MinIO<br/>planos")]
  end
  WEB --> CLIENT
  MOB --> CLIENT
  WEB --> UI
  MOB --> UI
  CLIENT --> TYPES
  CLIENT -->|HTTPS + JWT| API
  API --> PG
  API --> REDIS
  API --> S3
  API --> WORKER
  WORKER --> AI
  WORKER --> ENGINE
  AI -. solo interpreta resultados .-> ENGINE
```

| Componente | Estado | Notas |
|---|---|---|
| `services/api` | Fase 0 ✔ | Auth, RBAC, RLS, proyectos, actividad, auditoría |
| `apps/web` | Fase 0 ✔ | Shell completo, login, dashboard, proyectos, perfil, PWA |
| `apps/mobile` | Fase 0 ✔ | Login, biometría, pestañas, proyectos, perfil |
| `packages/ui` | Fase 0 ✔ | Tokens con test de contraste WCAG AA e iconos propios |
| `packages/api-client` / `shared-types` | Fase 0 ✔ | Contrato OpenAPI verificado en CI |
| `services/worker`, `services/ai` | Fase 1 | Análisis de planos, OCR, RAG |
| `packages/engine` | Fase 2 | Cálculo determinista con `pint`, pytest + hypothesis |

## Flujo de IA (diseño, Fase 1)

```mermaid
sequenceDiagram
  participant U as Ingeniero
  participant API
  participant W as Worker
  participant V as Visión/OCR
  participant E as civia-engine
  participant L as LLM (Claude)
  U->>API: Subir plano (PDF/DXF/imagen)
  API->>API: Validar MIME + magic bytes, antivirus, renombrar
  API->>W: Encolar análisis (analysis_run)
  W->>V: Preprocesado → OCR → detección
  V-->>W: Elementos + confianza + bbox
  W->>E: Verificaciones deterministas
  E-->>W: Resultados con norma/versión/sección
  W->>L: Contexto mínimo (recortes, resultados) — el contenido del plano es DATO, no instrucción
  L-->>W: Observaciones redactadas con citas
  W-->>API: Hallazgos 🔴🟡🟢🔵 + trazabilidad
  API-->>U: Progreso por etapas (SSE) y resultados
```

## Multi-tenant

- Cada petición con datos de una organización envía `X-Organization-Id`.
- La API **valida la membresía primero** (404 si no pertenece) y luego fija
  `app.org_id` / `app.user_id` con `set_config(..., true)` para la transacción.
- PostgreSQL aplica **Row-Level Security** con un rol `civia_app` sin superusuario:
  aunque la aplicación olvidara un filtro, la base de datos no devuelve filas de otra
  organización. Los tests lo comprueban contra Postgres real.

Ver [ER](database/er.md), [modelo de amenazas](security/threat-model.md) y [ADRs](adr/).
