package com.civia.service;

import com.civia.ai.assistant.ArchitectAssistant;
import com.civia.web.Dto;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * CIVIA Copilot en streaming (Server-Sent Events). Eventos: {@code start} (id de la conversación),
 * {@code delta} (fragmento de texto), {@code done} (mensaje guardado) y {@code error}.
 *
 * <p>Si quien pregunta cierra la pestaña, la respuesta se termina de generar y se guarda igual,
 * para que aparezca en el historial.
 */
@Service
public class CopilotStreamService {

    private static final Logger LOG = Logger.getLogger(CopilotStreamService.class.getName());
    static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final AiService ai;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public CopilotStreamService(AiService ai) {
        this.ai = ai;
    }

    /** Valida y guarda la pregunta en el hilo de la petición (con el usuario en sesión) y responde en segundo plano. */
    public SseEmitter stream(Dto.ChatRequest req) {
        return stream(ai.prepareChat(req));
    }

    /** Nueva respuesta para la última pregunta de la conversación. */
    public SseEmitter regenerate(java.util.UUID conversationId) {
        return stream(ai.prepareRegenerate(conversationId));
    }

    private SseEmitter stream(AiService.PreparedChat chat) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        AtomicBoolean open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(e -> open.set(false));

        workers.execute(() -> {
            send(emitter, open, "start", Map.of("conversationId", chat.conversationId()));
            try {
                ArchitectAssistant.Answer answer = ai.engine().assistant().ask(chat.message(), chat.history(),
                        chat.project(), chat.attachments(), delta -> send(emitter, open, "delta", Map.of("text", delta)));
                Dto.ChatResponse saved = ai.saveReply(chat.conversationId(), answer);
                send(emitter, open, "done", saved);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "El Copilot no pudo responder", e);
                send(emitter, open, "error", Map.of("message", "CIVIA no pudo responder ahora. Intenta de nuevo."));
            }
            if (open.get()) emitter.complete();
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, AtomicBoolean open, String name, Object data) {
        if (!open.get()) return;
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // La conexión se cerró: se sigue generando la respuesta para guardarla.
            open.set(false);
        }
    }
}
