package dev.voxora.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.voxora.bootstrap.AppContext;
import dev.voxora.domain.Model.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** In-process JavaFX tests with ephemeral accounts, not automation of user login dialogs. */
class DesktopTest {
  @TempDir Path root;
  AppContext context;
  VoxoraApplication application;
  Stage stage;
  static final String PASSWORD = "Temporal-ui-2026!";

  @BeforeAll
  static void toolkit() throws Exception {
    Files.createDirectories(Path.of("target/ui-evidence"));
    Path cache = Path.of("target/ui-cache").toAbsolutePath();
    Files.createDirectories(cache);
    System.setProperty("javafx.cachedir", cache.toString());
    CountDownLatch started = new CountDownLatch(1);
    Platform.startup(
        () -> {
          Platform.setImplicitExit(false);
          started.countDown();
        });
    assertTrue(started.await(20, TimeUnit.SECONDS));
  }

  @BeforeEach
  void open() throws Exception {
    context = new AppContext(root);
    fx(
        () -> {
          application = new VoxoraApplication(context);
          stage = new Stage();
          application.start(stage);
          return null;
        });
  }

  @AfterEach
  void close() throws Exception {
    fx(
        () -> {
          for (Window window : List.copyOf(Window.getWindows())) window.hide();
          application.stop();
          return null;
        });
  }

  @Test
  void firstRunHasSetupWithoutPredefinedCredentials() throws Exception {
    fx(
        () -> {
          assertTrue(
              buttons(stage.getScene().getRoot()).stream()
                  .anyMatch(b -> b.getText().equals("Crear instalación")));
          assertEquals(2, stage.getScene().getRoot().lookupAll(".password-field").size());
          snapshot("first-run");
          return null;
        });
    assertTrue(context.identity.needsSetup());
  }

  @Test
  void loginDashboardNavigationAndCreateDialogRender() throws Exception {
    setupUi();
    String admin = context.identity.login("soporte", PASSWORD.toCharArray());
    context.identity.createUser(
        admin, "tecnico", PASSWORD.toCharArray(), Role.TECHNICIAN, Specialty.MECANICA);
    String tech = context.identity.login("tecnico", PASSWORD.toCharArray());
    long assigned =
        context.incidents.create(
            admin,
            "Revisar vibración de la bomba principal",
            "BOM-02",
            "Sala de bombas",
            "Inspeccionar vibración y anclajes.",
            Priority.ALTA,
            Specialty.MECANICA);
    context.incidents.assign(admin, assigned, context.identity.current(tech).id());
    context.incidents.create(
        admin,
        "Comprobar el filtro de retorno",
        "FIL-01",
        "Taller",
        "Revisar condición del filtro.",
        Priority.BAJA,
        Specialty.GENERAL);
    fx(
        () -> {
          findButton("Inicio").fire();
          snapshot("dashboard");
          findButton("Incidencias").fire();
          layout();
          assertEquals(1, stage.getScene().getRoot().lookupAll(".table-view").size());
          snapshot("incidents");
          ComboBox<Priority> priority =
              (ComboBox<Priority>) stage.getScene().getRoot().lookup("#priority-filter");
          priority.setValue(Priority.ALTA);
          assertEquals(
              1,
              ((TableView<?>) stage.getScene().getRoot().lookup("#incident-table"))
                  .getItems()
                  .size());
          findButton("Limpiar").fire();
          assertEquals(
              2,
              ((TableView<?>) stage.getScene().getRoot().lookup("#incident-table"))
                  .getItems()
                  .size());
          findButton("Mi jornada").fire();
          snapshot("journey");
          assertEquals(1, stage.getScene().getRoot().lookupAll(".work-card").size());
          findButton("Indicadores").fire();
          snapshot("indicators");
          findButton("Revisiones").fire();
          snapshot("reviews");
          findButton("Tablero").fire();
          layout();
          assertEquals(5, stage.getScene().getRoot().lookupAll(".kanban-column").size());
          snapshot("kanban");
          findButton("Usuarios").fire();
          findButton("Configuración").fire();
          snapshot("settings");
          return null;
        });
    // Modal nested event loops need the close action queued before opening.
    fx(
        () -> {
          findButton("Incidencias").fire();
          Platform.runLater(
              () -> {
                for (Window w : List.copyOf(Window.getWindows())) if (w != stage) w.hide();
              });
          findButton("Nueva incidencia").fire();
          return null;
        });
  }

  @Test
  void technicianEditorPreservesDraftWhenClosed() throws Exception {
    setupUi();
    String admin = context.identity.login("soporte", PASSWORD.toCharArray());
    context.identity.createUser(
        admin, "tecnico", PASSWORD.toCharArray(), Role.TECHNICIAN, Specialty.MECANICA);
    String tech = context.identity.login("tecnico", PASSWORD.toCharArray());
    long techId = context.identity.current(tech).id();
    long id =
        context.incidents.create(
            admin,
            "Inspeccionar bomba",
            "B-01",
            "Taller",
            "Revisar la fuga observada.",
            Priority.MEDIA,
            Specialty.MECANICA);
    context.incidents.assign(admin, id, techId);
    context.incidents.start(tech, id);
    fx(
        () -> {
          findButton("Cerrar sesión").fire();
          TextField name =
              (TextField)
                  stage.getScene().getRoot().lookupAll(".text-field").stream()
                      .filter(n -> n instanceof TextField && !(n instanceof PasswordField))
                      .findFirst()
                      .orElseThrow();
          name.setText("tecnico");
          ((PasswordField) stage.getScene().getRoot().lookup(".password-field")).setText(PASSWORD);
          findButton("Iniciar sesión").fire();
          return null;
        });
    awaitButton("Incidencias");
    java.util.concurrent.atomic.AtomicReference<Throwable> failure =
        new java.util.concurrent.atomic.AtomicReference<>();
    fx(
        () -> {
          findButton("Incidencias").fire();
          layout();
          ((TableView<?>) stage.getScene().getRoot().lookup(".table-view"))
              .getSelectionModel()
              .selectFirst();
          Platform.runLater(
              () -> {
                try {
                  Window window =
                      Window.getWindows().stream()
                          .filter(w -> w != stage && w.isShowing())
                          .findFirst()
                          .orElseThrow();
                  Parent pane = window.getScene().getRoot();
                  pane.applyCss();
                  pane.layout();
                  TextArea draft =
                      (TextArea)
                          pane.lookupAll(".text-area").stream()
                              .filter(n -> n instanceof TextArea t && t.isEditable())
                              .findFirst()
                              .orElseThrow();
                  draft.setText("Se inspeccionó la bomba y no se observaron fugas.");
                  DialogPane dialog =
                      pane instanceof DialogPane d ? d : (DialogPane) pane.lookup(".dialog-pane");
                  ((Button) dialog.lookupButton(ButtonType.CLOSE)).fire();
                } catch (Throwable e) {
                  failure.set(e);
                  for (Window w : List.copyOf(Window.getWindows())) if (w != stage) w.hide();
                }
              });
          findButton("Abrir seleccionada").fire();
          return null;
        });
    assertNull(failure.get(), () -> String.valueOf(failure.get()));
    assertEquals(
        "Se inspeccionó la bomba y no se observaron fugas.",
        context.incidents.read(tech, id).draft());
  }

  private void setupUi() throws Exception {
    fx(
        () -> {
          TextField username =
              (TextField)
                  stage.getScene().getRoot().lookupAll(".text-field").stream()
                      .filter(n -> n instanceof TextField && !(n instanceof PasswordField))
                      .findFirst()
                      .orElseThrow();
          username.setText("soporte");
          for (Node n : stage.getScene().getRoot().lookupAll(".password-field"))
            ((PasswordField) n).setText(PASSWORD);
          findButton("Crear instalación").fire();
          return null;
        });
    awaitButton("Incidencias");
  }

  @Test
  void voiceDialogDisplaysReviewableSourceAndAppliesOnlyDraft() throws Exception {
    setupUi();
    String admin = context.identity.login("soporte", PASSWORD.toCharArray());
    context.identity.createUser(
        admin, "tecnico", PASSWORD.toCharArray(), Role.TECHNICIAN, Specialty.MECANICA);
    String tech = context.identity.login("tecnico", PASSWORD.toCharArray());
    long actor = context.identity.current(tech).id();
    long id =
        context.incidents.create(
            admin,
            "Revisar bomba",
            "B-01",
            "Taller",
            "Verificar funcionamiento.",
            Priority.MEDIA,
            Specialty.MECANICA);
    context.incidents.assign(admin, id, actor);
    context.incidents.start(tech, id);
    String narration = "Se cambió la junta de goma. No se midió presión.";
    context.repository.transact(
        tx -> {
          long entry = tx.addVoice(id, actor, "", java.time.Instant.now());
          tx.extraction(
              entry,
              narration,
              dev.voxora.voice.AiRuntime.hash(narration),
              "{}",
              "ACTIVIDADES\nSe cambió la junta de goma.\n\nOBSERVACIONES\nNo se midió presión.");
          return null;
        });
    java.util.concurrent.atomic.AtomicReference<Version> applied =
        new java.util.concurrent.atomic.AtomicReference<>();
    java.util.concurrent.atomic.AtomicReference<Throwable> failure =
        new java.util.concurrent.atomic.AtomicReference<>();
    fx(
        () -> {
          Platform.runLater(
              () -> {
                try {
                  Window window =
                      Window.getWindows().stream()
                          .filter(w -> w != stage && w.isShowing())
                          .findFirst()
                          .orElseThrow();
                  Parent pane = window.getScene().getRoot();
                  pane.applyCss();
                  pane.layout();
                  assertTrue(
                      pane.lookupAll(".text-area").stream()
                          .anyMatch(
                              n ->
                                  n instanceof TextArea area
                                      && area.getText().contains("No se midió presión.")));
                  var image = window.getScene().snapshot(null);
                  BufferedImage png =
                      new BufferedImage(
                          (int) image.getWidth(),
                          (int) image.getHeight(),
                          BufferedImage.TYPE_INT_ARGB);
                  for (int y = 0; y < png.getHeight(); y++)
                    for (int x = 0; x < png.getWidth(); x++)
                      png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                  ImageIO.write(png, "png", Path.of("target/ui-evidence/voice.png").toFile());
                  buttons(pane).stream()
                      .filter(b -> b.getText().equals("Guardar borrador"))
                      .findFirst()
                      .orElseThrow()
                      .fire();
                } catch (Throwable e) {
                  failure.set(e);
                  for (Window window : List.copyOf(Window.getWindows()))
                    if (window != stage) window.hide();
                }
              });
          new VoiceDialog(context, tech, id, stage, applied::set).show();
          return null;
        });
    assertNull(failure.get(), () -> String.valueOf(failure.get()));
    assertNotNull(applied.get());
    assertEquals(Status.EN_PROCESO, context.incidents.read(tech, id).status());
  }

  private void awaitButton(String text) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < end) {
      if (fx(
          () ->
              buttons(stage.getScene().getRoot()).stream().anyMatch(b -> text.equals(b.getText()))))
        return;
      Thread.sleep(50);
    }
    fail("No apareció el botón " + text);
  }

  @Test
  void automaticVoicePreparationShowsEditableReviewAndRequiresExplicitSave() throws Exception {
    var calls = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var saved = new java.util.concurrent.atomic.AtomicReference<String>();
    VoiceEntry initial =
        new VoiceEntry(100, 1, 1, "test.wav", "", "", "", "", "", java.time.Instant.now());
    String narration = "Se revisó la bomba y no se observaron fugas.";
    VoiceDraftSession backend =
        new VoiceDraftSession() {
          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public List<VoiceEntry> history() {
            return List.of(initial);
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public VoiceEntry create(boolean audio) {
            throw new AssertionError("No debe grabar al abrir");
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public Path audio(VoiceEntry entry) {
            throw new AssertionError("No debe acceder al micrófono");
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public VoiceEntry transcribe(VoiceEntry entry) {
            calls.add("transcribe");
            return new VoiceEntry(
                100, 1, 1, "test.wav", narration, narration, "", "", "", initial.createdAt());
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public VoiceEntry organize(VoiceEntry entry, String text) {
            calls.add("organize");
            assertEquals(narration, text);
            return new VoiceEntry(
                100,
                1,
                1,
                "test.wav",
                narration,
                narration,
                dev.voxora.voice.AiRuntime.hash(text),
                "{}",
                "RESULTADOS\n" + text,
                initial.createdAt());
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public void correct(VoiceEntry entry, String text) {}

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public void accept(VoiceEntry entry, String text, String review) {
            saved.set(review);
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public boolean replacesContent() {
            return false;
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public String acceptLabel() {
            return "Guardar borrador";
          }
        };
    var workflow = new java.util.concurrent.atomic.AtomicReference<VoiceDialog>();
    Platform.runLater(
        () -> {
          VoiceDialog voice = new VoiceDialog(backend, stage, () -> {});
          workflow.set(voice);
          Platform.runLater(() -> voice.processAudio(initial));
          voice.show();
        });
    awaitVoiceStatus("Listo.");
    fx(
        () -> {
          Parent pane = voicePane();
          assertEquals(List.of("transcribe", "organize"), calls);
          assertNull(saved.get());
          TextArea result = (TextArea) pane.lookup("#voice-result");
          assertTrue(result.isEditable());
          result.setText("Resultado corregido por el técnico.");
          ((Button) pane.lookup("#voice-apply")).fire();
          return null;
        });
    assertEquals("Resultado corregido por el técnico.", saved.get());
  }

  @Test
  void incidentNarrationStaysPendingAndChecksRoleWithoutCreatingTicket() throws Exception {
    setupUi();
    String admin = context.identity.login("soporte", PASSWORD.toCharArray());
    var accepted = new java.util.concurrent.atomic.AtomicReference<String>();
    VoiceDraftSession session = VoiceDraftSession.incident(context, admin, false, accepted::set);
    VoiceEntry entry = session.create(false);
    session.correct(entry, "Se encontró una fuga en la bomba.");
    var reopened = VoiceDraftSession.incident(context, admin, false, accepted::set);
    assertEquals(
        "Se encontró una fuga en la bomba.", reopened.history().getFirst().correctedText());
    assertTrue(context.incidents.list(admin).isEmpty());
    assertNull(accepted.get());
    VoiceEntry noProposal = reopened.history().getFirst();
    assertThrows(
        RuleViolation.class,
        () -> reopened.accept(noProposal, noProposal.correctedText(), "Sin procesar"));
    context.identity.createUser(
        admin, "tecnico", PASSWORD.toCharArray(), Role.TECHNICIAN, Specialty.MECANICA);
    String tech = context.identity.login("tecnico", PASSWORD.toCharArray());
    assertThrows(
        RuleViolation.class, () -> VoiceDraftSession.incident(context, tech, false, accepted::set));
  }

  @Test
  void failedAutomaticOrganizationKeepsTranscriptAvailableForCorrection() throws Exception {
    VoiceEntry initial =
        new VoiceEntry(100, 1, 1, "audio.wav", "", "", "", "", "", java.time.Instant.now());
    String narration = "No se midió presión. Se revisó el equipo.";
    VoiceDraftSession backend =
        new VoiceDraftSession() {
          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public List<VoiceEntry> history() {
            return List.of(initial);
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public VoiceEntry create(boolean audio) {
            throw new AssertionError();
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public Path audio(VoiceEntry entry) {
            throw new AssertionError();
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public VoiceEntry transcribe(VoiceEntry entry) {
            return new VoiceEntry(
                100, 1, 1, "audio.wav", narration, narration, "", "", "", initial.createdAt());
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public VoiceEntry organize(VoiceEntry entry, String text) {
            throw new IllegalStateException("Motor no disponible");
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public void correct(VoiceEntry entry, String text) {}

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public void accept(VoiceEntry entry, String text, String reviewed) {
            fail("No debe guardar un fallo");
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public boolean replacesContent() {
            return false;
          }

          /* LFTY · JGTM · SCGC · YARS · DMFY */
          public String acceptLabel() {
            return "Guardar borrador";
          }
        };
    Platform.runLater(
        () -> {
          VoiceDialog voice = new VoiceDialog(backend, stage, () -> {});
          Platform.runLater(() -> voice.processAudio(initial));
          voice.show();
        });
    awaitVoiceStatus("Motor no disponible");
    fx(
        () -> {
          Parent pane = voicePane();
          pane.applyCss();
          pane.layout();
          assertEquals(narration, ((TextArea) pane.lookup("#voice-source")).getText());
          assertTrue(((Button) pane.lookup("#voice-apply")).isDisabled());
          pane.getScene().getWindow().hide();
          return null;
        });
  }

  private Parent voicePane() {
    return Window.getWindows().stream()
        .filter(w -> w != stage && w.isShowing())
        .findFirst()
        .orElseThrow()
        .getScene()
        .getRoot();
  }

  @Test
  void incidentDescriptionPreservesUnclassifiedSentences() throws Exception {
    String narration = "Se detectó una fuga en la bomba. Hay agua en el suelo.";
    var result =
        dev.voxora.voice.LlamaExtractor.resolve(
            narration,
            dev.voxora.voice.AiRuntime.JSON.readTree(
                "{\"actividades\":[],\"materiales\":[],\"pruebas\":[],\"resultados\":[],\"observaciones\":[],\"hallazgos\":[0]}"));
    String description = VoiceDraftSession.PendingIncident.completeDescription(result);
    assertTrue(description.contains("Se detectó una fuga en la bomba."));
    assertTrue(description.contains("Hay agua en el suelo."));
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
      named = "voxora.realAi",
      matches = "true")
  void realNewIncidentDictationPreparesDescriptionWithoutCreatingIt() throws Exception {
    setupUi();
    String admin = context.identity.login("soporte", PASSWORD.toCharArray());
    var accepted = new java.util.concurrent.atomic.AtomicReference<String>();
    var session = VoiceDraftSession.incident(context, admin, false, accepted::set);
    VoiceEntry audio = session.create(true);
    dev.voxora.voice.AudioFiles.normalize(
        Path.of(System.getProperty("voxora.audioFixture")), session.audio(audio));
    VoiceEntry transcribed = session.transcribe(audio);
    assertFalse(transcribed.rawText().isBlank());
    VoiceEntry organized =
        session.organize(
            transcribed,
            "Se detectó una fuga en la bomba del taller. Se observa agua en el suelo.");
    assertFalse(organized.proposal().isBlank());
    session.accept(organized, organized.correctedText(), organized.proposal());
    assertTrue(accepted.get().contains("fuga"));
    assertTrue(accepted.get().contains("agua"));
    assertTrue(context.incidents.list(admin).isEmpty());
  }

  private void awaitVoiceStatus(String expected) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < end) {
      if (fx(
          () -> {
            try {
              Parent pane = voicePane();
              pane.applyCss();
              pane.layout();
              var node = pane.lookup("#voice-status");
              return node instanceof Label label && label.getText().contains(expected);
            } catch (NoSuchElementException e) {
              return false;
            }
          })) return;
      Thread.sleep(50);
    }
    fail("No apareció el estado: " + expected);
  }

  private Button findButton(String text) {
    layout();
    return buttons(stage.getScene().getRoot()).stream()
        .filter(b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No se encontró: " + text));
  }

  private void layout() {
    stage.getScene().getRoot().applyCss();
    stage.getScene().getRoot().layout();
  }

  private static List<Button> buttons(Parent root) {
    return root.lookupAll(".button").stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .toList();
  }

  private void snapshot(String name) throws Exception {
    layout();
    var image = stage.getScene().snapshot(null);
    BufferedImage png =
        new BufferedImage(
            (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < png.getHeight(); y++)
      for (int x = 0; x < png.getWidth(); x++)
        png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
    Path destination = Path.of("target/ui-evidence", name + ".png");
    Files.createDirectories(destination.getParent());
    ImageIO.write(png, "png", destination.toFile());
  }

  private static <T> T fx(java.util.concurrent.Callable<T> callable) throws Exception {
    FutureTask<T> task = new FutureTask<>(callable);
    Platform.runLater(task);
    return task.get(20, TimeUnit.SECONDS);
  }
}
