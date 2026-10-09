"""Exporta el contrato OpenAPI de la API a packages/shared-types/openapi.json.

No necesita base de datos: solo construye la app y serializa su esquema.
Uso: .venv/Scripts/python scripts/export_openapi.py
"""

import json
from pathlib import Path

from civia_api.main import create_app

OUT = Path(__file__).resolve().parent.parent / "packages" / "shared-types" / "openapi.json"

OUT.write_text(json.dumps(create_app().openapi(), indent=2, ensure_ascii=False) + "\n", "utf-8")
print(f"OpenAPI exportado a {OUT}")
