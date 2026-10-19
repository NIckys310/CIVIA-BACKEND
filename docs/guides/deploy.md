# Despliegue público

CIVIA AI corre como **un solo servicio** (API + interfaz) en un contenedor Docker.

## Render + PostgreSQL (Neon o Render)

1. Crea una base PostgreSQL y copia su URL en formato JDBC:
   `jdbc:postgresql://HOST:5432/civia?sslmode=require`.
2. En Render, **New → Blueprint** apuntando a CIVIA-BACKEND (usa `render.yaml`).
3. Define las variables:
   - `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
   - `CIVIA_JWT_SECRET` y `CIVIA_DATA_KEY`: `openssl rand -base64 48` (guárdalas: si cambian se
     cierran las sesiones y no se pueden leer los datos cifrados).
   - `ANTHROPIC_API_KEY` (opcional) para Claude.
4. El Dockerfile compila CIVIA-IA y toma la interfaz de CIVIA-FRONTEND (`CIVIA_REF`, por defecto `main`).

## Almacenamiento de archivos

Los archivos se guardan en `CIVIA_STORAGE_DIR`. En plataformas con disco efímero monta un disco
persistente en `/app/data`.

## Servidor solo para la interfaz (opcional)

```bash
java server/CiviaWeb.java --port 3000 --api https://tu-api.example.com
```
