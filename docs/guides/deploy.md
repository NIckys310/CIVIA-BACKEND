# Despliegue público (gratis): Neon + Render + Vercel

```
Navegador ──https──► Vercel (CIVIA-FRONTEND, Next.js)
                        │  /api/v1/*  (mismo dominio: cookies SameSite=Strict, sin CORS)
                        ▼
                     Render (CIVIA-BACKEND, Docker) ──► Neon (PostgreSQL 16)
                        └──► Render Key Value (Redis: rate limiting)
```

Planes gratuitos: el servicio web de Render **se duerme tras 15 min sin tráfico** y la primera
petición tarda ~1 min en despertarlo. Para una demo es suficiente.

## 1. Base de datos — Neon

1. Crea una cuenta en <https://neon.tech> y un proyecto: Postgres 16, región **AWS us-east-1**.
2. En *Connection details* desactiva **Connection pooling** y copia la URL (la del rol
   `neondb_owner`). Tiene la forma `postgresql://neondb_owner:…@ep-….us-east-1.aws.neon.tech/neondb?sslmode=require`.
3. No la pegues en ningún archivo ni chat: solo en Render (paso 2).

> La API acepta la URL tal cual (convierte `sslmode` al formato de asyncpg). Las migraciones
> crean el rol `civia_app` (sin privilegios, sujeto a RLS) y la API se conecta con él.

## 2. API — Render

1. Crea una cuenta en <https://render.com> entrando con GitHub y autoriza el repo `CIVIA-BACKEND`.
2. **New → Blueprint** → elige `CIVIA-BACKEND` (rama `main`). Render lee `render.yaml`.
3. Te pedirá dos valores:
   - `MIGRATIONS_DATABASE_URL`: la URL de Neon del paso 1.
   - `WEB_BASE_URL`: pon `https://example.com` por ahora; lo actualizas en el paso 4.
4. **Apply**. Render genera `APP_DB_PASSWORD`, `JWT_SIGNING_SEED` y `DATA_ENCRYPTION_KEY`, crea
   el Redis y despliega. En los logs verás `Base de datos lista` y luego Uvicorn.
5. Comprueba `https://civia-api-XXXX.onrender.com/api/v1/health` → `{"status":"ok",…}`.

## 3. Web — Vercel

En CIVIA-FRONTEND (ver su `docs/guides/deploy.md`): proyecto con *Root Directory* `apps/web`
y la variable `API_PROXY_TARGET=https://civia-api-XXXX.onrender.com`.

## 4. Cerrar el círculo

En Render → `civia-api` → *Environment*: `WEB_BASE_URL=https://<tu-proyecto>.vercel.app` → guardar
(redespliega solo).

## Seguridad del despliegue

- Secretos generados por la plataforma; ninguno en el repositorio.
- La API usa `civia_app` (NOSUPERUSER, NOBYPASSRLS); el rol propietario solo migra.
- Redis sin acceso desde internet (`ipAllowList: []`).
- `ALLOW_CONSOLE_EMAIL=true`: los correos (verificación, recuperación) se escriben en el log
  de Render. Antes de abrir el registro a terceros, configura `EMAIL_BACKEND=smtp` y `SMTP_*`
  y elimina esa variable.
- Rotar secretos: en Render, cambia el valor (o usa *Generate*) y redespliega. Rotar
  `JWT_SIGNING_SEED` cierra todas las sesiones; rotar `DATA_ENCRYPTION_KEY` invalida los MFA
  activos (los usuarios usan sus códigos de recuperación).
