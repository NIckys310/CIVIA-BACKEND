# 0001 — Monorepo con npm workspaces + Turborepo y uv para Python

- Estado: aceptado
- Fecha: 2026-10-08

## Contexto
El plan inicial proponía pnpm. En el equipo de desarrollo, `corepack enable pnpm` falla sin
permisos de administrador (no puede escribir en `C:\Program Files\nodejs`).

## Decisión
- JavaScript/TypeScript: **npm workspaces** (`apps/*`, `packages/*`) + **Turborepo** para orquestar
  `build`, `typecheck`, `test` y `lint` con caché.
- Python: workspace de **uv** con `uv.lock` congelado en CI.
- `overrides` de npm fija `react-native` a la versión que exige Expo para evitar dos copias
  de React Native en el árbol (riesgo real en builds nativos).

## Consecuencias
- Sin dependencias globales extra: basta Node ≥ 22 y Python.
- npm 12 bloquea scripts de instalación no aprobados (`allowScripts`): mejora la cadena de
  suministro; se aprueban caso a caso con `npm install-scripts approve`.
