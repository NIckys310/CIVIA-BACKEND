package com.civia.service;

import com.civia.domain.Enums.ProjectRole;
import com.civia.domain.Project;
import com.civia.domain.ProjectMember;
import com.civia.domain.User;
import com.civia.repo.ProjectMemberRepository;
import com.civia.repo.ProjectRepository;
import com.civia.repo.UserRepository;
import com.civia.security.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Control de acceso por proyecto. Un proyecto ajeno responde 404 (no 403) para no revelar que existe.
 */
@Service
public class ProjectAccess {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserRepository users;

    public ProjectAccess(ProjectRepository projects, ProjectMemberRepository members, UserRepository users) {
        this.projects = projects;
        this.members = members;
        this.users = users;
    }

    public User me() {
        return users.findById(CurrentUser.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida"));
    }

    public ProjectRole roleIn(UUID projectId, UUID userId) {
        return members.findByProjectIdAndUserId(projectId, userId).map(ProjectMember::getRole)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
    }

    /** Lectura: cualquier miembro. */
    public Project view(UUID projectId) {
        roleIn(projectId, CurrentUser.id());
        return load(projectId);
    }

    /** Escritura: propietario o editor. */
    public Project edit(UUID projectId) {
        ProjectRole role = roleIn(projectId, CurrentUser.id());
        if (role == ProjectRole.VIEWER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu rol en este proyecto es de solo lectura.");
        }
        return load(projectId);
    }

    /** Administración (miembros, borrar el proyecto): solo el propietario. */
    public Project own(UUID projectId) {
        if (roleIn(projectId, CurrentUser.id()) != ProjectRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el propietario puede hacer esto.");
        }
        return load(projectId);
    }

    private Project load(UUID id) {
        return projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
    }

    public static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " no encontrado");
    }
}
