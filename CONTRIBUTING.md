# Cómo contribuir a CIVIA

Equipo de dos personas. Cada quien hace **sus propios commits desde su cuenta**:
el historial de Git es la evidencia de quién hizo qué, así que nunca se commitea a nombre
de otra persona ni se agregan commits vacíos o artificiales.

## Primer día (cada integrante)

```bash
git clone https://github.com/NIckys310/civia-ai.git
cd civia-ai
git config user.name "Tu Nombre"
git config user.email "el-correo-de-tu-cuenta-de-github@ejemplo.com"   # el de TU cuenta: así GitHub te cuenta los commits
npm install
uv sync --python 3.12
uvx pre-commit install
```

Comprueba que tu correo está verificado en GitHub (Settings → Emails); si no coincide con
`user.email`, tus commits no aparecen en tu perfil ni en las estadísticas del repo.

## Ramas

```
main      ← solo releases (tags v0.x.y). Nadie hace push directo.
develop   ← integración. Entra solo por Pull Request.
feat/<módulo>-<descripción>   fix/<...>   docs/<...>   security/<...>
```

1. `git switch develop && git pull`
2. `git switch -c feat/web-plan-viewer`
3. Commits pequeños y atómicos (uno por cambio lógico), cada uno compila y pasa tests.
4. `git push -u origin feat/web-plan-viewer` y abre un PR hacia `develop`.
5. La otra persona revisa. Se integra con **merge commit** (el squash está desactivado para
   conservar los commits individuales de cada autor).

## Commits

[Conventional Commits](https://www.conventionalcommits.org/es/): `feat(api): ...`, `fix(web): ...`,
`test(engine): ...`, `docs(adr): ...`, `security(api): ...`, `refactor`, `perf`, `build`, `ci`, `chore`.
Imperativo, asunto ≤ 72 caracteres, cuerpo con el *por qué* cuando haga falta.

## Reparto sugerido por fases

| Área | Integrante A (NIckys310) | Integrante B |
|---|---|---|
| Fase 1 | API de planos (subida segura, versiones), worker OCR/detección, reportes | Visor de planos web (PDF.js + superposición), biblioteca técnica (UI + búsqueda), Copilot (UI) |
| Fase 2 | `civia-engine` (cargas, combinaciones, verificaciones) | UI de cálculos con trazabilidad y fórmulas (KaTeX) |
| Fase 3 | Reconstrucción 3D en el backend, IFC | Visor 3D (React Three Fiber) |
| Fase 4 | Sincronización offline (API) | App móvil: CIVIA Measure, cámara, evidencia |

Cada integrante revisa los PR del otro: revisar también cuenta como trabajo de equipo
(comentarios y aprobaciones quedan en GitHub).

## Antes de abrir un PR

```bash
cd services/api && ../../.venv/Scripts/python -m pytest
npx turbo run typecheck test
uv run ruff check services/api && uv run mypy services/api/src
```

Si cambiaste la API: `python scripts/export_openapi.py && npm run generate -w @civia/shared-types`.
