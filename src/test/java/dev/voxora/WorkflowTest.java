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

class WorkflowTest {
  @TempDir Path root;
  SqliteRepository repository;
  IdentityService identity;
  IncidentService incidents;
  LocalReportDocuments documents;
  String admin, supervisor, technician, other;
  long techId, otherId;
  static final String PASSWORD = "Prueba-local-2026!";
  static final String REPORT =
      "Se cambió la junta de goma. No se observaron fugas. No se midió presión.";

  @BeforeEach
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

  long pending() {
    return incidents.create(
        supervisor,
        "Reparar fuga",
        "BOMBA-01",
        "Taller",
        "Se observa pérdida de agua.",
        Priority.ALTA,
        Specialty.MECANICA);
  }

  long started() {
    long id = pending();
    incidents.assign(supervisor, id, techId);
    incidents.start(technician, id);
    return id;
  }

  long submitted() {
    long id = started();
    Version v = incidents.preview(technician, id, REPORT);
    incidents.submit(technician, id, v.id());
    return id;
  }

  @Test
  void manualCircuitPersistsAndExportsExactApprovedVersion() throws Exception {
    long id = started();
    Version preview = incidents.preview(technician, id, REPORT);
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
    incidents.submit(technician, id, preview.id());
    incidents.returnReport(supervisor, id, "Agrega la prueba final.");
    assertEquals(1, incidents.versions(technician, id).size());
    String corrected = REPORT + " Se realizó una inspección visual final.";
    Version revised = incidents.preview(technician, id, corrected);
    incidents.submit(technician, id, revised.id());
    Path pdf = incidents.approve(supervisor, id);
    assertTrue(Files.isRegularFile(pdf));
    assertEquals(Status.COMPLETADO, incidents.read(technician, id).status());
    try (var doc = Loader.loadPDF(pdf.toFile())) {
      String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
      assertTrue(text.contains("No se midió presión"), text);
      assertTrue(text.contains("inspección visual final"), text);
      assertTrue(text.contains("supervisor"), text);
    }
    Path docx = incidents.export(technician, id, true);
    try (var in = Files.newInputStream(docx);
        var doc = new XWPFDocument(in)) {
      assertTrue(doc.getParagraphs().stream().anyMatch(p -> p.getText().equals(corrected)));
    }
    var reopened = new SqliteRepository(root);
    var newIdentity = new IdentityService(reopened);
    String session = newIdentity.login("tecnico", PASSWORD.toCharArray());
    assertEquals(
        Status.COMPLETADO,
        new IncidentService(reopened, newIdentity, documents).read(session, id).status());
    assertEquals(2, incidents.versions(technician, id).size());
    assertTrue(
        incidents.events(supervisor, id).stream().anyMatch(e -> e.action().equals("DEVUELTO")));
  }

  @Test
  void techniciansCannotReadOrModifyForeignWork() {
    long id = started();
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

  @Test
  void assignmentRequiresMatchingSpecialtyAndValidState() {
    long id = pending();
    assertThrows(RuleViolation.class, () -> incidents.assign(supervisor, id, otherId));
    incidents.assign(supervisor, id, techId);
    incidents.start(technician, id);
    assertThrows(RuleViolation.class, () -> incidents.assign(supervisor, id, techId));
    assertThrows(RuleViolation.class, () -> incidents.start(technician, id));
  }

  @Test
  void previewCancellationOnlySavesDraft() {
    long id = started();
    Version v = incidents.preview(technician, id, REPORT);
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
    assertNull(incidents.read(technician, id).submittedVersion());
    assertEquals(REPORT, incidents.versions(technician, id).getFirst().content());
    assertEquals("BORRADOR", v.kind());
  }

  @Test
  void stalePreviewCannotBeSubmitted() {
    long id = started();
    Version old = incidents.preview(technician, id, REPORT);
    incidents.save(technician, id, REPORT + " Corrección.");
    assertThrows(RuleViolation.class, () -> incidents.submit(technician, id, old.id()));
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
  }

  @Test
  void unchangedAutosaveDoesNotCreateVersions() {
    long id = started();
    Version first = incidents.save(technician, id, REPORT);
    assertEquals(first.id(), incidents.save(technician, id, REPORT).id());
    assertEquals(1, incidents.versions(technician, id).size());
  }

  @Test
  void incompleteDraftIsRecoverableButNotSubmittable() {
    long id = started();
    incidents.save(technician, id, "Falta probar");
    assertEquals("Falta probar", incidents.read(technician, id).draft());
    assertThrows(RuleViolation.class, () -> incidents.preview(technician, id, "Falta probar"));
  }

  @Test
  void duplicateSubmissionAndEditingSubmittedVersionAreRejected() {
    long id = submitted();
    Version v = incidents.versions(technician, id).getFirst();
    assertEquals("ENVIADO", v.kind());
    assertThrows(RuleViolation.class, () -> incidents.submit(technician, id, v.id()));
    assertThrows(RuleViolation.class, () -> incidents.save(technician, id, "otro"));
  }

  @Test
  void returnRequiresReasonAndSupervisor() {
    long id = submitted();
    assertThrows(RuleViolation.class, () -> incidents.returnReport(supervisor, id, " "));
    assertThrows(RuleViolation.class, () -> incidents.returnReport(technician, id, "Cambiar"));
    assertThrows(RuleViolation.class, () -> incidents.approve(technician, id));
  }

  @Test
  void pdfFailureRollsBackApprovalAndAudit() {
    long id = submitted();
    ReportDocuments failing =
        new ReportDocuments() {
          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public Path pdf(Incident i, Version v, String u, Instant at) {
            throw new IllegalStateException("Disco lleno");
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public Path docx(Incident i, Version v, String u, Instant at) {
            throw new UnsupportedOperationException();
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public Path pdfPath(Incident i, Version v) {
            return documents.pdfPath(i, v);
          }
        };
    assertThrows(
        IllegalStateException.class,
        () -> new IncidentService(repository, identity, failing).approve(supervisor, id));
    assertEquals(Status.EN_REVISION, incidents.read(supervisor, id).status());
    assertFalse(
        incidents.events(supervisor, id).stream().anyMatch(e -> e.action().equals("APROBADO")));
  }

  @Test
  void missingConfirmedPdfCanBeRegenerated() throws Exception {
    long id = submitted();
    Path pdf = incidents.approve(supervisor, id);
    Files.delete(pdf);
    incidents.reconcile();
    assertTrue(Files.size(pdf) > 1000);
  }

  @Test
  void forgedRevokedAndForeignRepositorySessionsAreRejected() {
    assertThrows(RuleViolation.class, () -> incidents.list("inventado"));
    identity.logout(technician, false);
    assertThrows(RuleViolation.class, () -> incidents.list(technician));
    IdentityService isolated = new IdentityService(repository);
    assertThrows(RuleViolation.class, () -> isolated.current(admin));
  }

  @Test
  void setupIsOneTimeAndPasswordsAreNeverStoredAsPlaintext() throws Exception {
    assertThrows(RuleViolation.class, () -> identity.setup("intruso", PASSWORD.toCharArray()));
    assertThrows(RuleViolation.class, () -> identity.login("soporte", "incorrecta".toCharArray()));
    String hash = repository.transact(tx -> tx.account("soporte").orElseThrow().hash());
    assertTrue(hash.startsWith("$argon2id$"));
    assertFalse(hash.contains(PASSWORD));
    Passwords passwords = new Passwords();
    String a = passwords.hash(PASSWORD.toCharArray()), b = passwords.hash(PASSWORD.toCharArray());
    assertNotEquals(a, b);
    assertTrue(passwords.verify(PASSWORD.toCharArray(), a));
  }

  @Test
  void usersRequireSupportAndCannotSelfElevate() {
    assertThrows(
        RuleViolation.class,
        () ->
            identity.createUser(
                technician, "elevado", PASSWORD.toCharArray(), Role.SUPERADMIN, Specialty.GENERAL));
    long id = identity.current(admin).id();
    assertThrows(
        RuleViolation.class,
        () -> identity.updateUser(admin, id, Role.TECHNICIAN, Specialty.GENERAL, true));
    assertThrows(
        RuleViolation.class,
        () -> identity.updateUser(admin, id, Role.SUPERADMIN, Specialty.GENERAL, false));
  }

  @Test
  void inactiveAccountsAndChangedPasswordsInvalidateExistingSessions() {
    identity.updateUser(admin, otherId, Role.TECHNICIAN, Specialty.ELECTRICA, false);
    assertThrows(RuleViolation.class, () -> identity.current(other));
    assertThrows(RuleViolation.class, () -> identity.login("otro", PASSWORD.toCharArray()));
    identity.resetPassword(admin, techId, "Otra-clave-2026!".toCharArray());
    assertThrows(RuleViolation.class, () -> identity.current(technician));
    assertDoesNotThrow(() -> identity.login("tecnico", "Otra-clave-2026!".toCharArray()));
  }

  @Test
  void technicianWithOpenWorkCannotBeDisabled() {
    started();
    assertThrows(
        RuleViolation.class,
        () -> identity.updateUser(admin, techId, Role.TECHNICIAN, Specialty.MECANICA, false));
  }

  @Test
  void inactivityIsMeasuredOnlyFromHumanActivity() {
    MutableClock clock = new MutableClock();
    IdentityService timed = new IdentityService(repository, clock);
    String session = timed.login("tecnico", PASSWORD.toCharArray());
    clock.advance(Duration.ofMinutes(14));
    timed.current(session);
    assertEquals(14, timed.idle(session).toMinutes());
    timed.activity(session);
    clock.advance(Duration.ofMinutes(14));
    assertDoesNotThrow(() -> timed.current(session));
    clock.advance(Duration.ofMinutes(1));
    assertThrows(RuleViolation.class, () -> timed.current(session));
  }

  @Test
  void timeoutConfigurationRequiresSupportAndBounds() {
    assertThrows(RuleViolation.class, () -> identity.inactivityMinutes(technician, 5));
    assertThrows(RuleViolation.class, () -> identity.inactivityMinutes(admin, 31));
    identity.inactivityMinutes(admin, 5);
    assertEquals(5, identity.inactivityMinutes(supervisor));
  }

  @Test
  void backupRestoresDatabaseAndDocumentsAndRevokesSessions() throws Exception {
    long id = submitted();
    Path pdf = incidents.approve(supervisor, id);
    Path zip = root.resolve("copy.zip");
    BackupService backups = new BackupService(repository, identity);
    backups.backup(admin, zip);
    pending();
    Files.delete(pdf);
    backups.restore(admin, zip);
    assertThrows(RuleViolation.class, () -> identity.current(admin));
    String restored = identity.login("soporte", PASSWORD.toCharArray());
    assertEquals(1, incidents.list(restored).size());
    assertTrue(Files.isRegularFile(pdf));
  }

  @Test
  void maliciousZipCannotEscapeOrChangeLiveDatabase() throws Exception {
    long id = started();
    Path zip = root.resolve("bad.zip");
    try (var out = new ZipOutputStream(Files.newOutputStream(zip))) {
      out.putNextEntry(new ZipEntry("../outside.txt"));
      out.write("bad".getBytes());
      out.closeEntry();
    }
    assertThrows(
        RuleViolation.class, () -> new BackupService(repository, identity).restore(admin, zip));
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
    assertFalse(Files.exists(root.getParent().resolve("outside.txt")));
  }

  @Test
  void backupsCannotBeReadOrRestoredByTechnician() {
    var backups = new BackupService(repository, identity);
    assertThrows(RuleViolation.class, () -> backups.backup(technician, root.resolve("copy.zip")));
    assertThrows(RuleViolation.class, () -> backups.restore(technician, root.resolve("copy.zip")));
  }

  @Test
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

  @Test
  void sqliteTransactionRollsBackChangesAndEventsTogether() {
    assertThrows(
        IllegalStateException.class,
        () ->
            repository.transact(
                tx -> {
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

  @Test
  void selfApprovalIsRejectedEvenAfterRoleChangeOutsideUi() {
    long id = submitted();
    repository.transact(
        tx -> {
          tx.updateUser(techId, Role.SUPERVISOR, Specialty.MECANICA, true);
          return null;
        });
    assertThrows(RuleViolation.class, () -> incidents.approve(technician, id));
    assertEquals(Status.EN_REVISION, incidents.read(supervisor, id).status());
  }

  @Test
  void removedIncidentPermissionIsEnforcedInService() {
    try (var c =
            java.sql.DriverManager.getConnection("jdbc:sqlite:" + root.resolve("data/voxora.db"));
        var s = c.createStatement()) {
      s.executeUpdate("DELETE FROM role_permissions WHERE role='TECHNICIAN'");
    } catch (java.sql.SQLException e) {
      throw new RuntimeException(e);
    }
    assertThrows(RuleViolation.class, () -> incidents.list(technician));
  }

  @Test
  void interruptedRestoreRecoversPreviousDatabaseOnNextStart() throws Exception {
    long id = started();
    Path safety = root.resolve("restore-recovery");
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

  @Test
  void corruptedBackupIsRejectedBeforeLiveDataChanges() throws Exception {
    long id = started();
    BackupService backups = new BackupService(repository, identity);
    Path source = root.resolve("source.zip"), bad = root.resolve("corrupt.zip");
    backups.backup(admin, source);
    try (var input = new ZipInputStream(Files.newInputStream(source));
        var output = new ZipOutputStream(Files.newOutputStream(bad))) {
      ZipEntry item;
      while ((item = input.getNextEntry()) != null) {
        output.putNextEntry(new ZipEntry(item.getName()));
        if (item.getName().equals("data/voxora.db")) {
          output.write("corrupt database".getBytes());
        } else input.transferTo(output);
        output.closeEntry();
      }
    }
    assertThrows(RuleViolation.class, () -> backups.restore(admin, bad));
    assertEquals(Status.EN_PROCESO, incidents.read(technician, id).status());
  }

  @Test
  void migrationChecksumMismatchPreventsOpeningDifferentSchema() throws Exception {
    try (var c =
            java.sql.DriverManager.getConnection("jdbc:sqlite:" + root.resolve("data/voxora.db"));
        var s = c.createStatement()) {
      s.executeUpdate("UPDATE schema_migrations SET checksum='changed'");
    }
    assertThrows(IllegalStateException.class, () -> new SqliteRepository(root));
    assertThrows(
        RuleViolation.class, () -> SqliteRepository.validate(root.resolve("data/voxora.db")));
  }

  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-31T00:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public Clock withZone(ZoneId zone) {
      return this;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public Instant instant() {
      return now;
    }
  }
}
