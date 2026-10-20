package com.civia.web;

import com.civia.ai.Recommendation;
import com.civia.ai.models.MassingAdvisor;
import com.civia.ai.models.ModelAnalyzer;
import com.civia.service.AiService;
import com.civia.service.CopilotStreamService;
import com.civia.web.Dto.*;
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

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("engine", ai.engine().engineName(), "llm", ai.engine().llmEnabled(),
                "capabilities", List.of("copilot", "plan-analysis", "model-analysis", "massing", "project-review", "library"));
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest req) {
        return ai.chat(req);
    }

    /** Igual que {@code /chat}, pero entrega la respuesta a medida que se escribe (Server-Sent Events). */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chatStream(@RequestBody ChatRequest req) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(stream.stream(req));
    }

    @GetMapping("/conversations")
    public List<ConversationDto> conversations(@RequestParam(required = false) String q) {
        return q == null || q.isBlank() ? ai.conversations() : ai.searchConversations(q);
    }

    @GetMapping("/conversations/{id}")
    public List<MessageDto> conversation(@PathVariable UUID id) {
        return ai.conversation(id);
    }

    /** Renombra o fija una conversación. */
    @PatchMapping("/conversations/{id}")
    public ConversationDto updateConversation(@PathVariable UUID id, @RequestBody ConversationUpdate req) {
        return ai.updateConversation(id, req);
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, Object> deleteConversation(@PathVariable UUID id) {
        ai.deleteConversation(id);
        return Map.of("deleted", true);
    }

    @PostMapping("/documents/{docId}/analyze")
    public AnalysisDto analyzePlan(@PathVariable UUID docId, @RequestBody(required = false) Map<String, String> body) {
        return ai.analyzePlan(docId, body == null ? null : body.get("notes"));
    }

    @PostMapping("/models/{docId}/analyze")
    public AnalysisDto analyzeModel(@PathVariable UUID docId, @RequestBody ModelAnalyzer.ModelStats stats) {
        return ai.analyzeModel(docId, stats);
    }

    @PostMapping("/massing")
    public MassingAdvisor.MassingProposal massing(@RequestBody MassingAdvisor.MassingInput input) {
        return ai.massing(input);
    }

    @GetMapping("/analyses/{id}")
    public AnalysisDto analysis(@PathVariable UUID id) {
        return ai.analysis(id);
    }

    @PatchMapping("/analyses/{id}/findings/{checkId}")
    public Map<String, Object> resolveFinding(@PathVariable UUID id, @PathVariable String checkId, @RequestBody Map<String, Boolean> body) {
        boolean resolved = Boolean.TRUE.equals(body.get("resolved"));
        ai.resolveFinding(id, checkId, resolved);
        return Map.of("resolved", resolved);
    }

    @GetMapping("/projects/{projectId}/analyses")
    public List<AnalysisDto> projectAnalyses(@PathVariable UUID projectId) {
        return ai.projectAnalyses(projectId);
    }

    @GetMapping("/projects/{projectId}/recommendations")
    public List<Recommendation> recommendations(@PathVariable UUID projectId) {
        return ai.recommendations(projectId);
    }
}
