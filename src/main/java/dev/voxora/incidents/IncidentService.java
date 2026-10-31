package dev.voxora.incidents;

import static dev.voxora.domain.Model.*;

import dev.voxora.domain.Model.*;
import dev.voxora.identity.IdentityService;
import dev.voxora.reports.ReportDocuments;
import dev.voxora.store.Repository;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/* LFTY JGTM SCGC YARS DMFY */ public final class IncidentService {
  /* LFTY JGTM SCGC YARS DMFY */ private final Repository repository;
  /* LFTY JGTM SCGC YARS DMFY */ private final IdentityService identity;
  /* LFTY JGTM SCGC YARS DMFY */ private final ReportDocuments documents;

  /* LFTY JGTM SCGC YARS DMFY */ public IncidentService(
      /* LFTY JGTM SCGC YARS DMFY */ Repository repository, /* LFTY JGTM SCGC YARS DMFY */
      IdentityService identity, /* LFTY JGTM SCGC YARS DMFY */
      ReportDocuments documents) {
    this.repository = repository;
    this.identity = identity;
    this.documents = documents;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private User actor(
      /* LFTY JGTM SCGC YARS DMFY */ Repository.Tx tx, /* LFTY JGTM SCGC YARS DMFY */
      String token) {
    /* LFTY JGTM SCGC YARS DMFY */ User u = identity.authorize(tx, token);
    require(
        tx.permitted(
            u.id(), u.role().managesIncidents() ? "incidents.manage" : "incidents.assigned"),
        "No tienes permiso para acceder a incidencias.");
    return u;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static void manager(
      /* LFTY JGTM SCGC YARS DMFY */ User user) {
    require(user.role().managesIncidents(), "Esta acción requiere un supervisor.");
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static void visible(
      /* LFTY JGTM SCGC YARS DMFY */ User user, /* LFTY JGTM SCGC YARS DMFY */ Incident i) {
    require(
        user.role().managesIncidents() || Objects.equals(i.assignedTo(), user.id()),
        "No tienes acceso a esta incidencia.");
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static void owner(
      /* LFTY JGTM SCGC YARS DMFY */ User user, /* LFTY JGTM SCGC YARS DMFY */ Incident i) {
    require(
        user.active()
            && user.role() == Role.TECHNICIAN
            && Objects.equals(i.assignedTo(), user.id()),
        "Solo el técnico asignado puede atender esta incidencia.");
  }

  /* LFTY JGTM SCGC YARS DMFY */ public List<Incident> list(
      /* LFTY JGTM SCGC YARS DMFY */ String token) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          return tx.incidents().stream()
              .filter(
                  /* LFTY JGTM SCGC YARS DMFY */ i ->
                      u.role().managesIncidents() || Objects.equals(i.assignedTo(), u.id()))
              .toList();
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Incident read(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ long id) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(id);
          visible(u, i);
          return i;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public long create(
      /* LFTY JGTM SCGC YARS DMFY */ String token,
      /* LFTY JGTM SCGC YARS DMFY */ String title,
      /* LFTY JGTM SCGC YARS DMFY */ String equipment,
      /* LFTY JGTM SCGC YARS DMFY */ String location,
      /* LFTY JGTM SCGC YARS DMFY */ String description,
      /* LFTY JGTM SCGC YARS DMFY */ Priority priority,
      /* LFTY JGTM SCGC YARS DMFY */ Specialty specialty) {
    /* LFTY JGTM SCGC YARS DMFY */ String t = required(title, "título", 160),
        e = required(equipment, "equipo", 120),
        l = required(location, "ubicación", 160),
        d = required(description, "descripción", 5000);
    require(priority != null && specialty != null, "Selecciona prioridad y especialidad.");
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          manager(u);
          /* LFTY JGTM SCGC YARS DMFY */ Instant at = Instant.now();
          /* LFTY JGTM SCGC YARS DMFY */ long id =
              tx.addIncident(t, e, l, d, priority, specialty, u.id(), at);
          tx.event(id, u.id(), "CREADO", "Incidencia creada", at);
          return id;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void assign(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long id, /* LFTY JGTM SCGC YARS DMFY */
      long technician) {
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          manager(u);
          /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(id);
          require(
              i.status() == Status.PENDIENTE || i.status() == Status.ASIGNADO,
              "Solo se puede asignar antes de iniciar la atención.");
          /* LFTY JGTM SCGC YARS DMFY */ User target = tx.user(technician);
          require(
              target.active() && target.role() == Role.TECHNICIAN, "Selecciona un técnico activo.");
          require(
              target.specialty() == i.specialty(),
              "La especialidad del técnico debe coincidir con la incidencia.");
          /* LFTY JGTM SCGC YARS DMFY */ Instant at = Instant.now();
          tx.assign(id, technician, u.id(), at);
          tx.event(id, u.id(), "ASIGNADO", "Responsable: " + target.username(), at);
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void start(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ long id) {
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(id);
          owner(u, i);
          require(i.status() == Status.ASIGNADO, "La incidencia debe estar asignada.");
          /* LFTY JGTM SCGC YARS DMFY */ Instant at = Instant.now();
          tx.changeStatus(id, Status.EN_PROCESO, "Inicio de atención", u.id(), at);
          tx.event(id, u.id(), "INICIADO", "Atención iniciada", at);
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Version save(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long id, /* LFTY JGTM SCGC YARS DMFY */
      String text) {
    require(text != null && text.length() <= 30000, "El borrador admite hasta 30000 caracteres.");
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(id);
          owner(u, i);
          require(i.status() == Status.EN_PROCESO, "El reporte no está disponible para edición.");
          /* LFTY JGTM SCGC YARS DMFY */ List<Version> versions = tx.versions(id);
          /* LFTY JGTM SCGC YARS DMFY */ if (i.draft().equals(text) && !versions.isEmpty())
            return versions.getFirst();
          /* LFTY JGTM SCGC YARS DMFY */ Instant at = Instant.now();
          /* LFTY JGTM SCGC YARS DMFY */ Version v =
              tx.addVersion(id, text, u.id(), "BORRADOR", at);
          tx.draft(id, text, at);
          tx.event(id, u.id(), "BORRADOR_GUARDADO", "Versión " + v.number(), at);
          return v;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Version preview(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long id, /* LFTY JGTM SCGC YARS DMFY */
      String text) {
    require(
        text != null && text.strip().length() >= 20,
        "Describe el trabajo realizado con al menos 20 caracteres antes de enviar.");
    return save(token, id, text);
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void submit(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long id, /* LFTY JGTM SCGC YARS DMFY */
      long versionId) {
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(id);
          owner(u, i);
          require(i.status() == Status.EN_PROCESO, "La incidencia no está en proceso.");
          /* LFTY JGTM SCGC YARS DMFY */ Version v = tx.version(versionId);
          /* LFTY JGTM SCGC YARS DMFY */ List<Version> versions = tx.versions(id);
          require(
              v.incidentId() == id
                  && v.authorId() == u.id()
                  && versions.getFirst().id() == versionId
                  && i.draft().equals(v.content()),
              "El borrador cambió. Abre nuevamente la vista previa.");
          require(v.content().strip().length() >= 20, "El reporte está incompleto.");
          /* LFTY JGTM SCGC YARS DMFY */ Instant at = Instant.now();
          tx.submitted(id, versionId);
          tx.changeStatus(id, Status.EN_REVISION, "Envío de versión " + v.number(), u.id(), at);
          tx.event(id, u.id(), "ENVIADO", "Versión " + v.number(), at);
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void returnReport(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long id, /* LFTY JGTM SCGC YARS DMFY */
      String reason) {
    /* LFTY JGTM SCGC YARS DMFY */ String detail = required(reason, "motivo de devolución", 2000);
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          manager(u);
          /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(id);
          require(i.status() == Status.EN_REVISION, "El reporte debe estar en revisión.");
          /* LFTY JGTM SCGC YARS DMFY */ Instant at = Instant.now();
          tx.review(id, i.submittedVersion(), u.id(), "DEVUELTO", detail, at);
          tx.changeStatus(id, Status.EN_PROCESO, detail, u.id(), at);
          tx.event(id, u.id(), "DEVUELTO", detail, at);
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Path approve(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ long id) {
    /* LFTY JGTM SCGC YARS DMFY */ Path[] published = {null};
    try {
      return repository.transact(
          /* LFTY JGTM SCGC YARS DMFY */ tx -> {
            /* LFTY JGTM SCGC YARS DMFY */ User u =
                identity.requirePermission(tx, token, "reports.review");
            manager(u);
            /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(id);
            require(i.status() == Status.EN_REVISION, "El reporte debe estar en revisión.");
            /* LFTY JGTM SCGC YARS DMFY */ Version v = tx.version(i.submittedVersion());
            require(v.authorId() != u.id(), "No puedes aprobar tu propio reporte.");
            /* LFTY JGTM SCGC YARS DMFY */ Instant at = Instant.now();
            published[0] = documents.pdf(i, v, u.username(), at);
            tx.review(id, v.id(), u.id(), "APROBADO", "", at);
            tx.changeStatus(id, Status.COMPLETADO, "Reporte aprobado", u.id(), at);
            tx.event(id, u.id(), "APROBADO", "Versión " + v.number(), at);
            return published[0];
          });
    } catch (/* LFTY JGTM SCGC YARS DMFY */ RuntimeException e) {
      /* LFTY JGTM SCGC YARS DMFY */ if (published[0] != null)
        try {
          Files.deleteIfExists(published[0]);
        } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception cleanup) {
          e.addSuppressed(cleanup);
        }
      throw e;
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Path export(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long id, /* LFTY JGTM SCGC YARS DMFY */
      boolean docx) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(id);
          visible(u, i);
          require(
              i.status() == Status.COMPLETADO,
              "El documento final está disponible después de la aprobación.");
          /* LFTY JGTM SCGC YARS DMFY */ Version v = tx.version(i.submittedVersion());
          /* LFTY JGTM SCGC YARS DMFY */ Event approval =
              tx.events(id).stream()
                  .filter(/* LFTY JGTM SCGC YARS DMFY */ e -> e.action().equals("APROBADO"))
                  .findFirst()
                  .orElseThrow();
          /* LFTY JGTM SCGC YARS DMFY */ Path file =
              docx
                  ? documents.docx(i, v, approval.actor(), i.finishedAt())
                  : documents.pdf(i, v, approval.actor(), i.finishedAt());
          tx.event(
              id,
              u.id(),
              docx ? "EXPORTACION_DOCX" : "EXPORTACION_PDF",
              "Versión " + v.number(),
              Instant.now());
          return file;
        });
  }

  /**
   * Reconcile confirmed documents after an abrupt shutdown; unconfirmed files are never exposed.
   */
  /* LFTY JGTM SCGC YARS DMFY */ public void reconcile() {
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ for (
          /* LFTY JGTM SCGC YARS DMFY */ Incident i : tx.incidents())
            /* LFTY JGTM SCGC YARS DMFY */ if (i.status() == Status.COMPLETADO) {
              /* LFTY JGTM SCGC YARS DMFY */ Version v = tx.version(i.submittedVersion());
              /* LFTY JGTM SCGC YARS DMFY */ if (!Files.isRegularFile(documents.pdfPath(i, v))) {
                /* LFTY JGTM SCGC YARS DMFY */ Event event =
                    tx.events(i.id()).stream()
                        .filter(/* LFTY JGTM SCGC YARS DMFY */ e -> e.action().equals("APROBADO"))
                        .findFirst()
                        .orElseThrow();
                documents.pdf(i, v, event.actor(), i.finishedAt());
              }
            }
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public List<Version> versions(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ long id) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          visible(u, tx.incident(id));
          return tx.versions(id);
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public List<Event> events(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ Long id) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = actor(tx, token);
          /* LFTY JGTM SCGC YARS DMFY */ if (id == null) manager(u);
          else visible(u, tx.incident(id));
          /* LFTY JGTM SCGC YARS DMFY */ List<Event> events = tx.events(id);
          return id == null && u.role() == Role.SUPERVISOR
              ? events.stream()
                  .filter(/* LFTY JGTM SCGC YARS DMFY */ e -> e.incidentId() != null)
                  .toList()
              : events;
        });
  }
}
