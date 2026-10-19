package com.civia.service;

import com.civia.domain.ActivityLog;
import com.civia.domain.AuditLog;
import com.civia.domain.Notification;
import com.civia.domain.Project;
import com.civia.domain.SecurityEvent;
import com.civia.domain.User;
import com.civia.repo.ActivityLogRepository;
import com.civia.repo.AuditLogRepository;
import com.civia.repo.NotificationRepository;
import com.civia.repo.SecurityEventRepository;
import com.civia.security.Hashing;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Auditoría encadenada, historial de seguridad, bitácora de proyecto y notificaciones. */
@Service
public class AuditService {

    private final AuditLogRepository audit;
    private final SecurityEventRepository securityEvents;
    private final ActivityLogRepository activity;
    private final NotificationRepository notifications;

    public AuditService(AuditLogRepository audit, SecurityEventRepository securityEvents,
                        ActivityLogRepository activity, NotificationRepository notifications) {
        this.audit = audit;
        this.securityEvents = securityEvents;
        this.activity = activity;
        this.notifications = notifications;
    }

    /** Registro append-only: cada fila incluye el hash de la anterior. */
    @Transactional
    public synchronized void audit(UUID userId, String action, String ip, String detail) {
        String prev = audit.findFirstByOrderByCreatedAtDescIdDesc().map(AuditLog::getHash).orElse("GENESIS");
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setIpAddress(ip);
        log.setDetail(detail);
        log.setPrevHash(prev);
        log.setHash(Hashing.sha256(prev + "|" + userId + "|" + action + "|" + ip + "|" + detail + "|" + Instant.now()));
        audit.save(log);
    }

    @Transactional
    public void security(User user, String type, String ip, String userAgent, String detail) {
        SecurityEvent e = new SecurityEvent();
        e.setUser(user);
        e.setType(type);
        e.setIpAddress(ip);
        e.setUserAgent(userAgent);
        e.setDetail(detail);
        securityEvents.save(e);
        audit(user == null ? null : user.getId(), "security." + type, ip, detail);
    }

    @Transactional
    public void activity(Project project, User user, String action, String entityType, UUID entityId, String detail) {
        ActivityLog a = new ActivityLog();
        a.setProject(project);
        a.setUser(user);
        a.setAction(action);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setDetail(detail);
        activity.save(a);
    }

    @Transactional
    public void notify(User user, String type, String title, String body, String link) {
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        notifications.save(n);
    }
}
