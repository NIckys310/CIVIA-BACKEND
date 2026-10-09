"""Punto de entrada de la API: `uvicorn civia_api.main:app`."""

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from civia_api import __version__
from civia_api.api.middleware import security_headers
from civia_api.api.v1 import router as v1_router
from civia_api.config import get_settings
from civia_api.services.auth import AuthError


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="CIVIA AI API",
        version=__version__,
        openapi_url="/api/v1/openapi.json",
        docs_url=None if settings.environment == "production" else "/api/docs",
        redoc_url=None,
    )

    app.middleware("http")(security_headers)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,  # lista explícita, nunca "*" con credenciales
        allow_credentials=True,
        allow_methods=["GET", "POST", "PATCH", "DELETE"],
        allow_headers=["Authorization", "Content-Type", "X-Organization-Id", "X-Requested-With", "X-Client"],
        max_age=600,
    )

    @app.exception_handler(AuthError)
    async def _auth_error(_: Request, exc: AuthError) -> JSONResponse:
        headers = {"WWW-Authenticate": "Bearer"} if exc.status == 401 else None
        return JSONResponse({"detail": exc.message}, status_code=exc.status, headers=headers)

    @app.get("/api/v1/health", tags=["system"])
    async def health() -> dict[str, str]:
        return {"status": "ok", "version": __version__}

    app.include_router(v1_router)
    return app


app = create_app()
