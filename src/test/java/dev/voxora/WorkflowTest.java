package dev.voxora;

import static org.junit.jupiter.api.Assertions.*;

import dev.voxora.domain.Model.*;
import dev.voxora.identity.*;
import dev.voxora.incidents.IncidentService;
import dev.voxora.reports.*;
import dev.voxora.settings.BackupService;
import dev.voxora.store.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/* LFTY JGTM SCGC YARS DMFY */ class WorkflowTest {
  /* LFTY JGTM SCGC YARS DMFY */ @TempDir Path root;
  /* LFTY JGTM SCGC YARS DMFY */ SqliteRepository repository;
  /* LFTY JGTM SCGC YARS DMFY */ IdentityService identity;
  /* LFTY JGTM SCGC YARS DMFY */ IncidentService incidents;
  /* LFTY JGTM SCGC YARS DMFY */ LocalReportDocuments documents;
  /* LFTY JGTM SCGC YARS DMFY */ String admin, supervisor, technician, other;
  /* LFTY JGTM SCGC YARS DMFY */ long techId, otherId;
  /* LFTY JGTM SCGC YARS DMFY */ static final String PASSWORD = "Prueba-local-2026!";
  /* LFTY JGTM SCGC YARS DMFY */ static final String REPORT =
      "Se cambió la junta de goma. No se observaron fugas. No se midió presión.";

  /* LFTY JGTM SCGC YARS DMFY */ @BeforeEach
  void initialize() {
    repository = new SqliteRepository(root);
    identity = new IdentityService(repository);
    documents = new LocalReportDocuments(root);
    incidents = new IncidentService(repository, identity, documents);
    identity.setup("soporte", PASSWORD.toCharArray());
    admin = identity.login("soporte", PASSWORD.toCharArray());
    identity.createUser(
        admin, "supervisor", PASSWORD.toCharArray(), Role.SUPERVISOR, Specialty.GENERAL);
    identity.createUser(
        admin, "tecnico", PASSWORD.toCharArray(), Role.TECHNICIAN, Specialty.MECANICA);
    identity.createUser(
        admin, "otro", PASSWORD.toCharArray(), Role.TECHNICIAN, Specialty.ELECTRICA);
    supervisor = identity.login("supervisor", PASSWORD.toCharArray());
    technician = identity.login("tecnico", PASSWORD.toCharArray());
    other = identity.login("otro", PASSWORD.toCharArray());
    techId = identity.current(technician).id();
    otherId = identity.current(other).id();
  }

  /* LFTY JGTM SCGC YARS DMFY */ long pending() {
    return incidents.create(
        supervisor,
        "Reparar fuga",
        "BOMBA-01",
        "Taller",
        "Se observa pérdida de agua.",
        Priority.ALTA,
        Specialty.MECANICA);
  }

  /* LFTY JGTM SCGC YARS DMFY */ long started() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = pending();
    incidents.assign(supervisor, id, techId);
    incidents.start(technician, id);
    return id;
  }

  /* LFTY JGTM SCGC YARS DMFY */ long submitted() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    /* LFTY JGTM SCGC YARS DMFY */ Version v = incidents.preview(technician, id, REPORT);
    incidents.submit(technician, id, v.id());
    return id;
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void manualCircuitPersistsAndExportsExactApprovedVersion() throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    /* LFTY JGTM SCGC YARS DMFY */ Version preview = incidents.preview(technician, id, REPORT);
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
    incidents.submit(technician, id, preview.id());
    incidents.returnReport(supervisor, id, "Agrega la prueba final.");
    assertEquals(1, incidents.versions(technician, id).size());
    /* LFTY JGTM SCGC YARS DMFY */ String corrected =
        REPORT + " Se realizó una inspección visual final.";
    /* LFTY JGTM SCGC YARS DMFY */ Version revised = incidents.preview(technician, id, corrected);
    incidents.submit(technician, id, revised.id());
    /* LFTY JGTM SCGC YARS DMFY */ Path pdf = incidents.approve(supervisor, id);
    assertTrue(Files.isRegularFile(pdf));
    assertEquals(Status.COMPLETADO, incidents.read(technician, id).status());
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var doc = Loader.loadPDF(pdf.toFile())) {
      /* LFTY JGTM SCGC YARS DMFY */ String text =
          new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
      assertTrue(text.contains("No se midió presión"), text);
      assertTrue(text.contains("inspección visual final"), text);
      assertTrue(text.contains("supervisor"), text);
    }
    /* LFTY JGTM SCGC YARS DMFY */ Path docx = incidents.export(technician, id, true);
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var in = Files.newInputStream(docx);
        /* LFTY JGTM SCGC YARS DMFY */ var doc = new XWPFDocument(in)) {
      assertTrue(
          doc.getParagraphs().stream()
              .anyMatch(/* LFTY JGTM SCGC YARS DMFY */ p -> p.getText().equals(corrected)));
    }
    /* LFTY JGTM SCGC YARS DMFY */ var reopened = new SqliteRepository(root);
    /* LFTY JGTM SCGC YARS DMFY */ var newIdentity = new IdentityService(reopened);
    /* LFTY JGTM SCGC YARS DMFY */ String session =
        newIdentity.login("tecnico", PASSWORD.toCharArray());
    assertEquals(
        Status.COMPLETADO,
        new IncidentService(reopened, newIdentity, documents).read(session, id).status());
    assertEquals(2, incidents.versions(technician, id).size());
    assertTrue(
        incidents.events(supervisor, id).stream()
            .anyMatch(/* LFTY JGTM SCGC YARS DMFY */ e -> e.action().equals("DEVUELTO")));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void techniciansCannotReadOrModifyForeignWork() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    assertTrue(incidents.list(other).isEmpty());
    assertThrows(RuleViolation.class, () -> incidents.read(other, id));
    assertThrows(RuleViolation.class, () -> incidents.save(other, id, REPORT));
    assertThrows(RuleViolation.class, () -> incidents.versions(other, id));
    assertThrows(RuleViolation.class, () -> incidents.events(other, id));
    assertThrows(RuleViolation.class, () -> incidents.assign(technician, id, otherId));
    assertThrows(
        RuleViolation.class,
        () -> incidents.create(technician, "x", "y", "z", "d", Priority.MEDIA, Specialty.GENERAL));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void assignmentRequiresMatchingSpecialtyAndValidState() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = pending();
    assertThrows(RuleViolation.class, () -> incidents.assign(supervisor, id, otherId));
    incidents.assign(supervisor, id, techId);
    incidents.start(technician, id);
    assertThrows(RuleViolation.class, () -> incidents.assign(supervisor, id, techId));
    assertThrows(RuleViolation.class, () -> incidents.start(technician, id));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void previewCancellationOnlySavesDraft() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    /* LFTY JGTM SCGC YARS DMFY */ Version v = incidents.preview(technician, id, REPORT);
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
    assertNull(incidents.read(technician, id).submittedVersion());
    assertEquals(REPORT, incidents.versions(technician, id).getFirst().content());
    assertEquals("BORRADOR", v.kind());
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void stalePreviewCannotBeSubmitted() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    /* LFTY JGTM SCGC YARS DMFY */ Version old = incidents.preview(technician, id, REPORT);
    incidents.save(technician, id, REPORT + " Corrección.");
    assertThrows(RuleViolation.class, () -> incidents.submit(technician, id, old.id()));
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void unchangedAutosaveDoesNotCreateVersions() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    /* LFTY JGTM SCGC YARS DMFY */ Version first = incidents.save(technician, id, REPORT);
    assertEquals(first.id(), incidents.save(technician, id, REPORT).id());
    assertEquals(1, incidents.versions(technician, id).size());
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void incompleteDraftIsRecoverableButNotSubmittable() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    incidents.save(technician, id, "Falta probar");
    assertEquals("Falta probar", incidents.read(technician, id).draft());
    assertThrows(RuleViolation.class, () -> incidents.preview(technician, id, "Falta probar"));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void duplicateSubmissionAndEditingSubmittedVersionAreRejected() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = submitted();
    /* LFTY JGTM SCGC YARS DMFY */ Version v = incidents.versions(technician, id).getFirst();
    assertEquals("ENVIADO", v.kind());
    assertThrows(RuleViolation.class, () -> incidents.submit(technician, id, v.id()));
    assertThrows(RuleViolation.class, () -> incidents.save(technician, id, "otro"));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void returnRequiresReasonAndSupervisor() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = submitted();
    assertThrows(RuleViolation.class, () -> incidents.returnReport(supervisor, id, " "));
    assertThrows(RuleViolation.class, () -> incidents.returnReport(technician, id, "Cambiar"));
    assertThrows(RuleViolation.class, () -> incidents.approve(technician, id));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void pdfFailureRollsBackApprovalAndAudit() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = submitted();
    /* LFTY JGTM SCGC YARS DMFY */ ReportDocuments failing =
        new ReportDocuments() /* LFTY JGTM SCGC YARS DMFY */ {
          /* LFTY JGTM SCGC YARS DMFY */ public Path pdf(
              /* LFTY JGTM SCGC YARS DMFY */ Incident i, /* LFTY JGTM SCGC YARS DMFY */
              Version v, /* LFTY JGTM SCGC YARS DMFY */
              String u, /* LFTY JGTM SCGC YARS DMFY */
              Instant at) {
            throw new IllegalStateException("Disco lleno");
          }

          /* LFTY JGTM SCGC YARS DMFY */ public Path docx(
              /* LFTY JGTM SCGC YARS DMFY */ Incident i, /* LFTY JGTM SCGC YARS DMFY */
              Version v, /* LFTY JGTM SCGC YARS DMFY */
              String u, /* LFTY JGTM SCGC YARS DMFY */
              Instant at) {
            throw new UnsupportedOperationException();
          }

          /* LFTY JGTM SCGC YARS DMFY */ public Path pdfPath(
              /* LFTY JGTM SCGC YARS DMFY */ Incident i, /* LFTY JGTM SCGC YARS DMFY */ Version v) {
            return documents.pdfPath(i, v);
          }
        };
    assertThrows(
        IllegalStateException.class,
        () -> new IncidentService(repository, identity, failing).approve(supervisor, id));
    assertEquals(Status.EN_REVISION, incidents.read(supervisor, id).status());
    assertFalse(
        incidents.events(supervisor, id).stream()
            .anyMatch(/* LFTY JGTM SCGC YARS DMFY */ e -> e.action().equals("APROBADO")));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void missingConfirmedPdfCanBeRegenerated() throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ long id = submitted();
    /* LFTY JGTM SCGC YARS DMFY */ Path pdf = incidents.approve(supervisor, id);
    Files.delete(pdf);
    incidents.reconcile();
    assertTrue(Files.size(pdf) > 1000);
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void forgedRevokedAndForeignRepositorySessionsAreRejected() {
    assertThrows(RuleViolation.class, () -> incidents.list("inventado"));
    identity.logout(technician, false);
    assertThrows(RuleViolation.class, () -> incidents.list(technician));
    /* LFTY JGTM SCGC YARS DMFY */ IdentityService isolated = new IdentityService(repository);
    assertThrows(RuleViolation.class, () -> isolated.current(admin));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void setupIsOneTimeAndPasswordsAreNeverStoredAsPlaintext() throws Exception {
    assertThrows(RuleViolation.class, () -> identity.setup("intruso", PASSWORD.toCharArray()));
    assertThrows(RuleViolation.class, () -> identity.login("soporte", "incorrecta".toCharArray()));
    /* LFTY JGTM SCGC YARS DMFY */ String hash =
        repository.transact(
            /* LFTY JGTM SCGC YARS DMFY */ tx -> tx.account("soporte").orElseThrow().hash());
    assertTrue(hash.startsWith("$argon2id$"));
    assertFalse(hash.contains(PASSWORD));
    /* LFTY JGTM SCGC YARS DMFY */ Passwords passwords = new Passwords();
    /* LFTY JGTM SCGC YARS DMFY */ String a = passwords.hash(PASSWORD.toCharArray()),
        b = passwords.hash(PASSWORD.toCharArray());
    assertNotEquals(a, b);
    assertTrue(passwords.verify(PASSWORD.toCharArray(), a));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void usersRequireSupportAndCannotSelfElevate() {
    assertThrows(
        RuleViolation.class,
        () ->
            identity.createUser(
                technician, "elevado", PASSWORD.toCharArray(), Role.SUPERADMIN, Specialty.GENERAL));
    /* LFTY JGTM SCGC YARS DMFY */ long id = identity.current(admin).id();
    assertThrows(
        RuleViolation.class,
        () -> identity.updateUser(admin, id, Role.TECHNICIAN, Specialty.GENERAL, true));
    assertThrows(
        RuleViolation.class,
        () -> identity.updateUser(admin, id, Role.SUPERADMIN, Specialty.GENERAL, false));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void inactiveAccountsAndChangedPasswordsInvalidateExistingSessions() {
    identity.updateUser(admin, otherId, Role.TECHNICIAN, Specialty.ELECTRICA, false);
    assertThrows(RuleViolation.class, () -> identity.current(other));
    assertThrows(RuleViolation.class, () -> identity.login("otro", PASSWORD.toCharArray()));
    identity.resetPassword(admin, techId, "Otra-clave-2026!".toCharArray());
    assertThrows(RuleViolation.class, () -> identity.current(technician));
    assertDoesNotThrow(() -> identity.login("tecnico", "Otra-clave-2026!".toCharArray()));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void technicianWithOpenWorkCannotBeDisabled() {
    started();
    assertThrows(
        RuleViolation.class,
        () -> identity.updateUser(admin, techId, Role.TECHNICIAN, Specialty.MECANICA, false));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void inactivityIsMeasuredOnlyFromHumanActivity() {
    /* LFTY JGTM SCGC YARS DMFY */ MutableClock clock = new MutableClock();
    /* LFTY JGTM SCGC YARS DMFY */ IdentityService timed = new IdentityService(repository, clock);
    /* LFTY JGTM SCGC YARS DMFY */ String session = timed.login("tecnico", PASSWORD.toCharArray());
    clock.advance(Duration.ofMinutes(14));
    timed.current(session);
    assertEquals(14, timed.idle(session).toMinutes());
    timed.activity(session);
    clock.advance(Duration.ofMinutes(14));
    assertDoesNotThrow(() -> timed.current(session));
    clock.advance(Duration.ofMinutes(1));
    assertThrows(RuleViolation.class, () -> timed.current(session));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void timeoutConfigurationRequiresSupportAndBounds() {
    assertThrows(RuleViolation.class, () -> identity.inactivityMinutes(technician, 5));
    assertThrows(RuleViolation.class, () -> identity.inactivityMinutes(admin, 31));
    identity.inactivityMinutes(admin, 5);
    assertEquals(5, identity.inactivityMinutes(supervisor));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void backupRestoresDatabaseAndDocumentsAndRevokesSessions() throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ long id = submitted();
    /* LFTY JGTM SCGC YARS DMFY */ Path pdf = incidents.approve(supervisor, id);
    /* LFTY JGTM SCGC YARS DMFY */ Path zip = root.resolve("copy.zip");
    /* LFTY JGTM SCGC YARS DMFY */ BackupService backups = new BackupService(repository, identity);
    backups.backup(admin, zip);
    pending();
    Files.delete(pdf);
    backups.restore(admin, zip);
    assertThrows(RuleViolation.class, () -> identity.current(admin));
    /* LFTY JGTM SCGC YARS DMFY */ String restored =
        identity.login("soporte", PASSWORD.toCharArray());
    assertEquals(1, incidents.list(restored).size());
    assertTrue(Files.isRegularFile(pdf));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void maliciousZipCannotEscapeOrChangeLiveDatabase() throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    /* LFTY JGTM SCGC YARS DMFY */ Path zip = root.resolve("bad.zip");
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var out = new ZipOutputStream(Files.newOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("../outside.txt"));
      out.write("bad".getBytes());
      out.closeEntry();
    }
    assertThrows(
        RuleViolation.class, () -> new BackupService(repository, identity).restore(admin, zip));
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
    assertFalse(Files.exists(root.getParent().resolve("outside.txt")));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void backupsCannotBeReadOrRestoredByTechnician() {
    /* LFTY JGTM SCGC YARS DMFY */ var backups = new BackupService(repository, identity);
    assertThrows(RuleViolation.class, () -> backups.backup(technician, root.resolve("copy.zip")));
    assertThrows(RuleViolation.class, () -> backups.restore(technician, root.resolve("copy.zip")));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void globalAuditIsRestrictedAndCatalogsAreValidated() {
    assertThrows(RuleViolation.class, () -> incidents.events(technician, null));
    assertThrows(
        RuleViolation.class,
        () ->
            incidents.create(
                supervisor, " ", "eq", "loc", "desc", Priority.MEDIA, Specialty.GENERAL));
    assertThrows(
        RuleViolation.class,
        () -> incidents.create(supervisor, "t", "eq", "loc", "desc", null, Specialty.GENERAL));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void sqliteTransactionRollsBackChangesAndEventsTogether() {
    assertThrows(
        IllegalStateException.class,
        () ->
            repository.transact(
                /* LFTY JGTM SCGC YARS DMFY */ tx -> {
                  tx.addIncident(
                      "x",
                      "eq",
                      "loc",
                      "desc",
                      Priority.MEDIA,
                      Specialty.GENERAL,
                      identity.current(admin).id(),
                      Instant.now());
                  throw new IllegalStateException("injected");
                }));
    assertTrue(incidents.list(admin).isEmpty());
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void selfApprovalIsRejectedEvenAfterRoleChangeOutsideUi() {
    /* LFTY JGTM SCGC YARS DMFY */ long id = submitted();
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          tx.updateUser(techId, Role.SUPERVISOR, Specialty.MECANICA, true);
          return null;
        });
    assertThrows(RuleViolation.class, () -> incidents.approve(technician, id));
    assertEquals(Status.EN_REVISION, incidents.read(supervisor, id).status());
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void removedIncidentPermissionIsEnforcedInService() {
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var c =
            java.sql.DriverManager.getConnection("jdbc:sqlite:" + root.resolve("data/voxora.db"));
        /* LFTY JGTM SCGC YARS DMFY */ var s = c.createStatement()) {
      s.executeUpdate("DELETE FROM role_permissions WHERE role='TECHNICIAN'");
    } catch (/* LFTY JGTM SCGC YARS DMFY */ java.sql.SQLException e) {
      throw new RuntimeException(e);
    }
    assertThrows(RuleViolation.class, () -> incidents.list(technician));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void interruptedRestoreRecoversPreviousDatabaseOnNextStart() throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    /* LFTY JGTM SCGC YARS DMFY */ Path safety = root.resolve("restore-recovery");
    Files.createDirectories(safety.resolve("data"));
    repository.snapshot(safety.resolve("data/voxora.db"));
    Files.createDirectories(safety.resolve("reports"));
    Files.writeString(safety.resolve("READY"), "pending");
    pending();
    BackupService.recoverInterrupted(root);
    assertEquals(1, incidents.list(supervisor).size());
    assertEquals(Status.EN_PROCESO, incidents.read(supervisor, id).status());
    assertFalse(Files.exists(safety));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void corruptedBackupIsRejectedBeforeLiveDataChanges() throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ long id = started();
    /* LFTY JGTM SCGC YARS DMFY */ BackupService backups = new BackupService(repository, identity);
    /* LFTY JGTM SCGC YARS DMFY */ Path source = root.resolve("source.zip"),
        bad = root.resolve("corrupt.zip");
    backups.backup(admin, source);
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var input = new ZipInputStream(Files.newInputStream(source));
        /* LFTY JGTM SCGC YARS DMFY */ var output =
            new ZipOutputStream(Files.newOutputStream(bad))) {
      /* LFTY JGTM SCGC YARS DMFY */ ZipEntry item;
      /* LFTY JGTM SCGC YARS DMFY */ while ((item = input.getNextEntry()) != null) {
        output.putNextEntry(new ZipEntry(item.getName()));
        /* LFTY JGTM SCGC YARS DMFY */ if (item.getName().equals("data/voxora.db")) {
          output.write("corrupt database".getBytes());
        } else input.transferTo(output);
        output.closeEntry();
      }
    }
    assertThrows(RuleViolation.class, () -> backups.restore(admin, bad));
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void migrationChecksumMismatchPreventsOpeningDifferentSchema() throws Exception {
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var c =
            java.sql.DriverManager.getConnection("jdbc:sqlite:" + root.resolve("data/voxora.db"));
        /* LFTY JGTM SCGC YARS DMFY */ var s = c.createStatement()) {
      s.executeUpdate("UPDATE schema_migrations SET checksum='changed'");
    }
    assertThrows(IllegalStateException.class, () -> new SqliteRepository(root));
    assertThrows(
        RuleViolation.class, () -> SqliteRepository.validate(root.resolve("data/voxora.db")));
  }

  /* LFTY JGTM SCGC YARS DMFY */ static class MutableClock extends Clock {
    /* LFTY JGTM SCGC YARS DMFY */ Instant now = Instant.parse("2026-10-31T00:00:00Z");

    /* LFTY JGTM SCGC YARS DMFY */ void advance(/* LFTY JGTM SCGC YARS DMFY */ Duration duration) {
      now = now.plus(duration);
    }

    /* LFTY JGTM SCGC YARS DMFY */ public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    /* LFTY JGTM SCGC YARS DMFY */ public Clock withZone(
        /* LFTY JGTM SCGC YARS DMFY */ ZoneId zone) {
      return this;
    }

    /* LFTY JGTM SCGC YARS DMFY */ public Instant instant() {
      return now;
    }
  }
}
