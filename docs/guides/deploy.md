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
   - `GROQ_API_KEY` (gratis, recomendado): créala en https://console.groq.com → API Keys y pégala
     solo en Render. El Copilot usa `openai/gpt-oss-120b` (cambia con `CIVIA_GROQ_MODEL`).
     Respaldo opcional: `CEREBRAS_API_KEY` y `OPENROUTER_API_KEY`.
   - `ANTHROPIC_API_KEY` (opcional, de pago) para Claude. Si está, va primero en la cadena.
   - Sin ninguna clave, o si se agota la cuota gratuita del día, el Copilot usa el motor local.
   - `CIVIA_AI_EFFORT` (`high` por defecto) y `CIVIA_AI_REQUESTS_PER_MINUTE` (20) para equilibrar
     calidad y costo.
   - `CIVIA_JWT_SECRET` y `CIVIA_DATA_KEY` las genera Render. No las cambies: se cerrarían las
     sesiones y no se podrían leer los datos cifrados.
3. El Dockerfile compila CIVIA-IA y toma la interfaz de CIVIA-FRONTEND (`CIVIA_REF`, por defecto `main`).
   El `Dockerfile` toma CIVIA-IA y CIVIA-FRONTEND con `ADD <url de git>#rama`: Docker comprueba el
   commit actual en cada build. Antes usaba `RUN git clone` y Render reutilizaba de su caché una copia
   vieja de CIVIA-IA, por eso los deploys del 20/10 y del 24/10 fallaron al compilar aunque CIVIA-IA
   ya estaba al día en `main`. Aun así, publica primero CIVIA-IA y después CIVIA-BACKEND.

## Disponibilidad en el plan gratuito

Render apaga el servicio tras **15 minutos sin tráfico**. Despertarlo tarda unos minutos (el
contenedor tiene ~0,1 CPU) y Vercel solo espera **120 s** antes de responder
`502 ROUTER_EXTERNAL_TARGET_ERROR`.

- **Mantenerlo despierto (recomendado):** crea un monitor gratuito en
  [cron-job.org](https://cron-job.org) o [UptimeRobot](https://uptimerobot.com) que consulte
  `https://civia-api-i71z.onrender.com/api/v1/status` cada 10 minutos. Las 750 h/mes gratuitas de
  Render alcanzan para un servicio encendido todo el mes (no para dos).
- `.github/workflows/keepalive.yml` hace lo mismo como respaldo, pero GitHub retrasa mucho los flujos
  programados: aunque pide cada 5 min, en la práctica corre cada 2–5 horas, así que **no basta solo**.
- Si aun así la API está dormida, la interfaz muestra «Despertando el servidor…», espera a que
  responda y repite la petición, sin cerrar la sesión.
- La imagen arranca más rápido gracias a CDS y al compilador C1 (ver el `Dockerfile`).

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
