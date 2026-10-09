"""Row-Level Security por organización y permisos mínimos del rol civia_app.

La API se conecta como `civia_app` (NOSUPERUSER, NOBYPASSRLS, no propietario), así que
estas políticas se aplican a todas sus consultas. Cada petición fija `app.org_id` y
`app.user_id` con `set_config(..., true)` (ver civia_api.db.session.set_tenant_context).

`users` y `refresh_tokens` no son datos de un tenant (el login busca por email antes de
conocer la organización); su acceso lo restringe la capa de aplicación.

Revision ID: 0002
Revises: 0001
Create Date: 2026-10-08
"""

from collections.abc import Sequence

from alembic import op

revision: str = "0002"
down_revision: str | None = "0001"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

TENANT_TABLES = ("projects", "project_members")


def upgrade() -> None:
    # El rol normalmente lo crea infra/postgres/init; aquí solo garantizamos que exista.
    op.execute(
        """
        DO $$ BEGIN
          IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'civia_app') THEN
            CREATE ROLE civia_app NOLOGIN NOSUPERUSER NOBYPASSRLS;
          END IF;
        END $$
        """
    )
    op.execute("GRANT USAGE ON SCHEMA public TO civia_app")
    op.execute(
        "GRANT SELECT, INSERT, UPDATE, DELETE ON organizations, users, memberships, "
        "projects, project_members, refresh_tokens TO civia_app"
    )
    # Auditoría: solo lectura. La escritura pasa por audit_write() (migración 0003).
    op.execute("GRANT SELECT ON audit_log TO civia_app")

    op.execute(
        "CREATE FUNCTION app_current_org() RETURNS uuid LANGUAGE sql STABLE AS "
        "$$ SELECT nullif(current_setting('app.org_id', true), '')::uuid $$"
    )
    op.execute(
        "CREATE FUNCTION app_current_user() RETURNS uuid LANGUAGE sql STABLE AS "
        "$$ SELECT nullif(current_setting('app.user_id', true), '')::uuid $$"
    )

    for table in TENANT_TABLES:
        op.execute(f"ALTER TABLE {table} ENABLE ROW LEVEL SECURITY")
        op.execute(f"ALTER TABLE {table} FORCE ROW LEVEL SECURITY")
        op.execute(
            f"CREATE POLICY tenant_isolation ON {table} "
            "USING (organization_id = app_current_org()) "
            "WITH CHECK (organization_id = app_current_org())"
        )

    # Un usuario ve sus propias membresías (para elegir organización) y las de su org activa.
    op.execute("ALTER TABLE memberships ENABLE ROW LEVEL SECURITY")
    op.execute("ALTER TABLE memberships FORCE ROW LEVEL SECURITY")
    op.execute(
        "CREATE POLICY membership_visibility ON memberships "
        "USING (organization_id = app_current_org() OR user_id = app_current_user()) "
        "WITH CHECK (organization_id = app_current_org())"
    )

    # Una organización es visible si es la activa o si el usuario es miembro.
    op.execute("ALTER TABLE organizations ENABLE ROW LEVEL SECURITY")
    op.execute("ALTER TABLE organizations FORCE ROW LEVEL SECURITY")
    op.execute(
        "CREATE POLICY organization_visibility ON organizations "
        "USING (id = app_current_org() OR id IN "
        "  (SELECT organization_id FROM memberships WHERE user_id = app_current_user())) "
        "WITH CHECK (id = app_current_org())"
    )

    # Auditoría: se lee solo la de la organización activa.
    # Sin FORCE: el propietario (funciones SECURITY DEFINER de la cadena de hashes)
    # necesita ver todas las filas; civia_app, al no ser propietario, sí queda limitado.
    op.execute("ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY")
    op.execute(
        "CREATE POLICY audit_read ON audit_log FOR SELECT "
        "USING (organization_id = app_current_org())"
    )


def downgrade() -> None:
    op.execute("DROP POLICY IF EXISTS audit_read ON audit_log")
    op.execute("ALTER TABLE audit_log DISABLE ROW LEVEL SECURITY")
    op.execute("DROP POLICY IF EXISTS organization_visibility ON organizations")
    op.execute("ALTER TABLE organizations DISABLE ROW LEVEL SECURITY")
    op.execute("DROP POLICY IF EXISTS membership_visibility ON memberships")
    op.execute("ALTER TABLE memberships DISABLE ROW LEVEL SECURITY")
    for table in TENANT_TABLES:
        op.execute(f"DROP POLICY IF EXISTS tenant_isolation ON {table}")
        op.execute(f"ALTER TABLE {table} DISABLE ROW LEVEL SECURITY")
    op.execute("DROP FUNCTION IF EXISTS app_current_user()")
    op.execute("DROP FUNCTION IF EXISTS app_current_org()")
    op.execute(
        "REVOKE ALL ON organizations, users, memberships, projects, project_members, "
        "refresh_tokens, audit_log FROM civia_app"
    )
