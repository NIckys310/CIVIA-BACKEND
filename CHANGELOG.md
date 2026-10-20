# Cambios

## 0.2.0 — Copilot conversacional y biblioteca de ingeniería civil

### Copilot
- Respuestas en tiempo real (`POST /api/v1/ai/chat/stream`, Server-Sent Events) y regeneración
  de la última respuesta.
- Con Claude: conversa como un experto, responde cualquier pregunta y plantea cálculos paso a paso.
- Historial: buscar, renombrar, fijar, exportar en Markdown, valorar respuestas y borrar todo.
- Preguntas sugeridas según el estado del proyecto y resumen de uso.
- Memoria de 40 turnos, mensajes de hasta 12 000 caracteres y cuota de 20 preguntas por minuto.

### Biblioteca
- 19 categorías (nuevas: geotecnia, hidráulica, vías, topografía, puentes, seguridad en obra),
  144 artículos y 265 términos.
- Artículo del día, estadísticas, glosario por categoría, búsquedas recientes y edición o borrado
  de artículos propios.

### Seguridad
- Cuota de IA por usuario también en `/library/ask` y en los análisis con Claude.
- Documentación OpenAPI de todos los endpoints de IA y biblioteca.

## 0.1.0 — Fase 0 y seguridad avanzada
- CIVIA Auth (Argon2id, JWT + refresh rotativo, MFA TOTP, recuperación), proyectos, archivos,
  visor 3D, presupuesto, biblioteca y Copilot con motor local o Claude.
