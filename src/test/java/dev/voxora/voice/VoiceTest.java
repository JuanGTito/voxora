package dev.voxora.voice;

import static org.junit.jupiter.api.Assertions.*;

import dev.voxora.bootstrap.AppContext;
import dev.voxora.domain.Model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class VoiceTest {
  @Test
  void explicitlyUnperformedMeasurementCannotBecomePerformedTest() throws Exception {
    var answer = AiRuntime.JSON.readTree(INDEXES.replace("\"pruebas\":[]", "\"pruebas\":[2]"));
    var resolved = LlamaExtractor.resolve(SOURCE, answer);
    assertTrue(AiRuntime.JSON.readTree(resolved.json()).path("pruebas").isEmpty());
    assertTrue(resolved.proposal().contains("No se midió presión."));
  }

  @TempDir Path root;
  AppContext context;
  VoiceService voice;
  String admin, tech, other;
  long incident;
  static final String SOURCE =
      "Se cambió la junta de goma. No se observaron fugas. No se midió presión.";
  static final String INDEXES =
      "{\"actividades\":[0],\"materiales\":[0],\"pruebas\":[],\"resultados\":[1],\"observaciones\":[2],\"hallazgos\":[]}";

  @BeforeEach
  void setup() throws Exception {
    context = new AppContext(root);
    context.identity.setup("soporte", "Temporal-voice-2026!".toCharArray());
    admin = context.identity.login("soporte", "Temporal-voice-2026!".toCharArray());
    context.identity.createUser(
        admin,
        "tecnico",
        "Temporal-voice-2026!".toCharArray(),
        Role.TECHNICIAN,
        Specialty.MECANICA);
    context.identity.createUser(
        admin, "otro", "Temporal-voice-2026!".toCharArray(), Role.TECHNICIAN, Specialty.MECANICA);
    tech = context.identity.login("tecnico", "Temporal-voice-2026!".toCharArray());
    other = context.identity.login("otro", "Temporal-voice-2026!".toCharArray());
    incident =
        context.incidents.create(
            admin, "Bomba", "B-01", "Taller", "Revisar fuga", Priority.ALTA, Specialty.MECANICA);
    context.incidents.assign(admin, incident, context.identity.current(tech).id());
    context.incidents.start(tech, incident);
    voice =
        new VoiceService(
            context.repository,
            context.identity,
            new AiRuntime(root),
            audio -> SOURCE,
            source -> LlamaExtractor.resolve(source, AiRuntime.JSON.readTree(INDEXES)));
  }

  @AfterEach
  void close() throws Exception {
    context.close();
  }

  @Test
  void resolverPreservesWholeSourceAndNegations() throws Exception {
    var extraction = LlamaExtractor.resolve(SOURCE, AiRuntime.JSON.readTree(INDEXES));
    assertTrue(extraction.proposal().contains("No se midió presión."));
    assertTrue(extraction.proposal().contains("No se observaron fugas."));
    assertFalse(extraction.proposal().contains("Se midió presión."));
    assertEquals(AiRuntime.hash(SOURCE), extraction.sourceHash());
  }

  @Test
  void unknownFieldsGeneratedProseAndOutOfRangeReferencesAreRejected() throws Exception {
    assertThrows(
        RuleViolation.class,
        () ->
            LlamaExtractor.resolve(
                SOURCE, AiRuntime.JSON.readTree(INDEXES.replace("[0]", "[999]"))));
    assertThrows(
        RuleViolation.class,
        () ->
            LlamaExtractor.resolve(
                SOURCE, AiRuntime.JSON.readTree(INDEXES.replace("[0]", "[\"Se reparó todo\"]"))));
    assertThrows(
        RuleViolation.class,
        () ->
            LlamaExtractor.resolve(
                SOURCE,
                AiRuntime.JSON.readTree(INDEXES.replace("\"hallazgos\":[]", "\"inventado\":[]"))));
  }

  @Test
  void applyingProposalOnlySavesDraftAndStoresProvenance() throws Exception {
    VoiceEntry entry = voice.create(tech, incident, false);
    entry = voice.extract(tech, entry.id(), SOURCE);
    Version v = voice.apply(tech, entry.id(), SOURCE);
    assertEquals(Status.EN_PROCESO, context.incidents.read(tech, incident).status());
    assertNull(context.incidents.read(tech, incident).submittedVersion());
    assertEquals(entry.proposal(), v.content());
    assertEquals(1, voice.entries(tech, incident).size());
    try (var c =
            java.sql.DriverManager.getConnection("jdbc:sqlite:" + root.resolve("data/voxora.db"));
        var s = c.createStatement();
        var r = s.executeQuery("SELECT voice_entry_id FROM report_versions WHERE id=" + v.id())) {
      assertTrue(r.next());
      assertEquals(entry.id(), r.getLong(1));
    }
  }

  @Test
  void changingSourceInvalidatesProposal() throws Exception {
    VoiceEntry entry = voice.create(tech, incident, false);
    long id = entry.id();
    voice.extract(tech, id, SOURCE);
    assertThrows(RuleViolation.class, () -> voice.apply(tech, id, SOURCE + " Cambio."));
    voice.saveCorrection(tech, id, SOURCE + " Cambio.");
    assertThrows(RuleViolation.class, () -> voice.apply(tech, id, SOURCE));
  }

  @Test
  void foreignTechnicianCannotUseAnotherIncidentVoice() {
    VoiceEntry entry = voice.create(tech, incident, false);
    assertThrows(RuleViolation.class, () -> voice.create(other, incident, false));
    assertThrows(RuleViolation.class, () -> voice.extract(other, entry.id(), SOURCE));
    assertThrows(RuleViolation.class, () -> voice.apply(other, entry.id(), SOURCE));
  }

  @Test
  void extractorFailurePreservesCorrectedNarration() throws Exception {
    VoiceService failing =
        new VoiceService(
            context.repository,
            context.identity,
            new AiRuntime(root),
            audio -> SOURCE,
            source -> {
              throw new IllegalStateException("Motor detenido");
            });
    VoiceEntry entry = failing.create(tech, incident, false);
    assertThrows(IllegalStateException.class, () -> failing.extract(tech, entry.id(), SOURCE));
    assertEquals(SOURCE, failing.entries(tech, incident).getFirst().correctedText());
  }

  @Test
  void originalTranscriptionSurvivesCorrection() throws Exception {
    VoiceEntry entry = voice.create(tech, incident, true);
    voice.transcribe(tech, entry.id());
    voice.saveCorrection(tech, entry.id(), "Narración corregida manualmente.");
    VoiceEntry stored = voice.entries(tech, incident).getFirst();
    assertEquals(SOURCE, stored.rawText());
    assertEquals("Narración corregida manualmente.", stored.correctedText());
  }

  @Test
  void voiceCannotMutateSubmittedTicket() throws Exception {
    VoiceEntry entry = voice.create(tech, incident, false);
    voice.extract(tech, entry.id(), SOURCE);
    Version v = voice.apply(tech, entry.id(), SOURCE);
    context.incidents.submit(tech, incident, v.id());
    assertThrows(RuleViolation.class, () -> voice.extract(tech, entry.id(), SOURCE));
    assertThrows(RuleViolation.class, () -> voice.apply(tech, entry.id(), SOURCE));
  }

  @Test
  void backupIncludesAudioAndRestoresIt() throws Exception {
    VoiceEntry entry = voice.create(tech, incident, true);
    Path audio = voice.audioPath(entry);
    Files.createDirectories(audio.getParent());
    Files.write(audio, new byte[] {1, 2, 3});
    Path backup = root.resolve("with-audio.zip");
    context.backups.backup(admin, backup);
    Files.delete(audio);
    context.backups.restore(admin, backup);
    assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(audio));
  }

  @Test
  void unavailableComponentsDoNotDisableManualWork() {
    AiRuntime runtime = new AiRuntime(root);
    assertDoesNotThrow(() -> context.incidents.save(tech, incident, SOURCE));
  }

  @Test
  void reviewedProposalSavesHumanEditsWithVoiceProvenance() throws Exception {
    VoiceEntry entry = voice.create(tech, incident, false);
    entry = voice.extract(tech, entry.id(), SOURCE);
    String reviewed =
        "Se cambió la junta y se revisaron visualmente las conexiones. No se midió presión.";
    Version result = voice.apply(tech, entry.id(), SOURCE, reviewed);
    assertEquals(reviewed, result.content());
    assertEquals(reviewed, context.incidents.read(tech, incident).draft());
    assertEquals(Status.EN_PROCESO, context.incidents.read(tech, incident).status());
    assertNotEquals(reviewed, voice.entries(tech, incident).getFirst().proposal());
  }
}
