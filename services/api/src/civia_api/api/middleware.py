"""Cabeceras de seguridad HTTP aplicadas a todas las respuestas de la API."""

from collections.abc import Awaitable, Callable

from fastapi import Request, Response

SECURITY_HEADERS = {
    "Strict-Transport-Security": "max-age=63072000; includeSubDomains; preload",
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
    "Cross-Origin-Opener-Policy": "same-origin",
    "Cross-Origin-Resource-Policy": "same-site",
    # La API solo sirve JSON; la documentación interactiva vive en /api/docs.
    "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
    "Cache-Control": "no-store",
}

_DOCS_PATHS = ("/api/docs",)


async def security_headers(
    request: Request, call_next: Callable[[Request], Awaitable[Response]]
) -> Response:
    response = await call_next(request)
    for name, value in SECURITY_HEADERS.items():
        if name == "Content-Security-Policy" and request.url.path.startswith(_DOCS_PATHS):
            continue  # Swagger UI necesita cargar sus scripts
        response.headers.setdefault(name, value)
    return response
