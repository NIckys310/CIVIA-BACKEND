"""Seguridad avanzada de la cuenta: MFA, bloqueo, correo, contraseñas e historial."""

import re
import time
import uuid

import pyotp
import pytest
from httpx import AsyncClient

from civia_api.config import get_settings
from civia_api.services.email import OUTBOX

PASSWORD = "cuaderno-de-obra-2026"
NEW_PASSWORD = "plano-estructural-nuevo-2026"
MOBILE = {"X-Client": "mobile"}


def _email() -> str:
    return f"sec-{uuid.uuid4().hex[:8]}@obra.co"


def _mail_token(to: str, path: str) -> str:
    for msg in reversed(OUTBOX):
        if msg.to == to and (m := re.search(rf"{path}\?token=([\w-]+)", msg.body)):
            return m.group(1)
    raise AssertionError(f"No se envió correo con {path} a {to}")


def _mails(to: str, subject_part: str) -> int:
    return sum(1 for m in OUTBOX if m.to == to and subject_part in m.subject)


async def _register(client: AsyncClient, email: str) -> None:
    res = await client.post(
        "/api/v1/auth/register",
        json={
            "email": email,
            "password": PASSWORD,
            "full_name": "Luis Pérez",
            "organization_name": "Obra",
        },
    )
    assert res.status_code == 201, res.text


async def _login(
    client: AsyncClient, email: str, password: str = PASSWORD, device: str = "Pruebas"
) -> dict[str, object]:
    res = await client.post(
        "/api/v1/auth/login",
        json={"email": email, "password": password, "device_label": device},
        headers=MOBILE,
    )
    return {"status": res.status_code, **res.json()}


def _bearer(token: object) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


async def _enable_mfa(client: AsyncClient, access: object) -> tuple[str, list[str]]:
    setup = (await client.post("/api/v1/account/mfa/setup", headers=_bearer(access))).json()
    assert setup["otpauth_uri"].startswith("otpauth://totp/")
    code = pyotp.TOTP(setup["secret"]).now()
    res = await client.post(
        "/api/v1/account/mfa/enable", json={"code": code}, headers=_bearer(access)
    )
    assert res.status_code == 200, res.text
    return setup["secret"], res.json()["codes"]


# --- Verificación de correo ----------------------------------------------------------------


async def test_register_sends_verification_and_token_is_single_use(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    token = _mail_token(email, "/verify-email")

    first = await client.post("/api/v1/account/email/verify", json={"token": token})
    assert first.status_code == 204
    again = await client.post("/api/v1/account/email/verify", json={"token": token})
    assert again.status_code == 400

    login = await _login(client, email)
    me = (await client.get("/api/v1/me", headers=_bearer(login["access_token"]))).json()
    assert me["user"]["email_verified"] is True


# --- MFA -----------------------------------------------------------------------------------


async def test_mfa_login_flow_with_totp_and_replay_protection(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    first = await _login(client, email)
    secret, codes = await _enable_mfa(client, first["access_token"])
    assert len(codes) == 10

    step1 = await _login(client, email)
    assert step1["status"] == 200 and step1["mfa_required"] is True
    assert step1["access_token"] is None
    # El token de desafío no sirve como token de acceso.
    me = await client.get("/api/v1/me", headers=_bearer(step1["mfa_token"]))
    assert me.status_code == 401

    code = pyotp.TOTP(secret).at(time.time() + 30)  # el actual se consumió al activar
    ok = await client.post(
        "/api/v1/auth/mfa/verify",
        json={"mfa_token": step1["mfa_token"], "code": code},
        headers=MOBILE,
    )
    assert ok.status_code == 200 and ok.json()["access_token"]

    # El mismo código no puede reutilizarse (aunque siga dentro de su ventana de tiempo).
    step1b = await _login(client, email)
    replay = await client.post(
        "/api/v1/auth/mfa/verify",
        json={"mfa_token": step1b["mfa_token"], "code": code},
        headers=MOBILE,
    )
    assert replay.status_code == 401


async def test_recovery_code_works_once(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    first = await _login(client, email)
    _, codes = await _enable_mfa(client, first["access_token"])

    for expected in (200, 401):
        challenge = await _login(client, email)
        res = await client.post(
            "/api/v1/auth/mfa/verify",
            json={"mfa_token": challenge["mfa_token"], "recovery_code": codes[0].lower()},
            headers=MOBILE,
        )
        assert res.status_code == expected

    status = await client.get("/api/v1/account/mfa", headers=_bearer(first["access_token"]))
    assert status.json() == {"enabled": True, "recovery_codes_remaining": 9}


async def test_disable_mfa_requires_password_and_code(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    first = await _login(client, email)
    secret, _ = await _enable_mfa(client, first["access_token"])
    headers = _bearer(first["access_token"])

    bad = await client.post(
        "/api/v1/account/mfa/disable",
        json={"password": "incorrecta-123456", "code": pyotp.TOTP(secret).now()},
        headers=headers,
    )
    assert bad.status_code == 400
    ok = await client.post(
        "/api/v1/account/mfa/disable",
        # Código del siguiente paso: el actual ya se usó al activar (anti-reutilización).
        json={"password": PASSWORD, "code": pyotp.TOTP(secret).at(time.time() + 30)},
        headers=headers,
    )
    assert ok.status_code == 204
    assert (await _login(client, email))["mfa_required"] is False


# --- Bloqueo -------------------------------------------------------------------------------


async def test_account_locks_after_repeated_failures(
    client: AsyncClient, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(get_settings(), "lockout_threshold", 3)
    email = _email()
    await _register(client, email)
    for _ in range(3):
        assert (await _login(client, email, password="clave-equivocada-1"))["status"] == 401
    assert _mails(email, "bloqueamos") == 1

    # Ni siquiera la contraseña correcta entra mientras dura el bloqueo.
    locked = await _login(client, email)
    assert locked["status"] == 429


# --- Contraseñas ---------------------------------------------------------------------------


async def test_forgot_password_is_generic_and_reset_revokes_sessions(client: AsyncClient) -> None:
    unknown = await client.post("/api/v1/account/password/forgot", json={"email": _email()})
    assert unknown.status_code == 202

    email = _email()
    await _register(client, email)
    old = await _login(client, email)
    res = await client.post("/api/v1/account/password/forgot", json={"email": email})
    assert res.status_code == 202
    token = _mail_token(email, "/reset-password")

    done = await client.post(
        "/api/v1/account/password/reset", json={"token": token, "password": NEW_PASSWORD}
    )
    assert done.status_code == 204
    assert (await client.get("/api/v1/me", headers=_bearer(old["access_token"]))).status_code == 401
    assert (await _login(client, email))["status"] == 401
    assert (await _login(client, email, password=NEW_PASSWORD))["status"] == 200
    reuse = await client.post(
        "/api/v1/account/password/reset", json={"token": token, "password": NEW_PASSWORD + "x"}
    )
    assert reuse.status_code == 400


async def test_change_password_keeps_current_session_only(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    laptop = await _login(client, email, device="Portátil")
    phone = await _login(client, email, device="Celular")

    wrong = await client.post(
        "/api/v1/account/password/change",
        json={"current_password": "no-es-esta-1234", "new_password": NEW_PASSWORD},
        headers=_bearer(laptop["access_token"]),
    )
    assert wrong.status_code == 400
    ok = await client.post(
        "/api/v1/account/password/change",
        json={"current_password": PASSWORD, "new_password": NEW_PASSWORD},
        headers=_bearer(laptop["access_token"]),
    )
    assert ok.status_code == 204
    assert (
        await client.get("/api/v1/me", headers=_bearer(laptop["access_token"]))
    ).status_code == 200
    assert (
        await client.get("/api/v1/me", headers=_bearer(phone["access_token"]))
    ).status_code == 401


# --- Avisos e historial --------------------------------------------------------------------


async def test_new_device_alert_and_security_history(client: AsyncClient) -> None:
    email = _email()
    await _register(client, email)
    first = await _login(client, email, device="Portátil")
    await _login(client, email, device="Portátil")
    assert _mails(email, "nuevo inicio de sesión") == 0
    await _login(client, email, device="Celular desconocido")
    assert _mails(email, "nuevo inicio de sesión") == 1

    events = (
        await client.get("/api/v1/account/security-events", headers=_bearer(first["access_token"]))
    ).json()
    actions = [e["action"] for e in events]
    assert "auth.login.new_device" in actions
    assert actions.count("auth.login.succeeded") == 3
    assert any(e["device"] == "Celular desconocido" for e in events)

    other = _email()
    await _register(client, other)
    theirs = await _login(client, other)
    their_events = (
        await client.get("/api/v1/account/security-events", headers=_bearer(theirs["access_token"]))
    ).json()
    # Solo ve sus propios eventos (registro + login), nunca los del otro usuario.
    assert {e["action"] for e in their_events} == {"auth.register", "auth.login.succeeded"}
