# 0010 — Respuestas del Copilot en streaming con Server-Sent Events

- Estado: aceptado
- Fecha: 2026-10-20

## Contexto
Con Claude, una respuesta completa puede tardar decenas de segundos. Esperarla entera con un
`POST /ai/chat` deja a la persona mirando un indicador de carga, y no se parece a conversar.
La interfaz pasa por el proxy de Vercel y la API corre en Render (plan gratuito).

## Decisión
- `POST /api/v1/ai/chat/stream` responde `text/event-stream` con los eventos `start`
  (id de la conversación), `delta` (fragmento de texto), `done` (mensaje guardado) y `error`.
- La pregunta se valida y se guarda en el hilo de la petición (con el usuario autenticado);
  la respuesta se genera en un hilo virtual y se guarda con `AiService.saveReply`, que no depende
  de la sesión.
- Se permite el despacho `ASYNC` en Spring Security: solo ocurre después de una petición ya autorizada.
- Cabeceras `Cache-Control: no-cache, no-transform` y `X-Accel-Buffering: no` para que los proxies
  no acumulen la respuesta.
- La interfaz usa `fetch` + `ReadableStream` (EventSource no permite POST ni la cabecera
  `Authorization`) y cae a `POST /ai/chat` si el streaming falla.
- Si la conexión se corta, la respuesta se termina y se guarda igual.

## Consecuencias
- La primera palabra aparece en pocos segundos.
- Una cuota por usuario (`CIVIA_AI_REQUESTS_PER_MINUTE`) y un límite de longitud protegen el costo.
- `/ai/chat` se mantiene para clientes sin streaming y para las pruebas.
