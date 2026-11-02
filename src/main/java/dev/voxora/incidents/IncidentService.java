package dev.voxora.incidents;

import static dev.voxora.domain.Model.*;

import dev.voxora.domain.Model.*;
import dev.voxora.identity.IdentityService;
import dev.voxora.reports.ReportDocuments;
import dev.voxora.store.Repository;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class IncidentService {
  private final Repository repository;
  private final IdentityService identity;
  private final ReportDocuments documents;

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public IncidentService(
      Repository repository, IdentityService identity, ReportDocuments documents) {
    this.repository = repository;
    this.identity = identity;
    this.documents = documents;
  }

  private User actor(Repository.Tx tx, String token) {
    User u = identity.authorize(tx, token);
    require(
        tx.permitted(
            u.id(), u.role().managesIncidents() ? "incidents.manage" : "incidents.assigned"),
        "No tienes permiso para acceder a incidencias.");
    return u;
  }

  private static void manager(User user) {
    require(user.role().managesIncidents(), "Esta acción requiere un supervisor.");
  }

  private static void visible(User user, Incident i) {
    require(
        user.role().managesIncidents() || Objects.equals(i.assignedTo(), user.id()),
        "No tienes acceso a esta incidencia.");
  }

  private static void owner(User user, Incident i) {
    require(
        user.active()
            && user.role() == Role.TECHNICIAN
            && Objects.equals(i.assignedTo(), user.id()),
        "Solo el técnico asignado puede atender esta incidencia.");
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public List<Incident> list(String token) {
    return repository.transact(
        tx -> {
          User u = actor(tx, token);
          return tx.incidents().stream()
              .filter(i -> u.role().managesIncidents() || Objects.equals(i.assignedTo(), u.id()))
              .toList();
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Incident read(String token, long id) {
    return repository.transact(
        tx -> {
          User u = actor(tx, token);
          Incident i = tx.incident(id);
          visible(u, i);
          return i;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public long create(
      String token,
      String title,
      String equipment,
      String location,
      String description,
      Priority priority,
      Specialty specialty) {
    String t = required(title, "título", 160),
        e = required(equipment, "equipo", 120),
        l = required(location, "ubicación", 160),
        d = required(description, "descripción", 5000);
    require(priority != null && specialty != null, "Selecciona prioridad y especialidad.");
    return repository.transact(
        tx -> {
          User u = actor(tx, token);
          manager(u);
          Instant at = Instant.now();
          long id = tx.addIncident(t, e, l, d, priority, specialty, u.id(), at);
          tx.event(id, u.id(), "CREADO", "Incidencia creada", at);
          return id;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void assign(String token, long id, long technician) {
    repository.transact(
        tx -> {
          User u = actor(tx, token);
          manager(u);
          Incident i = tx.incident(id);
          require(
              i.status() == Status.PENDIENTE || i.status() == Status.ASIGNADO,
              "Solo se puede asignar antes de iniciar la atención.");
          User target = tx.user(technician);
          require(
              target.active() && target.role() == Role.TECHNICIAN, "Selecciona un técnico activo.");
          require(
              target.specialty() == i.specialty(),
              "La especialidad del técnico debe coincidir con la incidencia.");
          Instant at = Instant.now();
          tx.assign(id, technician, u.id(), at);
          tx.event(id, u.id(), "ASIGNADO", "Responsable: " + target.username(), at);
          return null;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void start(String token, long id) {
    repository.transact(
        tx -> {
          User u = actor(tx, token);
          Incident i = tx.incident(id);
          owner(u, i);
          require(i.status() == Status.ASIGNADO, "La incidencia debe estar asignada.");
          Instant at = Instant.now();
          tx.changeStatus(id, Status.EN_PROCESO, "Inicio de atención", u.id(), at);
          tx.event(id, u.id(), "INICIADO", "Atención iniciada", at);
          return null;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Version save(String token, long id, String text) {
    require(text != null && text.length() <= 30000, "El borrador admite hasta 30000 caracteres.");
    return repository.transact(
        tx -> {
          User u = actor(tx, token);
          Incident i = tx.incident(id);
          owner(u, i);
          require(i.status() == Status.EN_PROCESO, "El reporte no está disponible para edición.");
          List<Version> versions = tx.versions(id);
          if (i.draft().equals(text) && !versions.isEmpty()) return versions.getFirst();
          Instant at = Instant.now();
          Version v = tx.addVersion(id, text, u.id(), "BORRADOR", at);
          tx.draft(id, text, at);
          tx.event(id, u.id(), "BORRADOR_GUARDADO", "Versión " + v.number(), at);
          return v;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Version preview(String token, long id, String text) {
    require(
        text != null && text.strip().length() >= 20,
        "Describe el trabajo realizado con al menos 20 caracteres antes de enviar.");
    return save(token, id, text);
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void submit(String token, long id, long versionId) {
    repository.transact(
        tx -> {
          User u = actor(tx, token);
          Incident i = tx.incident(id);
          owner(u, i);
          require(i.status() == Status.EN_PROCESO, "La incidencia no está en proceso.");
          Version v = tx.version(versionId);
          List<Version> versions = tx.versions(id);
          require(
              v.incidentId() == id
                  && v.authorId() == u.id()
                  && versions.getFirst().id() == versionId
                  && i.draft().equals(v.content()),
              "El borrador cambió. Abre nuevamente la vista previa.");
          require(v.content().strip().length() >= 20, "El reporte está incompleto.");
          Instant at = Instant.now();
          tx.submitted(id, versionId);
          tx.changeStatus(id, Status.EN_REVISION, "Envío de versión " + v.number(), u.id(), at);
          tx.event(id, u.id(), "ENVIADO", "Versión " + v.number(), at);
          return null;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void returnReport(String token, long id, String reason) {
    String detail = required(reason, "motivo de devolución", 2000);
    repository.transact(
        tx -> {
          User u = actor(tx, token);
          manager(u);
          Incident i = tx.incident(id);
          require(i.status() == Status.EN_REVISION, "El reporte debe estar en revisión.");
          Instant at = Instant.now();
          tx.review(id, i.submittedVersion(), u.id(), "DEVUELTO", detail, at);
          tx.changeStatus(id, Status.EN_PROCESO, detail, u.id(), at);
          tx.event(id, u.id(), "DEVUELTO", detail, at);
          return null;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Path approve(String token, long id) {
    Path[] published = {null};
    try {
      return repository.transact(
          tx -> {
            User u = identity.requirePermission(tx, token, "reports.review");
            manager(u);
            Incident i = tx.incident(id);
            require(i.status() == Status.EN_REVISION, "El reporte debe estar en revisión.");
            Version v = tx.version(i.submittedVersion());
            require(v.authorId() != u.id(), "No puedes aprobar tu propio reporte.");
            Instant at = Instant.now();
            published[0] = documents.pdf(i, v, u.username(), at);
            tx.review(id, v.id(), u.id(), "APROBADO", "", at);
            tx.changeStatus(id, Status.COMPLETADO, "Reporte aprobado", u.id(), at);
            tx.event(id, u.id(), "APROBADO", "Versión " + v.number(), at);
            return published[0];
          });
    } catch (RuntimeException e) {
      if (published[0] != null)
        try {
          Files.deleteIfExists(published[0]);
        } catch (Exception cleanup) {
          e.addSuppressed(cleanup);
        }
      throw e;
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Path export(String token, long id, boolean docx) {
    return repository.transact(
        tx -> {
          User u = actor(tx, token);
          Incident i = tx.incident(id);
          visible(u, i);
          require(
              i.status() == Status.COMPLETADO,
              "El documento final está disponible después de la aprobación.");
          Version v = tx.version(i.submittedVersion());
          Event approval =
              tx.events(id).stream()
                  .filter(e -> e.action().equals("APROBADO"))
                  .findFirst()
                  .orElseThrow();
          Path file =
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
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void reconcile() {
    repository.transact(
        tx -> {
          for (Incident i : tx.incidents())
            if (i.status() == Status.COMPLETADO) {
              Version v = tx.version(i.submittedVersion());
              if (!Files.isRegularFile(documents.pdfPath(i, v))) {
                Event event =
                    tx.events(i.id()).stream()
                        .filter(e -> e.action().equals("APROBADO"))
                        .findFirst()
                        .orElseThrow();
                documents.pdf(i, v, event.actor(), i.finishedAt());
              }
            }
          return null;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public List<Version> versions(String token, long id) {
    return repository.transact(
        tx -> {
          User u = actor(tx, token);
          visible(u, tx.incident(id));
          return tx.versions(id);
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public List<Event> events(String token, Long id) {
    return repository.transact(
        tx -> {
          User u = actor(tx, token);
          if (id == null) manager(u);
          else visible(u, tx.incident(id));
          List<Event> events = tx.events(id);
          return id == null && u.role() == Role.SUPERVISOR
              ? events.stream().filter(e -> e.incidentId() != null).toList()
              : events;
        });
  }
}
