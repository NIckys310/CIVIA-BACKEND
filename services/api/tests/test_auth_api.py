"""Flujo de autenticación de extremo a extremo contra la API y Postgres reales."""

import uuid
from datetime import UTC, datetime, timedelta

from httpx import AsyncClient
from sqlalchemy import update

from civia_api.db.session import get_sessionmaker
from civia_api.models import RefreshToken
from civia_api.security.tokens import hash_refresh_token

PASSWORD = "cuaderno-de-obra-2026"
MOBILE = {"X-Client": "mobile"}
CSRF = {"X-Requested-With": "civia"}


def _email() -> str:
    return f"ing-{uuid.uuid4().hex[:8]}@obra.co"


async def _register(client: AsyncClient, email: str, org: str = "Constructora Andina") -> None:
    res = await client.post(
        "/api/v1/auth/register",
        json={
            "email": email,
            "password": PASSWORD,
            "full_name": "Ana Ruiz",
            "organization_name": org,
        },
    )
    assert res.status_code == 201, res.text


async def _login_mobile(client: AsyncClient, email: str) -> dict[str, str]:
    res = await client.post(
        "/api/v1/auth/login", json={"email": email, "password": PASSWORD}, headers=MOBILE
    )
    assert res.status_code == 200, res.text
    return res.json()  # type: ignore[no-any-return]


def _bearer(tokens: dict[str, str]) -> dict[str, str]:
    return {"Authorization": f"Bearer {tokens['access_token']}"}


async def test_register_login_and_me(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email.upper())  # el correo se normaliza a minúsculas
    tokens = await _login_mobile(client, email)
    me = (await client.get("/api/v1/me", headers=_bearer(tokens))).json()
    assert me["user"]["email"] == email
    assert me["memberships"][0]["role"] == "admin"
    assert me["memberships"][0]["organization_name"] == "Constructora Andina"


async def test_duplicate_registration_is_generic(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    res = await client.post(
        "/api/v1/auth/register",
        json={
            "email": email,
            "password": PASSWORD,
            "full_name": "Otra",
            "organization_name": "X S.A.S",
        },
    )
    assert res.status_code == 409
    assert "registr" in res.json()["detail"]


async def test_weak_password_is_rejected(client: AsyncClient) -> None:
    res = await client.post(
        "/api/v1/auth/register",
        json={
            "email": _email(),
            "password": "corta",
            "full_name": "Ana",
            "organization_name": "Obra",
        },
    )
    assert res.status_code == 422


async def test_wrong_password_and_unknown_user_look_identical(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    wrong = await client.post("/api/v1/auth/login", json={"email": email, "password": "x" * 12})
    unknown = await client.post(
        "/api/v1/auth/login", json={"email": _email(), "password": "x" * 12}
    )
    assert wrong.status_code == unknown.status_code == 401
    assert wrong.json() == unknown.json()


async def test_login_is_rate_limited(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    codes = [
        (
            await client.post(
                "/api/v1/auth/login", json={"email": email, "password": "mala-clave-123"}
            )
        ).status_code
        for _ in range(6)
    ]
    assert codes[:5] == [401] * 5
    assert codes[5] == 429


async def test_refresh_rotation_and_reuse_detection(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    first = await _login_mobile(client, email)

    res = await client.post(
        "/api/v1/auth/refresh", json={"refresh_token": first["refresh_token"]}, headers=MOBILE
    )
    assert res.status_code == 200
    second = res.json()
    assert second["refresh_token"] != first["refresh_token"]

    # Simulamos que el token antiguo fue robado y se usa pasado el margen de gracia.
    async with get_sessionmaker()() as s, s.begin():
        await s.execute(
            update(RefreshToken)
            .where(RefreshToken.token_hash == hash_refresh_token(first["refresh_token"]))
            .values(rotated_at=datetime.now(UTC) - timedelta(minutes=5))
        )
    reuse = await client.post(
        "/api/v1/auth/refresh", json={"refresh_token": first["refresh_token"]}, headers=MOBILE
    )
    assert reuse.status_code == 401

    # Toda la familia queda revocada: el token legítimo más nuevo tampoco sirve…
    again = await client.post(
        "/api/v1/auth/refresh", json={"refresh_token": second["refresh_token"]}, headers=MOBILE
    )
    assert again.status_code == 401
    # …y el access token asociado deja de valer de inmediato.
    assert (await client.get("/api/v1/me", headers=_bearer(second))).status_code == 401


async def test_web_refresh_uses_httponly_cookie_and_requires_csrf_header(
    client: AsyncClient,
) -> None:
    email = _email()
    await _register(client, email)
    res = await client.post("/api/v1/auth/login", json={"email": email, "password": PASSWORD})
    assert "refresh_token" not in res.json() or res.json()["refresh_token"] is None
    cookie = res.headers["set-cookie"]
    assert "HttpOnly" in cookie and "Secure" in cookie and "samesite=strict" in cookie.lower()

    token = res.cookies["civia_refresh"]
    client.cookies.set("civia_refresh", token)
    no_csrf = await client.post("/api/v1/auth/refresh")
    assert no_csrf.status_code == 403
    ok = await client.post("/api/v1/auth/refresh", headers=CSRF)
    assert ok.status_code == 200


async def test_remote_logout_kills_other_device(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    laptop = await _login_mobile(client, email)
    phone = await _login_mobile(client, email)

    sessions = (await client.get("/api/v1/auth/sessions", headers=_bearer(laptop))).json()
    assert len(sessions) == 2
    phone_session = next(s for s in sessions if not s["current"])

    res = await client.delete(
        f"/api/v1/auth/sessions/{phone_session['id']}", headers=_bearer(laptop)
    )
    assert res.status_code == 204
    assert (await client.get("/api/v1/me", headers=_bearer(phone))).status_code == 401
    assert (await client.get("/api/v1/me", headers=_bearer(laptop))).status_code == 200


async def test_projects_are_isolated_across_organizations_via_api(client: AsyncClient) -> None:
    alice, bob = _email(), _email()
    await _register(client, alice, org="Constructora Alfa")
    await _register(client, bob, org="Ingeniería Beta")
    a = await _login_mobile(client, alice)
    b = await _login_mobile(client, bob)
    org_a = (await client.get("/api/v1/me", headers=_bearer(a))).json()["memberships"][0][
        "organization_id"
    ]
    org_b = (await client.get("/api/v1/me", headers=_bearer(b))).json()["memberships"][0][
        "organization_id"
    ]

    created = await client.post(
        "/api/v1/projects",
        json={"name": "Edificio Torre Norte", "location": "Medellín"},
        headers={**_bearer(a), "X-Organization-Id": org_a},
    )
    assert created.status_code == 201
    assert created.json()["code"] == "PRJ-0001"
    assert created.json()["norm_code"] == "NSR-10"

    # Bob no puede usar la organización de Alice ni ver sus proyectos.
    spoof = await client.get("/api/v1/projects", headers={**_bearer(b), "X-Organization-Id": org_a})
    assert spoof.status_code == 404
    own = await client.get("/api/v1/projects", headers={**_bearer(b), "X-Organization-Id": org_b})
    assert own.status_code == 200 and own.json() == []


async def test_unauthenticated_requests_are_rejected(client: AsyncClient) -> None:
    assert (await client.get("/api/v1/me")).status_code == 401
    assert (
        await client.get("/api/v1/me", headers={"Authorization": "Bearer basura"})
    ).status_code == 401


async def test_security_headers_present(client: AsyncClient) -> None:
    res = await client.get("/api/v1/health")
    assert res.headers["x-content-type-options"] == "nosniff"
    assert res.headers["x-frame-options"] == "DENY"
    assert "max-age" in res.headers["strict-transport-security"]
    assert res.headers["content-security-policy"].startswith("default-src 'none'")


async def test_project_detail_and_activity_feed(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    tokens = await _login_mobile(client, email)
    me = (await client.get("/api/v1/me", headers=_bearer(tokens))).json()
    headers = {**_bearer(tokens), "X-Organization-Id": me["memberships"][0]["organization_id"]}

    created = (
        await client.post("/api/v1/projects", json={"name": "Puente Río Claro"}, headers=headers)
    ).json()
    detail = await client.get(f"/api/v1/projects/{created['id']}", headers=headers)
    assert detail.status_code == 200 and detail.json()["name"] == "Puente Río Claro"
    missing = await client.get(f"/api/v1/projects/{uuid.uuid4()}", headers=headers)
    assert missing.status_code == 404

    feed = (await client.get("/api/v1/activity", headers=headers)).json()
    actions = [a["action"] for a in feed]
    assert actions[0] == "project.created"
    assert "auth.register" in actions
    assert all(not a.startswith("auth.login") for a in actions)
    assert feed[0]["actor_name"] == "Ana Ruiz"
