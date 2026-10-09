#!/bin/sh
# Crea el rol de aplicación sin privilegios (NOSUPERUSER, NOBYPASSRLS).
# Los permisos sobre tablas los concede la migración inicial de Alembic.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
  CREATE ROLE civia_app LOGIN PASSWORD '${APP_DB_PASSWORD}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  GRANT CONNECT ON DATABASE "$POSTGRES_DB" TO civia_app;
  GRANT USAGE ON SCHEMA public TO civia_app;
SQL
