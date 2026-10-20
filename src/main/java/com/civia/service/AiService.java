package com.civia.service;

import com.civia.ai.CiviaAi;
import com.civia.ai.Recommendation;
import com.civia.ai.assistant.ArchitectAssistant;
import com.civia.ai.assistant.ProjectAdvisor;
import com.civia.ai.llm.LlmProvider;
import com.civia.ai.models.MassingAdvisor;
import com.civia.ai.models.ModelAnalyzer;
import com.civia.ai.plans.PlanAnalyzer;
import com.civia.ai.plans.PlanReport;
import com.civia.domain.*;
import com.civia.domain.Enums.DocumentKind;
import com.civia.repo.*;
import com.civia.storage.StorageService;
import com.civia.web.Dto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Conecta la capa de IA (CIVIA-IA) con los datos: análisis guardados, conversaciones y contexto. */
@Service
public class AiService {

    private static final long MAX_LOCAL_ANALYSIS_BYTES = 200L * 1024 * 1024;
    private static final long MAX_CHAT_ATTACHMENT_BYTES = 5L * 1024 * 1024;

    private final CiviaAi ai;
    private final ObjectMapper json;
    private final ProjectAccess access;
    private final DocumentRepository documents;
    private final AiAnalysisRepository analyses;
    private final AiFindingRepository findings;
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final TaskRepository tasks;
    private final BudgetItemRepository budgetItems;
    private final ProjectMemberRepository members;
    private final RoomRepository rooms;
    private final StorageService storage;
    private final AuditService audit;
    private final AiQuota quota;

    @Value("${civia.ai.history-turns:40}")
    private int historyTurns = 40;

    @Value("${civia.ai.max-message-chars:12000}")
    private int maxMessageChars = 12_000;

    public AiService(CiviaAi ai, ObjectMapper json, ProjectAccess access, DocumentRepository documents,
                     AiAnalysisRepository analyses, AiFindingRepository findings, AiConversationRepository conversations,
                     AiMessageRepository messages, TaskRepository tasks, BudgetItemRepository budgetItems,
                     ProjectMemberRepository members, RoomRepository rooms, StorageService storage, AuditService audit,
                     AiQuota quota) {
        this.ai = ai;
        this.json = json;
        this.access = access;
        this.documents = documents;
        this.analyses = analyses;
        this.findings = findings;
        this.conversations = conversations;
        this.messages = messages;
        this.tasks = tasks;
        this.budgetItems = budgetItems;
        this.members = members;
        this.rooms = rooms;
        this.storage = storage;
        this.audit = audit;
        this.quota = quota;
    }

    public CiviaAi engine() {
        return ai;
    }

    // ------------------------------------------------------------------ planos

    @Transactional
    public Dto.AnalysisDto analyzePlan(UUID documentId, String notes) {
        Document d = documents.findById(documentId).orElseThrow(() -> ProjectAccess.notFound("Archivo"));
        access.edit(d.getProject().getId());
        if (d.getKind() == DocumentKind.MODEL_3D) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los modelos 3D se analizan desde el visor 3D.");
        }
        if (d.getSizeBytes() > MAX_LOCAL_ANALYSIS_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo es demasiado grande para analizarlo (máx. 200 MB).");
        }
        String extra = notes;
        if (d.getDescription() != null && !d.getDescription().isBlank()) {
            extra = (extra == null ? "" : extra + "\n") + "Descripción del archivo: " + d.getDescription();
        }
        PlanReport report = ai.plans().analyze(new PlanAnalyzer.PlanInput(d.getName(), d.getMediaType(), storage.read(d.getStorageKey()), extra));

        AiAnalysis a = new AiAnalysis();
        a.setProject(d.getProject());
        a.setDocument(d);
        a.setAnalysisType("PLAN");
        a.setEngine(report.engine());
        a.setSummary(report.summary());
        a.setCompleteness(report.completeness());
        a.setResultJson(write(report));
        a.setCreatedBy(access.me());
        analyses.save(a);
        for (PlanReport.Check c : report.checks()) {
            AiFinding f = new AiFinding();
            f.setAnalysis(a);
            f.setCheckId(c.id());
            f.setTitle(c.title());
            f.setStatus(c.status().name());
            f.setDetail(c.detail());
            f.setRecommendation(c.recommendation());
            findings.save(f);
        }
        if ((d.getPlanType() == null || d.getPlanType().isBlank()) && !"general".equals(report.documentType())) {
            d.setPlanType(report.documentType());
        }
        audit.activity(d.getProject(), access.me(), "ai.plan-analyzed", "document", d.getId(),
                "CIVIA analizó «" + d.getName() + "»: " + report.completeness() + " % completo");
        return toDto(a, report);
    }

    // ------------------------------------------------------------------ modelos 3D

    @Transactional
    public Dto.AnalysisDto analyzeModel(UUID documentId, ModelAnalyzer.ModelStats stats) {
        Document d = documents.findById(documentId).orElseThrow(() -> ProjectAccess.notFound("Archivo"));
        Project p = access.edit(d.getProject().getId());
        ModelAnalyzer.ModelReport report = ai.models().analyze(stats, p.getDescription());
        AiAnalysis a = new AiAnalysis();
        a.setProject(p);
        a.setDocument(d);
        a.setAnalysisType("MODEL");
        a.setEngine(report.engine());
        a.setSummary(report.summary());
        a.setResultJson(write(report));
        a.setCreatedBy(access.me());
        analyses.save(a);
        audit.activity(p, access.me(), "ai.model-analyzed", "document", d.getId(), "CIVIA revisó el modelo «" + d.getName() + "»");
        return toDto(a, report);
    }

    public MassingAdvisor.MassingProposal massing(MassingAdvisor.MassingInput input) {
        return ai.massing().propose(input);
    }

    @Transactional(readOnly = true)
    public List<Dto.AnalysisDto> projectAnalyses(UUID projectId) {
        access.view(projectId);
        return analyses.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(a -> toDto(a, read(a.getResultJson()))).toList();
    }

    @Transactional(readOnly = true)
    public Dto.AnalysisDto analysis(UUID id) {
        AiAnalysis a = analyses.findById(id).orElseThrow(() -> ProjectAccess.notFound("Análisis"));
        access.view(a.getProject().getId());
        return toDto(a, read(a.getResultJson()));
    }

    @Transactional
    public void resolveFinding(UUID analysisId, String checkId, boolean resolved) {
        AiAnalysis a = analyses.findById(analysisId).orElseThrow(() -> ProjectAccess.notFound("Análisis"));
        access.edit(a.getProject().getId());
        findings.findByAnalysisId(analysisId).stream().filter(f -> checkId.equals(f.getCheckId())).forEach(f -> f.setResolved(resolved));
    }

    // ------------------------------------------------------------------ proyecto

    @Transactional(readOnly = true)
    public List<Recommendation> recommendations(UUID projectId) {
        Project p = access.view(projectId);
        return ai.projects().review(snapshot(p));
    }

    ProjectAdvisor.ProjectSnapshot snapshot(Project p) {
        UUID id = p.getId();
        List<Document> docs = documents.findByProjectIdOrderByCreatedAtDesc(id);
        List<Document> plans = docs.stream().filter(d -> d.getKind() == DocumentKind.PLAN).toList();
        // Último análisis de cada plano → hallazgos "FALTA" sin resolver.
        Map<UUID, AiAnalysis> latest = new HashMap<>();
        for (AiAnalysis a : analyses.findByProjectIdOrderByCreatedAtDesc(id)) {
            if ("PLAN".equals(a.getAnalysisType()) && a.getDocument() != null) latest.putIfAbsent(a.getDocument().getId(), a);
        }
        long pending = latest.isEmpty() ? 0 : findings.countByAnalysisIdInAndStatusAndResolvedFalse(
                latest.values().stream().map(AiAnalysis::getId).toList(), "FALTA");
        List<String> planTypes = plans.stream().map(Document::getPlanType).filter(t -> t != null && !t.isBlank()).distinct().toList();
        return new ProjectAdvisor.ProjectSnapshot(p.getName(), p.getType(), p.getPhase(), p.getBuiltArea(), p.getFloors(),
                p.getLocation(), plans.size(),
                (int) docs.stream().filter(d -> d.getKind() == DocumentKind.MODEL_3D).count(),
                (int) docs.stream().filter(d -> d.getKind() == DocumentKind.PHOTO).count(),
                (int) docs.stream().filter(d -> d.getKind() == DocumentKind.DOCUMENT).count(),
                (int) tasks.countByProjectIdAndStatusNot(id, Enums.TaskStatus.DONE),
                (int) tasks.countByProjectIdAndStatusNotAndDueDateBefore(id, Enums.TaskStatus.DONE, LocalDate.now()),
                (int) budgetItems.countByChapterProjectId(id),
                (int) members.countByProjectId(id),
                latest.size(), (int) pending, planTypes);
    }

    private String describe(Project p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nombre: ").append(p.getName()).append('\n');
        if (p.getType() != null) sb.append("Tipo: ").append(p.getType()).append('\n');
        if (p.getPhase() != null) sb.append("Fase: ").append(p.getPhase()).append('\n');
        if (p.getLocation() != null) sb.append("Ubicación: ").append(p.getLocation()).append('\n');
        if (p.getLotArea() != null) sb.append("Área del lote: ").append(p.getLotArea()).append(" m²\n");
        if (p.getBuiltArea() != null) sb.append("Área construida: ").append(p.getBuiltArea()).append(" m²\n");
        if (p.getFloors() != null) sb.append("Pisos: ").append(p.getFloors()).append('\n');
        if (p.getDescription() != null) sb.append("Descripción: ").append(p.getDescription()).append('\n');
        List<Room> rs = rooms.findByProjectIdOrderByNameAsc(p.getId());
        if (!rs.isEmpty()) {
            sb.append("Espacios: ");
            rs.forEach(r -> sb.append(r.getName()).append(r.getArea() == null ? "" : " (" + r.getArea() + " m²)").append("; "));
            sb.append('\n');
        }
        documents.findByProjectIdOrderByCreatedAtDesc(p.getId()).stream().limit(30).forEach(d -> sb.append("Archivo: ")
                .append(d.getName()).append(" [").append(d.getKind()).append(d.getPlanType() == null ? "" : ", " + d.getPlanType()).append("]\n"));
        return sb.toString();
    }

    // ------------------------------------------------------------------ copiloto

    /** Pregunta lista para el Copilot: la conversación ya existe y el mensaje del usuario quedó guardado. */
    public record PreparedChat(UUID conversationId, String message, List<LlmProvider.Turn> history,
                               ArchitectAssistant.ProjectContext project, List<LlmProvider.Attachment> attachments) {}

    @Transactional
    public Dto.ChatResponse chat(Dto.ChatRequest req) {
        PreparedChat chat = prepareChat(req);
        ArchitectAssistant.Answer answer = ai.assistant().ask(chat.message(), chat.history(), chat.project(), chat.attachments());
        return saveReply(chat.conversationId(), answer);
    }

    /** Valida el acceso, crea o recupera la conversación y guarda el mensaje del usuario. */
    @Transactional
    public PreparedChat prepareChat(Dto.ChatRequest req) {
        if (req.message() == null || req.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un mensaje.");
        }
        if (req.message().length() > maxMessageChars) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El mensaje es muy largo (máximo " + maxMessageChars + " caracteres). Divídelo en partes.");
        }
        User me = access.me();
        quota.acquire(me.getId());
        AiConversation conv;
        if (req.conversationId() != null) {
            conv = conversations.findById(req.conversationId()).filter(c -> c.getUser().getId().equals(me.getId()))
                    .orElseThrow(() -> ProjectAccess.notFound("Conversación"));
        } else {
            conv = new AiConversation();
            conv.setUser(me);
            String t = req.message().strip().replaceAll("\\s+", " ");
            conv.setTitle(t.length() > 70 ? t.substring(0, 70) + "…" : t);
            if (req.projectId() != null) conv.setProject(access.view(req.projectId()));
            conversations.save(conv);
        }
        Project project = conv.getProject();
        if (req.projectId() != null && project == null) {
            project = access.view(req.projectId());
            conv.setProject(project);
        }

        List<LlmProvider.Turn> history = new ArrayList<>();
        List<AiMessage> previous = messages.findByConversationIdOrderByCreatedAtAsc(conv.getId());
        for (AiMessage m : previous.subList(Math.max(0, previous.size() - historyTurns), previous.size())) {
            history.add(new LlmProvider.Turn(m.getRole(), m.getContent()));
        }

        ArchitectAssistant.ProjectContext ctx = null;
        if (project != null) {
            access.view(project.getId());
            ctx = new ArchitectAssistant.ProjectContext(describe(project), ai.projects().review(snapshot(project)));
        }

        List<LlmProvider.Attachment> attachments = new ArrayList<>();
        if (req.documentIds() != null && ai.llmEnabled()) {
            for (UUID docId : req.documentIds()) {
                Document d = documents.findById(docId).orElse(null);
                if (d == null) continue;
                access.view(d.getProject().getId());
                boolean visual = d.getMediaType().startsWith("image/") || d.getMediaType().equals("application/pdf");
                long limit = d.getMediaType().equals("application/pdf") ? 24L * 1024 * 1024 : MAX_CHAT_ATTACHMENT_BYTES;
                if (visual && d.getSizeBytes() <= limit) {
                    attachments.add(new LlmProvider.Attachment(d.getMediaType(), storage.read(d.getStorageKey()), d.getName()));
                }
            }
        }

        AiMessage userMsg = new AiMessage();
        userMsg.setConversation(conv);
        userMsg.setRole("user");
        userMsg.setContent(req.message());
        messages.save(userMsg);
        return new PreparedChat(conv.getId(), req.message(), history, ctx, attachments);
    }

    /** Guarda la respuesta del Copilot. No depende del usuario en sesión: sirve también en streaming. */
    @Transactional
    public Dto.ChatResponse saveReply(UUID conversationId, ArchitectAssistant.Answer answer) {
        AiConversation conv = conversations.findById(conversationId).orElseThrow(() -> ProjectAccess.notFound("Conversación"));
        AiMessage reply = new AiMessage();
        reply.setConversation(conv);
        reply.setRole("assistant");
        reply.setContent(answer.text());
        reply.setEngine(answer.engine());
        reply.setSourcesJson(write(Map.of("sources", answer.sources(), "suggestions", answer.suggestions())));
        messages.save(reply);
        return new Dto.ChatResponse(conv.getId(), message(reply));
    }

    @Transactional(readOnly = true)
    public List<Dto.ConversationDto> conversations() {
        return conversations.findByUserIdOrderByUpdatedAtDesc(access.me().getId()).stream()
                .map(c -> new Dto.ConversationDto(c.getId(), c.getTitle(), c.getProject() == null ? null : c.getProject().getId(),
                        c.getProject() == null ? null : c.getProject().getName(), c.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<Dto.MessageDto> conversation(UUID id) {
        AiConversation c = conversations.findById(id).filter(x -> x.getUser().getId().equals(access.me().getId()))
                .orElseThrow(() -> ProjectAccess.notFound("Conversación"));
        return messages.findByConversationIdOrderByCreatedAtAsc(c.getId()).stream().map(this::message).toList();
    }

    @Transactional
    public void deleteConversation(UUID id) {
        AiConversation c = conversations.findById(id).filter(x -> x.getUser().getId().equals(access.me().getId()))
                .orElseThrow(() -> ProjectAccess.notFound("Conversación"));
        messages.deleteByConversationId(c.getId());
        conversations.delete(c);
    }

    // ------------------------------------------------------------------ utilidades

    private Dto.MessageDto message(AiMessage m) {
        Map<String, Object> meta = m.getSourcesJson() == null ? Map.of() : readMap(m.getSourcesJson());
        @SuppressWarnings("unchecked")
        List<String> suggestions = (List<String>) meta.getOrDefault("suggestions", List.of());
        return new Dto.MessageDto(m.getId(), m.getRole(), m.getContent(), m.getEngine(), meta.get("sources"), suggestions, m.getCreatedAt());
    }

    private Dto.AnalysisDto toDto(AiAnalysis a, Object result) {
        return new Dto.AnalysisDto(a.getId(), a.getAnalysisType(), a.getEngine(), a.getSummary(), a.getCompleteness(),
                a.getDocument() == null ? null : a.getDocument().getId(),
                a.getDocument() == null ? null : a.getDocument().getName(), result, a.getCreatedAt());
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object read(String s) {
        if (s == null) return null;
        try {
            return json.readValue(s, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Object> readMap(String s) {
        try {
            return json.readValue(s, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
