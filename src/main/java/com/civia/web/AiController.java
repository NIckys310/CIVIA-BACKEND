package com.civia.web;

import com.civia.ai.Recommendation;
import com.civia.ai.models.MassingAdvisor;
import com.civia.ai.models.ModelAnalyzer;
import com.civia.service.AiService;
import com.civia.service.CopilotStreamService;
import com.civia.web.Dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** CIVIA Copilot y análisis de planos, modelos 3D y proyectos. */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "Inteligencia artificial")
public class AiController {

    private final AiService ai;
    private final CopilotStreamService stream;

    public AiController(AiService ai, CopilotStreamService stream) {
        this.ai = ai;
        this.stream = stream;
    }

    @Operation(summary = "Motor de IA activo y capacidades")
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("engine", ai.engine().engineName(), "llm", ai.engine().llmEnabled(), "streaming", true,
                "capabilities", List.of("copilot", "copilot-stream", "plan-analysis", "model-analysis", "massing",
                        "project-review", "library"));
    }

    @Operation(summary = "Preguntar al Copilot (respuesta completa)")
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest req) {
        return ai.chat(req);
    }

    /** Igual que {@code /chat}, pero entrega la respuesta a medida que se escribe (Server-Sent Events). */
    @Operation(summary = "Preguntar al Copilot en tiempo real (Server-Sent Events)")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chatStream(@RequestBody ChatRequest req) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(stream.stream(req));
    }

    @Operation(summary = "Listar o buscar conversaciones (?q=)")
    @GetMapping("/conversations")
    public List<ConversationDto> conversations(@RequestParam(required = false) String q) {
        return q == null || q.isBlank() ? ai.conversations() : ai.searchConversations(q);
    }

    @Operation(summary = "Mensajes de una conversación")
    @GetMapping("/conversations/{id}")
    public List<MessageDto> conversation(@PathVariable UUID id) {
        return ai.conversation(id);
    }

    /** Renombra o fija una conversación. */
    @Operation(summary = "Renombrar o fijar una conversación")
    @PatchMapping("/conversations/{id}")
    public ConversationDto updateConversation(@PathVariable UUID id, @RequestBody ConversationUpdate req) {
        return ai.updateConversation(id, req);
    }

    @Operation(summary = "Borrar una conversación")
    @DeleteMapping("/conversations/{id}")
    public Map<String, Object> deleteConversation(@PathVariable UUID id) {
        ai.deleteConversation(id);
        return Map.of("deleted", true);
    }

    /** Nueva respuesta para la última pregunta, en streaming. */
    @Operation(summary = "Regenerar la última respuesta (streaming)")
    @PostMapping(value = "/conversations/{id}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> regenerate(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(stream.regenerate(id));
    }

    /** Valora una respuesta del Copilot ("up", "down" o null). */
    @Operation(summary = "Valorar una respuesta")
    @PostMapping("/messages/{id}/feedback")
    public MessageDto feedback(@PathVariable UUID id, @RequestBody FeedbackRequest req) {
        return ai.feedback(id, req.value());
    }

    /** La conversación en Markdown (descarga). */
    @Operation(summary = "Exportar una conversación en Markdown")
    @GetMapping(value = "/conversations/{id}/export", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> exportConversation(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"civia-copilot-" + id + ".md\"")
                .body(ai.exportConversation(id));
    }

    /** Borra todo el historial del Copilot del usuario. */
    @Operation(summary = "Borrar todo el historial del Copilot")
    @DeleteMapping("/conversations")
    public Map<String, Object> deleteAllConversations() {
        return Map.of("deleted", ai.deleteAllConversations());
    }

    @Operation(summary = "Analizar un plano")
    @PostMapping("/documents/{docId}/analyze")
    public AnalysisDto analyzePlan(@PathVariable UUID docId, @RequestBody(required = false) Map<String, String> body) {
        return ai.analyzePlan(docId, body == null ? null : body.get("notes"));
    }

    @Operation(summary = "Analizar un modelo 3D")
    @PostMapping("/models/{docId}/analyze")
    public AnalysisDto analyzeModel(@PathVariable UUID docId, @RequestBody ModelAnalyzer.ModelStats stats) {
        return ai.analyzeModel(docId, stats);
    }

    @Operation(summary = "Volumetría máxima de un lote")
    @PostMapping("/massing")
    public MassingAdvisor.MassingProposal massing(@RequestBody MassingAdvisor.MassingInput input) {
        return ai.massing(input);
    }

    @Operation(summary = "Detalle de un análisis")
    @GetMapping("/analyses/{id}")
    public AnalysisDto analysis(@PathVariable UUID id) {
        return ai.analysis(id);
    }

    @Operation(summary = "Marcar un hallazgo como resuelto")
    @PatchMapping("/analyses/{id}/findings/{checkId}")
    public Map<String, Object> resolveFinding(@PathVariable UUID id, @PathVariable String checkId, @RequestBody Map<String, Boolean> body) {
        boolean resolved = Boolean.TRUE.equals(body.get("resolved"));
        ai.resolveFinding(id, checkId, resolved);
        return Map.of("resolved", resolved);
    }

    @Operation(summary = "Análisis de un proyecto")
    @GetMapping("/projects/{projectId}/analyses")
    public List<AnalysisDto> projectAnalyses(@PathVariable UUID projectId) {
        return ai.projectAnalyses(projectId);
    }

    @Operation(summary = "Qué le falta al proyecto")
    @GetMapping("/projects/{projectId}/recommendations")
    public List<Recommendation> recommendations(@PathVariable UUID projectId) {
        return ai.recommendations(projectId);
    }
}
