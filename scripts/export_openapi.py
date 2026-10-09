"""Exporta el contrato OpenAPI de la API a services/api/openapi.json.

Ese archivo es el contrato publicado: el repo CIVIA-FRONTEND lo copia para generar sus tipos.

No necesita base de datos: solo construye la app y serializa su esquema.
Uso: .venv/Scripts/python scripts/export_openapi.py
"""

import json
from pathlib import Path

from civia_api.main import create_app

OUT = Path(__file__).resolve().parent.parent / "services" / "api" / "openapi.json"

OUT.write_text(
    json.dumps(create_app().openapi(), indent=2, ensure_ascii=False) + "\n",
    encoding="utf-8",
    newline="\n",  # LF también en Windows: CI compara el contrato byte a byte
)
print(f"OpenAPI exportado a {OUT}")
