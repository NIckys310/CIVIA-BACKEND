"""Errores de dominio de autenticación compartidos por los servicios."""

from civia_api.db.session import CommitOnError


class AuthError(CommitOnError):
    """Error de autenticación con mensaje seguro para mostrar al usuario.

    Hereda de CommitOnError: la auditoría, los contadores de intentos y las revocaciones
    previas al error persisten. Por eso las validaciones que no deben dejar rastro
    (p. ej. en `register`) ocurren antes de escribir nada.
    """

    def __init__(self, message: str, *, status: int = 401) -> None:
        super().__init__(message)
        self.message = message
        self.status = status
