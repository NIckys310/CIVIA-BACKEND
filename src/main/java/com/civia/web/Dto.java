package com.civia.web;

import com.civia.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Objetos de la API. Las entidades nunca se serializan directamente. */
public final class Dto {

    private Dto() {}

    // ------------------------------------------------------------------ CIVIA Auth

    public record RegisterRequest(String fullName, String email, String password, String profession, String organization) {}

    /** Petición al endpoint de tokens (estilo OAuth 2.0): grant_type "password" o "refresh_token". */
    public record TokenRequest(String grant_type, String email, String password, String mfa_code) {}

    public record TokenResponse(String access_token, String token_type, long expires_in, UserDto user, Boolean mfa_required) {
        public static TokenResponse bearer(String token, long expiresIn, UserDto user) {
            return new TokenResponse(token, "Bearer", expiresIn, user, null);
        }

        public static TokenResponse mfa() {
            return new TokenResponse(null, null, 0, null, true);
        }
    }

    public record ProfileDto(String bio, String company, String professionalId, String city, String country, String website) {}

    public record PreferencesDto(String theme, String language, String units, Boolean emailNotifications, Boolean compactMode) {}

    public record UserDto(UUID id, String email, String fullName, String phone, String profession, String avatarColor,
                          String role, boolean mfaEnabled, Instant createdAt, Instant lastLoginAt, String organization,
                          ProfileDto profile, PreferencesDto preferences) {}

    public record UpdateUserRequest(String fullName, String phone, String profession, ProfileDto profile, PreferencesDto preferences) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record ForgotPasswordRequest(String email) {}

    public record ResetPasswordRequest(String token, String password) {}

    public record SessionDto(UUID id, String userAgent, String ipAddress, Instant createdAt, Instant lastUsedAt, Instant expiresAt) {}

    public record SecurityEventDto(String type, String detail, String ipAddress, String userAgent, Instant createdAt) {}

    public record MfaCodeRequest(String code) {}

    public record PasswordConfirmRequest(String password) {}

    // ------------------------------------------------------------------ Proyectos

    public record ProjectRequest(String name, String description, String type, String status, String phase, String location,
                                 String address, Double latitude, Double longitude, Double lotArea, Double builtArea,
                                 Integer floors, BigDecimal budgetEstimate, String currency, LocalDate startDate,
                                 LocalDate dueDate, String coverColor, String code, UUID clientId, Boolean archived) {}

    public record ProjectDto(UUID id, String code, String name, String description, String type, String status, String phase,
                             String location, String address, Double latitude, Double longitude, Double lotArea,
                             Double builtArea, Integer floors, BigDecimal budgetEstimate, String currency,
                             LocalDate startDate, LocalDate dueDate, String coverColor, boolean archived, String role,
                             String ownerName, String clientName, Instant createdAt, Instant updatedAt, ProjectStats stats) {}

    public record ProjectStats(long plans, long models, long photos, long documents, long openTasks, long members) {}

    public record MemberDto(UUID id, UUID userId, String fullName, String email, String role, String avatarColor) {}

    public record AddMemberRequest(String email, String role) {}

    public record PhaseDto(UUID id, String name, int sortOrder, String status, LocalDate startDate, LocalDate endDate) {}

    public record TaskRequest(String title, String description, String status, String priority, UUID assigneeId,
                              UUID phaseId, LocalDate dueDate) {}

    public record TaskDto(UUID id, String title, String description, String status, String priority, UUID assigneeId,
                          String assigneeName, UUID phaseId, String phaseName, LocalDate dueDate, Instant completedAt,
                          Instant createdAt) {}

    public record CommentRequest(String body, String targetType, UUID targetId) {}

    public record CommentDto(UUID id, String body, String authorName, String authorColor, UUID authorId,
                             String targetType, UUID targetId, Instant createdAt) {}

    public record LevelDto(UUID id, String name, Double elevation, Double height, int sortOrder) {}

    public record RoomDto(UUID id, String name, String roomType, Double area, String notes, UUID levelId, String levelName) {}

    public record ActivityDto(UUID id, String action, String detail, String entityType, UUID entityId, String userName,
                              UUID projectId, String projectName, Instant createdAt) {}

    // ------------------------------------------------------------------ Archivos

    public record DocumentDto(UUID id, UUID projectId, String name, String kind, String mediaType, String extension,
                              long sizeBytes, String sha256, String planType, String scale, String discipline,
                              String description, int version, String uploadedBy, Instant createdAt,
                              AnalysisBrief lastAnalysis) {}

    public record AnalysisBrief(UUID id, String engine, Integer completeness, String summary, Instant createdAt) {}

    public record DocumentUpdate(String name, String description, String planType, String scale, String discipline) {}

    public record MeasurementRequest(UUID documentId, String label, double value, String unit, Double referenceLength, String pointsJson) {}

    public record MeasurementDto(UUID id, UUID documentId, String label, double value, String unit, Double referenceLength,
                                 String pointsJson, String authorName, Instant createdAt) {}

    public record AnnotationRequest(double x, double y, double z, String text) {}

    public record AnnotationDto(UUID id, double x, double y, double z, String text, boolean resolved, String authorName, Instant createdAt) {}

    // ------------------------------------------------------------------ IA

    public record ChatRequest(UUID conversationId, UUID projectId, String message, List<UUID> documentIds) {}

    public record ChatResponse(UUID conversationId, MessageDto message) {}

    public record MessageDto(UUID id, String role, String content, String engine, Object sources, List<String> suggestions,
                             Instant createdAt, Integer feedback) {}

    /** {@code value}: "up", "down" o null para quitar la valoración. */
    public record FeedbackRequest(String value) {}

    public record ConversationDto(UUID id, String title, UUID projectId, String projectName, Instant updatedAt, boolean pinned) {}

    /** Cambios en una conversación: título y/o fijada. Los campos nulos no cambian. */
    public record ConversationUpdate(String title, Boolean pinned) {}

    public record AnalysisDto(UUID id, String analysisType, String engine, String summary, Integer completeness,
                              UUID documentId, String documentName, Object result, Instant createdAt) {}

    public record AskRequest(String question) {}

    // ------------------------------------------------------------------ Biblioteca

    public record CategoryDto(String id, String name, String icon, String description, long articles) {}

    public record ArticleSummary(String slug, String category, String title, String summary, List<String> tags, boolean custom) {}

    public record ArticleDto(String slug, String category, String title, String summary, List<String> tags, String body,
                             boolean custom, boolean favorite, List<ArticleSummary> related) {}

    public record ArticleRequest(String category, String title, String summary, String body, List<String> tags) {}

    public record TermDto(String term, String category, String definition) {}

    // ------------------------------------------------------------------ Presupuesto

    public record ChapterRequest(String code, String name, Integer sortOrder) {}

    public record ItemRequest(UUID chapterId, String code, String description, String unit, BigDecimal quantity, BigDecimal unitPrice) {}

    public record ItemDto(UUID id, UUID chapterId, String code, String description, String unit, BigDecimal quantity,
                          BigDecimal unitPrice, BigDecimal total) {}

    public record ChapterDto(UUID id, String code, String name, int sortOrder, BigDecimal subtotal, List<ItemDto> items) {}

    public record BudgetSettingsDto(BigDecimal administrationPct, BigDecimal contingencyPct, BigDecimal profitPct, BigDecimal taxOnProfitPct) {}

    public record BudgetDto(List<ChapterDto> chapters, BigDecimal directCost, BigDecimal administration, BigDecimal contingency,
                            BigDecimal profit, BigDecimal taxOnProfit, BigDecimal total, String currency,
                            BudgetSettingsDto settings, Double costPerM2) {}

    // ------------------------------------------------------------------ Otros

    public record NotificationDto(UUID id, String type, String title, String body, String link, boolean read, Instant createdAt) {}

    public record ClientRequest(String name, String email, String phone, String company, String notes) {}

    public record ClientDto(UUID id, String name, String email, String phone, String company, String notes) {}

    // ------------------------------------------------------------------ Mapeos

    public static UserDto user(User u, UserProfile p, UserPreference pref) {
        return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getProfession(), u.getAvatarColor(),
                u.getRole().name(), u.isMfaEnabled(), u.getCreatedAt(), u.getLastLoginAt(),
                u.getOrganization() == null ? null : u.getOrganization().getName(),
                p == null ? null : new ProfileDto(p.getBio(), p.getCompany(), p.getProfessionalId(), p.getCity(), p.getCountry(), p.getWebsite()),
                pref == null ? null : new PreferencesDto(pref.getTheme(), pref.getLanguage(), pref.getUnits(), pref.isEmailNotifications(), pref.isCompactMode()));
    }

    public static ProjectDto project(Project p, String role, ProjectStats stats) {
        return new ProjectDto(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getType(),
                p.getStatus() == null ? null : p.getStatus().name(), p.getPhase(), p.getLocation(), p.getAddress(),
                p.getLatitude(), p.getLongitude(), p.getLotArea(), p.getBuiltArea(), p.getFloors(), p.getBudgetEstimate(),
                p.getCurrency(), p.getStartDate(), p.getDueDate(), p.getCoverColor(), p.isArchived(), role,
                p.getOwner().getFullName(), p.getClient() == null ? null : p.getClient().getName(),
                p.getCreatedAt(), p.getUpdatedAt(), stats);
    }

    public static TaskDto task(Task t) {
        return new TaskDto(t.getId(), t.getTitle(), t.getDescription(), t.getStatus().name(), t.getPriority().name(),
                t.getAssignee() == null ? null : t.getAssignee().getId(),
                t.getAssignee() == null ? null : t.getAssignee().getFullName(),
                t.getPhase() == null ? null : t.getPhase().getId(),
                t.getPhase() == null ? null : t.getPhase().getName(),
                t.getDueDate(), t.getCompletedAt(), t.getCreatedAt());
    }

    public static CommentDto comment(Comment c) {
        return new CommentDto(c.getId(), c.getBody(), c.getAuthor().getFullName(), c.getAuthor().getAvatarColor(),
                c.getAuthor().getId(), c.getTargetType(), c.getTargetId(), c.getCreatedAt());
    }

    public static DocumentDto document(Document d, AnalysisBrief last) {
        return new DocumentDto(d.getId(), d.getProject().getId(), d.getName(), d.getKind().name(), d.getMediaType(),
                d.getExtension(), d.getSizeBytes(), d.getSha256(), d.getPlanType(), d.getScale(), d.getDiscipline(),
                d.getDescription(), d.getCurrentVersion(),
                d.getUploadedBy() == null ? null : d.getUploadedBy().getFullName(), d.getCreatedAt(), last);
    }

    public static AnalysisBrief brief(AiAnalysis a) {
        return a == null ? null : new AnalysisBrief(a.getId(), a.getEngine(), a.getCompleteness(), a.getSummary(), a.getCreatedAt());
    }

    public static ActivityDto activity(ActivityLog a) {
        return new ActivityDto(a.getId(), a.getAction(), a.getDetail(), a.getEntityType(), a.getEntityId(),
                a.getUser() == null ? "CIVIA" : a.getUser().getFullName(),
                a.getProject() == null ? null : a.getProject().getId(),
                a.getProject() == null ? null : a.getProject().getName(), a.getCreatedAt());
    }

    public static NotificationDto notification(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.getReadAt() != null, n.getCreatedAt());
    }
}
