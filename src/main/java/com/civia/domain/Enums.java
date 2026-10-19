package com.civia.domain;

/** Enumeraciones del dominio (se guardan como texto). */
public final class Enums {
    private Enums() {}

    public enum UserRole { ADMIN, ARCHITECT, ENGINEER, CLIENT, VIEWER }

    public enum UserStatus { ACTIVE, LOCKED, DISABLED }

    public enum ProjectRole { OWNER, EDITOR, VIEWER }

    public enum ProjectStatus { PLANNING, DESIGN, PERMITS, CONSTRUCTION, COMPLETED, ON_HOLD }

    public enum TaskStatus { TODO, IN_PROGRESS, REVIEW, DONE }

    public enum Priority { LOW, MEDIUM, HIGH, URGENT }

    public enum DocumentKind { PLAN, MODEL_3D, PHOTO, DOCUMENT }
}
