"""Punto de entrada de la API: `uvicorn civia_api.main:app`."""

from fastapi import FastAPI

from civia_api import __version__


def create_app() -> FastAPI:
    app = FastAPI(
        title="CIVIA AI API",
        version=__version__,
        openapi_url="/api/v1/openapi.json",
        docs_url="/api/docs",
        redoc_url=None,
    )

    @app.get("/api/v1/health", tags=["system"])
    async def health() -> dict[str, str]:
        return {"status": "ok", "version": __version__}

    return app


app = create_app()
