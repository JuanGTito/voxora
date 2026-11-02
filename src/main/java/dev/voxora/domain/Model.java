package dev.voxora.domain;

import java.time.Instant;

/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class Model {
  private Model() {}

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public enum Role {
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    SUPERADMIN("Superadministrador"),
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    SUPERVISOR("Supervisor"),
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    TECHNICIAN("Técnico");
    private final String label;

    Role(String label) {
      this.label = label;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public boolean managesIncidents() {
      return this != TECHNICIAN;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    @Override
    public String toString() {
      return label;
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public enum Status {
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    PENDIENTE("Pendiente"),
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    ASIGNADO("Asignado"),
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    EN_PROCESO("En proceso"),
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    EN_REVISION("En revisión"),
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    COMPLETADO("Completado");
    private final String label;

    Status(String label) {
      this.label = label;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    @Override
    public String toString() {
      return label;
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public enum Priority {
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    CRITICA,
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    ALTA,
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    MEDIA,
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    BAJA
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public enum Specialty {
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    MECANICA("Mecánica"),
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    ELECTRICA("Eléctrica"),
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    GENERAL("General");
    private final String label;

    Specialty(String label) {
      this.label = label;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    @Override
    public String toString() {
      return label;
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public record User(long id, String username, Role role, Specialty specialty, boolean active) {
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    @Override
    public String toString() {
      return username + " · " + role;
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public record Account(User user, String hash) {}

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public record Incident(
      long id,
      String title,
      String equipment,
      String location,
      String description,
      Priority priority,
      Specialty specialty,
      Status status,
      Long assignedTo,
      String technician,
      long createdBy,
      Instant createdAt,
      Instant updatedAt,
      Instant startedAt,
      Instant finishedAt,
      String draft,
      Long submittedVersion,
      String observation,
      int revision) {
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public String code() {
      return "VX-%05d".formatted(id);
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public record Version(
      long id,
      long incidentId,
      int number,
      String content,
      long authorId,
      String author,
      String kind,
      Instant createdAt) {}

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public record Event(
      long id, Long incidentId, String actor, String action, String detail, Instant at) {}

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public record VoiceEntry(
      long id,
      long incidentId,
      long actorId,
      String audioPath,
      String rawText,
      String correctedText,
      String sourceHash,
      String extractionJson,
      String proposal,
      Instant createdAt) {}

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static final class RuleViolation extends RuntimeException {
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public RuleViolation(String message) {
      super(message);
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static void require(boolean condition, String message) {
    if (!condition) throw new RuleViolation(message);
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static String required(String text, String field, int max) {
    String value = text == null ? "" : text.strip();
    require(!value.isEmpty(), "Completa «" + field + "».");
    require(value.length() <= max, "«" + field + "» admite hasta " + max + " caracteres.");
    return value;
  }
}
