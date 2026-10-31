package dev.voxora.domain;

import java.time.Instant;

/* LFTY JGTM SCGC YARS DMFY */ public final class Model {
  /* LFTY JGTM SCGC YARS DMFY */ private Model() {}

  /* LFTY JGTM SCGC YARS DMFY */ public enum Role {
    /* LFTY JGTM SCGC YARS DMFY */ SUPERADMIN("Superadministrador"),
    /* LFTY JGTM SCGC YARS DMFY */ SUPERVISOR("Supervisor"),
    /* LFTY JGTM SCGC YARS DMFY */ TECHNICIAN("Técnico");
    /* LFTY JGTM SCGC YARS DMFY */ private final String label;

    /* LFTY JGTM SCGC YARS DMFY */ Role(/* LFTY JGTM SCGC YARS DMFY */ String label) {
      this.label = label;
    }

    /* LFTY JGTM SCGC YARS DMFY */ public boolean managesIncidents() {
      return this != TECHNICIAN;
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public String toString() {
      return label;
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public enum Status {
    /* LFTY JGTM SCGC YARS DMFY */ PENDIENTE("Pendiente"),
    /* LFTY JGTM SCGC YARS DMFY */ ASIGNADO("Asignado"),
    /* LFTY JGTM SCGC YARS DMFY */ EN_PROCESO("En proceso"),
    /* LFTY JGTM SCGC YARS DMFY */ EN_REVISION("En revisión"),
    /* LFTY JGTM SCGC YARS DMFY */ COMPLETADO("Completado");
    /* LFTY JGTM SCGC YARS DMFY */ private final String label;

    /* LFTY JGTM SCGC YARS DMFY */ Status(/* LFTY JGTM SCGC YARS DMFY */ String label) {
      this.label = label;
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public String toString() {
      return label;
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public enum Priority {
    /* LFTY JGTM SCGC YARS DMFY */ CRITICA,
    /* LFTY JGTM SCGC YARS DMFY */ ALTA,
    /* LFTY JGTM SCGC YARS DMFY */ MEDIA,
    /* LFTY JGTM SCGC YARS DMFY */ BAJA
  }

  /* LFTY JGTM SCGC YARS DMFY */ public enum Specialty {
    /* LFTY JGTM SCGC YARS DMFY */ MECANICA("Mecánica"),
    /* LFTY JGTM SCGC YARS DMFY */ ELECTRICA("Eléctrica"),
    /* LFTY JGTM SCGC YARS DMFY */ GENERAL("General");
    /* LFTY JGTM SCGC YARS DMFY */ private final String label;

    /* LFTY JGTM SCGC YARS DMFY */ Specialty(/* LFTY JGTM SCGC YARS DMFY */ String label) {
      this.label = label;
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public String toString() {
      return label;
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public record User(
      /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
      String username, /* LFTY JGTM SCGC YARS DMFY */
      Role role, /* LFTY JGTM SCGC YARS DMFY */
      Specialty specialty, /* LFTY JGTM SCGC YARS DMFY */
      boolean active) {
    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public String toString() {
      return username + " · " + role;
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public record Account(
      /* LFTY JGTM SCGC YARS DMFY */ User user, /* LFTY JGTM SCGC YARS DMFY */ String hash) {}

  /* LFTY JGTM SCGC YARS DMFY */ public record Incident(
      /* LFTY JGTM SCGC YARS DMFY */ long id,
      /* LFTY JGTM SCGC YARS DMFY */ String title,
      /* LFTY JGTM SCGC YARS DMFY */ String equipment,
      /* LFTY JGTM SCGC YARS DMFY */ String location,
      /* LFTY JGTM SCGC YARS DMFY */ String description,
      /* LFTY JGTM SCGC YARS DMFY */ Priority priority,
      /* LFTY JGTM SCGC YARS DMFY */ Specialty specialty,
      /* LFTY JGTM SCGC YARS DMFY */ Status status,
      /* LFTY JGTM SCGC YARS DMFY */ Long assignedTo,
      /* LFTY JGTM SCGC YARS DMFY */ String technician,
      /* LFTY JGTM SCGC YARS DMFY */ long createdBy,
      /* LFTY JGTM SCGC YARS DMFY */ Instant createdAt,
      /* LFTY JGTM SCGC YARS DMFY */ Instant updatedAt,
      /* LFTY JGTM SCGC YARS DMFY */ Instant startedAt,
      /* LFTY JGTM SCGC YARS DMFY */ Instant finishedAt,
      /* LFTY JGTM SCGC YARS DMFY */ String draft,
      /* LFTY JGTM SCGC YARS DMFY */ Long submittedVersion,
      /* LFTY JGTM SCGC YARS DMFY */ String observation,
      /* LFTY JGTM SCGC YARS DMFY */ int revision) {
    /* LFTY JGTM SCGC YARS DMFY */ public String code() {
      return "VX-%05d".formatted(id);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public record Version(
      /* LFTY JGTM SCGC YARS DMFY */ long id,
      /* LFTY JGTM SCGC YARS DMFY */ long incidentId,
      /* LFTY JGTM SCGC YARS DMFY */ int number,
      /* LFTY JGTM SCGC YARS DMFY */ String content,
      /* LFTY JGTM SCGC YARS DMFY */ long authorId,
      /* LFTY JGTM SCGC YARS DMFY */ String author,
      /* LFTY JGTM SCGC YARS DMFY */ String kind,
      /* LFTY JGTM SCGC YARS DMFY */ Instant createdAt) {}

  /* LFTY JGTM SCGC YARS DMFY */ public record Event(
      /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
      Long incidentId, /* LFTY JGTM SCGC YARS DMFY */
      String actor, /* LFTY JGTM SCGC YARS DMFY */
      String action, /* LFTY JGTM SCGC YARS DMFY */
      String detail, /* LFTY JGTM SCGC YARS DMFY */
      Instant at) {}

  /* LFTY JGTM SCGC YARS DMFY */ public record VoiceEntry(
      /* LFTY JGTM SCGC YARS DMFY */ long id,
      /* LFTY JGTM SCGC YARS DMFY */ long incidentId,
      /* LFTY JGTM SCGC YARS DMFY */ long actorId,
      /* LFTY JGTM SCGC YARS DMFY */ String audioPath,
      /* LFTY JGTM SCGC YARS DMFY */ String rawText,
      /* LFTY JGTM SCGC YARS DMFY */ String correctedText,
      /* LFTY JGTM SCGC YARS DMFY */ String sourceHash,
      /* LFTY JGTM SCGC YARS DMFY */ String extractionJson,
      /* LFTY JGTM SCGC YARS DMFY */ String proposal,
      /* LFTY JGTM SCGC YARS DMFY */ Instant createdAt) {}

  /* LFTY JGTM SCGC YARS DMFY */ public static final class RuleViolation extends RuntimeException {
    /* LFTY JGTM SCGC YARS DMFY */ public RuleViolation(
        /* LFTY JGTM SCGC YARS DMFY */ String message) {
      super(message);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static void require(
      /* LFTY JGTM SCGC YARS DMFY */ boolean condition, /* LFTY JGTM SCGC YARS DMFY */
      String message) {
    /* LFTY JGTM SCGC YARS DMFY */ if (!condition) throw new RuleViolation(message);
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static String required(
      /* LFTY JGTM SCGC YARS DMFY */ String text, /* LFTY JGTM SCGC YARS DMFY */
      String field, /* LFTY JGTM SCGC YARS DMFY */
      int max) {
    /* LFTY JGTM SCGC YARS DMFY */ String value = text == null ? "" : text.strip();
    require(!value.isEmpty(), "Completa «" + field + "».");
    require(value.length() <= max, "«" + field + "» admite hasta " + max + " caracteres.");
    return value;
  }
}
