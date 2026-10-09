# 0004 — Aislamiento multi-tenant con Row-Level Security

- Estado: aceptado
- Fecha: 2026-10-08

## Contexto
Los planos son propiedad intelectual de los clientes. Un error de filtro en una consulta
(IDOR) no debe poder exponer datos de otra organización.

## Decisión
- Defensa en dos capas: (1) la API valida la membresía y responde 404 si no pertenece;
  (2) PostgreSQL aplica **RLS FORCE** sobre `organization_id = app_current_org()`.
- La API se conecta con `civia_app` (NOSUPERUSER, NOBYPASSRLS, no propietario); las
  migraciones usan otro rol. El contexto se fija con `set_config(..., is_local => true)`.
- `audit_log`: lectura bajo RLS y escritura solo vía la función `audit_write()`.

## Consecuencias
- Toda tabla de negocio nueva debe incluir `organization_id`, índice y política RLS.
- Los tests de aislamiento son obligatorios por tabla (checklist de cierre de fase).
