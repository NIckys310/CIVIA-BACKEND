"""Envío de correo transaccional (verificación, recuperación, alertas de seguridad).

- `console`: desarrollo/tests. Escribe el mensaje en el log y en `OUTBOX` (memoria).
- `smtp`: producción. STARTTLS obligatorio; se ejecuta en un hilo para no bloquear el loop.

Los correos nunca incluyen contraseñas ni códigos MFA; solo enlaces de un solo uso.
"""

import asyncio
import logging
import smtplib
import ssl
from dataclasses import dataclass
from email.message import EmailMessage

from civia_api.config import get_settings

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class OutgoingEmail:
    to: str
    subject: str
    body: str


OUTBOX: list[OutgoingEmail] = []  # solo backend "console"

FOOTER = (
    "\n\n—\nCIVIA AI · El copiloto digital del ingeniero civil\n"
    "Si no reconoces esta actividad, cambia tu contraseña y cierra las sesiones abiertas "
    "desde Perfil y seguridad."
)


def _send_smtp(message: OutgoingEmail) -> None:
    settings = get_settings()
    if not settings.smtp_host:
        raise RuntimeError("SMTP_HOST no configurado")
    msg = EmailMessage()
    msg["From"] = settings.email_from
    msg["To"] = message.to
    msg["Subject"] = message.subject
    msg.set_content(message.body + FOOTER)
    context = ssl.create_default_context()
    with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=10) as smtp:
        smtp.starttls(context=context)
        if settings.smtp_username and settings.smtp_password:
            smtp.login(settings.smtp_username, settings.smtp_password.get_secret_value())
        smtp.send_message(msg)


async def send_email(to: str, subject: str, body: str) -> None:
    """Envía sin propagar fallos de transporte: el flujo de seguridad no debe romperse
    (ni revelar información) porque el servidor de correo esté caído."""
    message = OutgoingEmail(to=to, subject=subject, body=body)
    if get_settings().email_backend == "console":
        OUTBOX.append(message)
        log.info("Correo (console) para %s: %s\n%s", to, subject, body)
        return
    try:
        await asyncio.to_thread(_send_smtp, message)
    except (OSError, smtplib.SMTPException, RuntimeError):
        log.exception("No se pudo enviar el correo '%s'", subject)
