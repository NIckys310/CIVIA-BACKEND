"""Integridad del audit_log: append-only y cadena de hashes SHA-256.

- UPDATE, DELETE y TRUNCATE quedan bloqueados por trigger (incluso para el propietario).
- Cada fila guarda `prev_hash` y `hash = sha256(prev_hash | campos)`. El número de
  secuencia se asigna dentro del trigger tras tomar un advisory lock, así el orden de
  `seq` coincide con el orden de la cadena aunque haya inserciones concurrentes.
- `audit_log_verify()` recorre la cadena y devuelve el primer `seq` alterado (o NULL).

Revision ID: 0003
Revises: 0002
Create Date: 2026-10-08
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0003"
down_revision: str | None = "0002"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# Independiente de la zona horaria de la sesión (epoch) y de la forma del JSON (jsonb canónico).
ROW_DIGEST = """
encode(sha256(convert_to(
  coalesce({prev}, '') || '|' || {r}.seq::text || '|' ||
  extract(epoch FROM {r}.occurred_at)::text || '|' ||
  coalesce({r}.organization_id::text, '') || '|' || coalesce({r}.actor_user_id::text, '') || '|' ||
  {r}.action || '|' || coalesce({r}.target_type, '') || '|' || coalesce({r}.target_id, '') || '|' ||
  coalesce({r}.ip_address, '') || '|' || {r}.details::text,
'UTF8')), 'hex')
"""

UPGRADE = [
    f"""
    CREATE FUNCTION audit_log_chain() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
    DECLARE last_hash text;
    BEGIN
      PERFORM pg_advisory_xact_lock(hashtext('civia.audit_log_chain'));
      NEW.seq := nextval('audit_log_seq_seq');
      SELECT hash INTO last_hash FROM audit_log ORDER BY seq DESC LIMIT 1;
      NEW.prev_hash := last_hash;
      NEW.hash := {ROW_DIGEST.format(prev="last_hash", r="NEW")};
      RETURN NEW;
    END $$
    """,
    """
    CREATE TRIGGER audit_log_chain BEFORE INSERT ON audit_log
      FOR EACH ROW EXECUTE FUNCTION audit_log_chain()
    """,
    """
    CREATE FUNCTION audit_log_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
    BEGIN
      RAISE EXCEPTION 'audit_log es append-only: % no permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
    END $$
    """,
    """
    CREATE TRIGGER audit_log_no_update BEFORE UPDATE OR DELETE ON audit_log
      FOR EACH ROW EXECUTE FUNCTION audit_log_immutable()
    """,
    """
    CREATE TRIGGER audit_log_no_truncate BEFORE TRUNCATE ON audit_log
      FOR EACH STATEMENT EXECUTE FUNCTION audit_log_immutable()
    """,
    f"""
    CREATE FUNCTION audit_log_verify() RETURNS bigint
    LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
    DECLARE r audit_log; expected_prev text := NULL;
    BEGIN
      FOR r IN SELECT * FROM audit_log ORDER BY seq LOOP
        IF r.prev_hash IS DISTINCT FROM expected_prev
           OR r.hash IS DISTINCT FROM {ROW_DIGEST.format(prev="r.prev_hash", r="r")} THEN
          RETURN r.seq;
        END IF;
        expected_prev := r.hash;
      END LOOP;
      RETURN NULL;
    END $$
    """,
    "REVOKE ALL ON FUNCTION audit_log_verify() FROM PUBLIC",
    # Única vía de escritura para la app: civia_app no tiene INSERT sobre audit_log.
    """
    CREATE FUNCTION audit_write(
      p_organization_id uuid, p_actor_user_id uuid, p_action text, p_target_type text,
      p_target_id text, p_ip_address text, p_details jsonb
    ) RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
      INSERT INTO audit_log
        (organization_id, actor_user_id, action, target_type, target_id, ip_address, details)
      VALUES (p_organization_id, p_actor_user_id, p_action, p_target_type, p_target_id,
              p_ip_address, coalesce(p_details, '{}'::jsonb))
    $$
    """,
    "REVOKE ALL ON FUNCTION audit_write(uuid, uuid, text, text, text, text, jsonb) FROM PUBLIC",
    "GRANT EXECUTE ON FUNCTION audit_write(uuid, uuid, text, text, text, text, jsonb) TO civia_app",
]

DOWNGRADE = [
    "DROP FUNCTION IF EXISTS audit_write(uuid, uuid, text, text, text, text, jsonb)",
    "DROP FUNCTION IF EXISTS audit_log_verify()",
    "DROP TRIGGER IF EXISTS audit_log_no_truncate ON audit_log",
    "DROP TRIGGER IF EXISTS audit_log_no_update ON audit_log",
    "DROP FUNCTION IF EXISTS audit_log_immutable()",
    "DROP TRIGGER IF EXISTS audit_log_chain ON audit_log",
    "DROP FUNCTION IF EXISTS audit_log_chain()",
]


def upgrade() -> None:
    for statement in UPGRADE:
        op.execute(statement)


def downgrade() -> None:
    for statement in DOWNGRADE:
        op.execute(statement)
