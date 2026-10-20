# Política de seguridad de CIVIA

## Reportar una vulnerabilidad

Si encuentras una vulnerabilidad, **no abras un issue público**. Usa
[GitHub Security Advisories](../../security/advisories/new) de este repositorio
(reporte privado). Incluye pasos para reproducir, impacto y versión afectada.

- Acuse de recibo: 3 días hábiles.
- Evaluación inicial: 10 días hábiles.
- Divulgación coordinada: publicamos el aviso cuando la corrección esté disponible
  y damos crédito a quien reporta, salvo que prefiera el anonimato.

## Alcance

API (`src/main/java`), interfaz web (`web/`), servidor web (`server/`) y capa de IA (`civia-ai`).
Fuera de alcance: ataques de denegación de servicio volumétricos, ingeniería social y
hallazgos en dependencias sin un vector explotable en CIVIA.

## Datos personales

CIVIA trata datos personales conforme a la **Ley 1581 de 2012 (Habeas Data, Colombia)**.
Los planos de los clientes son propiedad intelectual confidencial: un incidente que los
exponga se trata como severidad crítica.

## Inteligencia artificial

- Las preguntas al Copilot, a la biblioteca y los análisis con Claude comparten una cuota por
  usuario (`CIVIA_AI_REQUESTS_PER_MINUTE`) para proteger el costo de la API.
- `ANTHROPIC_API_KEY` solo vive en las variables de entorno del servidor (Render); nunca en el
  repositorio ni en la interfaz.
- El contenido de planos y documentos se trata como dato, nunca como instrucción.

Ver también [`docs/security/threat-model.md`](docs/security/threat-model.md).
