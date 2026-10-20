# Despliegue público

CIVIA AI corre como **un solo servicio** (API + interfaz) en un contenedor Docker en Render, con
PostgreSQL en **Neon** y la interfaz pública en Vercel.

## Base de datos (Neon)

1. En [Neon](https://console.neon.tech) crea un proyecto `civia`, región **AWS US West 2 (Oregon)**
   (la misma que Render) y base `civia`.
2. Copia la cadena de conexión **directa** (sin *pooling*):
   `postgresql://USUARIO:CLAVE@ep-xxxx.us-west-2.aws.neon.tech/civia?sslmode=require&channel_binding=require`.
   El backend la convierte a JDBC, separa usuario y clave y descarta `channel_binding`
   (parámetro de `psql` que el driver Java no admite).
3. Hibernate crea las tablas en el primer arranque.

## Render

1. **New → Blueprint** apuntando a CIVIA-BACKEND (usa `render.yaml`).
2. En **Environment** define:
   - `DATABASE_URL`: la cadena de Neon del paso anterior.
   - `ANTHROPIC_API_KEY` (opcional) para Claude: créala en https://console.anthropic.com
     (Settings → API keys) y pégala solo en Render. Sin ella el Copilot usa el motor local.
   - `CIVIA_AI_EFFORT` (`high` por defecto) y `CIVIA_AI_REQUESTS_PER_MINUTE` (20) para equilibrar
     calidad y costo.
   - `CIVIA_JWT_SECRET` y `CIVIA_DATA_KEY` las genera Render. No las cambies: se cerrarían las
     sesiones y no se podrían leer los datos cifrados.
3. El Dockerfile compila CIVIA-IA y toma la interfaz de CIVIA-FRONTEND (`CIVIA_REF`, por defecto `main`).
4. `.github/workflows/keepalive.yml` consulta `/api/v1/status` cada 5 minutos para que el plan
   gratuito no se duerma.

## Almacenamiento de archivos

Los archivos se guardan en `CIVIA_STORAGE_DIR`. En plataformas con disco efímero monta un disco
persistente en `/app/data`.

## Servidor solo para la interfaz (opcional)

```bash
java server/CiviaWeb.java --port 3000 --api https://tu-api.example.com
```

## Streaming del Copilot

`/api/v1/ai/chat/stream` usa Server-Sent Events. Vercel reenvía la respuesta sin acumularla
(la API envía `Cache-Control: no-transform` y `X-Accel-Buffering: no`). Si un proxy la acumulara,
la interfaz igual recibe la respuesta completa al final.
