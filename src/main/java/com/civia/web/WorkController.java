package com.civia.web;

import com.civia.domain.Comment;
import com.civia.domain.Enums;
import com.civia.domain.Project;
import com.civia.domain.Task;
import com.civia.domain.User;
import com.civia.repo.CommentRepository;
import com.civia.repo.ProjectMemberRepository;
import com.civia.repo.ProjectPhaseRepository;
import com.civia.repo.TaskRepository;
import com.civia.service.AuditService;
import com.civia.service.ProjectAccess;
import com.civia.web.Dto.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Tareas y comentarios de un proyecto. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tareas y comentarios")
public class WorkController {

    private final ProjectAccess access;
    private final TaskRepository tasks;
    private final CommentRepository comments;
    private final ProjectPhaseRepository phases;
    private final ProjectMemberRepository members;
    private final AuditService audit;

    public WorkController(ProjectAccess access, TaskRepository tasks, CommentRepository comments,
                          ProjectPhaseRepository phases, ProjectMemberRepository members, AuditService audit) {
        this.access = access;
        this.tasks = tasks;
        this.comments = comments;
        this.phases = phases;
        this.members = members;
        this.audit = audit;
    }

    @GetMapping("/projects/{id}/tasks")
    @Transactional(readOnly = true)
    public List<TaskDto> list(@PathVariable UUID id) {
        access.view(id);
        return tasks.findByProjectIdOrderByCreatedAtDesc(id).stream().map(Dto::task).toList();
    }

    @GetMapping("/tasks/mine")
    @Transactional(readOnly = true)
    public List<TaskDto> mine() {
        return tasks.findByAssigneeIdAndStatusNotOrderByDueDateAsc(access.me().getId(), Enums.TaskStatus.DONE).stream()
                .map(Dto::task).toList();
    }

    @PostMapping("/projects/{id}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TaskDto create(@PathVariable UUID id, @RequestBody TaskRequest req) {
        Project p = access.edit(id);
        if (req.title() == null || req.title().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La tarea necesita un título.");
        User me = access.me();
        Task t = new Task();
        t.setProject(p);
        t.setCreatedBy(me);
        apply(t, req, id);
        tasks.save(t);
        audit.activity(p, me, "task.created", "task", t.getId(), "Creó la tarea «" + t.getTitle() + "»");
        if (t.getAssignee() != null && !t.getAssignee().getId().equals(me.getId())) {
            audit.notify(t.getAssignee(), "task", "Nueva tarea asignada", t.getTitle() + " · " + p.getName(), "#/projects/" + id + "/tasks");
        }
        return Dto.task(t);
    }

    @PatchMapping("/projects/{id}/tasks/{taskId}")
    @Transactional
    public TaskDto update(@PathVariable UUID id, @PathVariable UUID taskId, @RequestBody TaskRequest req) {
        Project p = access.edit(id);
        Task t = tasks.findById(taskId).filter(x -> x.getProject().getId().equals(id)).orElseThrow(() -> ProjectAccess.notFound("Tarea"));
        Enums.TaskStatus before = t.getStatus();
        apply(t, req, id);
        if (t.getStatus() == Enums.TaskStatus.DONE && before != Enums.TaskStatus.DONE) {
            t.setCompletedAt(Instant.now());
            audit.activity(p, access.me(), "task.done", "task", t.getId(), "Completó «" + t.getTitle() + "»");
        } else if (t.getStatus() != Enums.TaskStatus.DONE) {
            t.setCompletedAt(null);
        }
        return Dto.task(t);
    }

    @DeleteMapping("/projects/{id}/tasks/{taskId}")
    @Transactional
    public Map<String, Object> delete(@PathVariable UUID id, @PathVariable UUID taskId) {
        access.edit(id);
        tasks.findById(taskId).filter(x -> x.getProject().getId().equals(id)).ifPresent(tasks::delete);
        return Map.of("deleted", true);
    }

    private void apply(Task t, TaskRequest r, UUID projectId) {
        if (r.title() != null && !r.title().isBlank()) t.setTitle(r.title().strip());
        if (r.description() != null) t.setDescription(r.description());
        if (r.status() != null) t.setStatus(Enums.TaskStatus.valueOf(r.status()));
        if (r.priority() != null) t.setPriority(Enums.Priority.valueOf(r.priority()));
        if (r.dueDate() != null) t.setDueDate(r.dueDate());
        if (r.assigneeId() != null) {
            t.setAssignee(members.findByProjectIdAndUserId(projectId, r.assigneeId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "El responsable debe ser miembro del proyecto."))
                    .getUser());
        }
        if (r.phaseId() != null) {
            t.setPhase(phases.findById(r.phaseId()).filter(f -> f.getProject().getId().equals(projectId)).orElse(null));
        }
    }

    @GetMapping("/projects/{id}/comments")
    @Transactional(readOnly = true)
    public List<CommentDto> comments(@PathVariable UUID id, @RequestParam(required = false) UUID targetId) {
        access.view(id);
        return comments.findByProjectIdOrderByCreatedAtDesc(id).stream()
                .filter(c -> targetId == null || targetId.equals(c.getTargetId()))
                .map(Dto::comment).toList();
    }

    @PostMapping("/projects/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CommentDto comment(@PathVariable UUID id, @RequestBody CommentRequest req) {
        Project p = access.view(id); // los lectores también pueden comentar
        if (req.body() == null || req.body().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe un comentario.");
        User me = access.me();
        Comment c = new Comment();
        c.setProject(p);
        c.setAuthor(me);
        c.setBody(req.body());
        c.setTargetType(req.targetType());
        c.setTargetId(req.targetId());
        comments.save(c);
        audit.activity(p, me, "comment.created", req.targetType(), req.targetId(), "Comentó en el proyecto");
        members.findByProjectId(id).stream().map(m -> m.getUser()).filter(u -> !u.getId().equals(me.getId()))
                .forEach(u -> audit.notify(u, "comment", me.getFullName() + " comentó en " + p.getName(),
                        req.body().length() > 140 ? req.body().substring(0, 140) + "…" : req.body(), "#/projects/" + id));
        return Dto.comment(c);
    }

    @DeleteMapping("/projects/{id}/comments/{commentId}")
    @Transactional
    public Map<String, Object> deleteComment(@PathVariable UUID id, @PathVariable UUID commentId) {
        access.view(id);
        UUID me = access.me().getId();
        Comment c = comments.findById(commentId).filter(x -> x.getProject().getId().equals(id)).orElseThrow(() -> ProjectAccess.notFound("Comentario"));
        if (!c.getAuthor().getId().equals(me)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el autor puede borrar su comentario.");
        comments.delete(c);
        return Map.of("deleted", true);
    }
}
