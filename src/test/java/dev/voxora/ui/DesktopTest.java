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
/* LFTY JGTM SCGC YARS DMFY */ class DesktopTest {
  /* LFTY JGTM SCGC YARS DMFY */ @TempDir Path root;
  /* LFTY JGTM SCGC YARS DMFY */ AppContext context;
  /* LFTY JGTM SCGC YARS DMFY */ VoxoraApplication application;
  /* LFTY JGTM SCGC YARS DMFY */ Stage stage;
  /* LFTY JGTM SCGC YARS DMFY */ static final String PASSWORD = "Temporal-ui-2026!";

  /* LFTY JGTM SCGC YARS DMFY */ @BeforeAll
  static void toolkit() throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ Path cache = Path.of("target/ui-cache").toAbsolutePath();
    Files.createDirectories(cache);
    System.setProperty("javafx.cachedir", cache.toString());
    /* LFTY JGTM SCGC YARS DMFY */ CountDownLatch started = new CountDownLatch(1);
    Platform.startup(
        () -> {
          Platform.setImplicitExit(false);
          started.countDown();
        });
    assertTrue(started.await(20, TimeUnit.SECONDS));
  }

  /* LFTY JGTM SCGC YARS DMFY */ @BeforeEach
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

  /* LFTY JGTM SCGC YARS DMFY */ @AfterEach
  void close() throws Exception {
    fx(
        () -> {
          /* LFTY JGTM SCGC YARS DMFY */ for (
          /* LFTY JGTM SCGC YARS DMFY */ Window window : List.copyOf(Window.getWindows()))
            window.hide();
          application.stop();
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void firstRunHasSetupWithoutPredefinedCredentials() throws Exception {
    fx(
        () -> {
          assertTrue(
              buttons(stage.getScene().getRoot()).stream()
                  .anyMatch(
                      /* LFTY JGTM SCGC YARS DMFY */ b -> b.getText().equals("Crear instalación")));
          assertEquals(2, stage.getScene().getRoot().lookupAll(".password-field").size());
          snapshot("first-run");
          return null;
        });
    assertTrue(context.identity.needsSetup());
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void loginDashboardNavigationAndCreateDialogRender() throws Exception {
    setupUi();
    fx(
        () -> {
          snapshot("dashboard");
          findButton("Incidencias").fire();
          layout();
          assertEquals(1, stage.getScene().getRoot().lookupAll(".table-view").size());
          snapshot("incidents");
          findButton("Tablero").fire();
          layout();
          assertEquals(5, stage.getScene().getRoot().lookupAll(".kanban-column").size());
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
                /* LFTY JGTM SCGC YARS DMFY */ for (
                /* LFTY JGTM SCGC YARS DMFY */ Window w :
                    List.copyOf(Window.getWindows())) /* LFTY JGTM SCGC YARS DMFY */
                  if (w != stage) w.hide();
              });
          findButton("Nueva incidencia").fire();
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void technicianEditorPreservesDraftWhenClosed() throws Exception {
    setupUi();
    /* LFTY JGTM SCGC YARS DMFY */ String admin =
        context.identity.login("soporte", PASSWORD.toCharArray());
    context.identity.createUser(
        admin, "tecnico", PASSWORD.toCharArray(), Role.TECHNICIAN, Specialty.MECANICA);
    /* LFTY JGTM SCGC YARS DMFY */ String tech =
        context.identity.login("tecnico", PASSWORD.toCharArray());
    /* LFTY JGTM SCGC YARS DMFY */ long techId = context.identity.current(tech).id();
    /* LFTY JGTM SCGC YARS DMFY */ long id =
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
          /* LFTY JGTM SCGC YARS DMFY */ TextField name =
              (TextField)
                  stage.getScene().getRoot().lookupAll(".text-field").stream()
                      .filter(
                          /* LFTY JGTM SCGC YARS DMFY */ n ->
                              n instanceof TextField && !(n instanceof PasswordField))
                      .findFirst()
                      .orElseThrow();
          name.setText("tecnico");
          ((PasswordField) stage.getScene().getRoot().lookup(".password-field")).setText(PASSWORD);
          findButton("Iniciar sesión").fire();
          return null;
        });
    awaitButton("Incidencias");
    /* LFTY JGTM SCGC YARS DMFY */ java.util.concurrent.atomic.AtomicReference<Throwable> failure =
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
                  /* LFTY JGTM SCGC YARS DMFY */ Window window =
                      Window.getWindows().stream()
                          .filter(/* LFTY JGTM SCGC YARS DMFY */ w -> w != stage && w.isShowing())
                          .findFirst()
                          .orElseThrow();
                  /* LFTY JGTM SCGC YARS DMFY */ Parent pane = window.getScene().getRoot();
                  pane.applyCss();
                  pane.layout();
                  /* LFTY JGTM SCGC YARS DMFY */ TextArea draft =
                      (TextArea)
                          pane.lookupAll(".text-area").stream()
                              .filter(
                                  /* LFTY JGTM SCGC YARS DMFY */ n ->
                                      n instanceof /* LFTY JGTM SCGC YARS DMFY */ TextArea t
                                          && t.isEditable())
                              .findFirst()
                              .orElseThrow();
                  draft.setText("Se inspeccionó la bomba y no se observaron fugas.");
                  /* LFTY JGTM SCGC YARS DMFY */ DialogPane dialog =
                      pane instanceof /* LFTY JGTM SCGC YARS DMFY */ DialogPane d
                          ? d
                          : (DialogPane) pane.lookup(".dialog-pane");
                  ((Button) dialog.lookupButton(ButtonType.CLOSE)).fire();
                } catch (/* LFTY JGTM SCGC YARS DMFY */ Throwable e) {
                  failure.set(e);
                  /* LFTY JGTM SCGC YARS DMFY */ for (
                  /* LFTY JGTM SCGC YARS DMFY */ Window w :
                      List.copyOf(Window.getWindows())) /* LFTY JGTM SCGC YARS DMFY */
                    if (w != stage) w.hide();
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

  /* LFTY JGTM SCGC YARS DMFY */ private void setupUi() throws Exception {
    fx(
        () -> {
          /* LFTY JGTM SCGC YARS DMFY */ TextField username =
              (TextField)
                  stage.getScene().getRoot().lookupAll(".text-field").stream()
                      .filter(
                          /* LFTY JGTM SCGC YARS DMFY */ n ->
                              n instanceof TextField && !(n instanceof PasswordField))
                      .findFirst()
                      .orElseThrow();
          username.setText("soporte");
          /* LFTY JGTM SCGC YARS DMFY */ for (
          /* LFTY JGTM SCGC YARS DMFY */ Node n :
              stage.getScene().getRoot().lookupAll(".password-field"))
            ((PasswordField) n).setText(PASSWORD);
          findButton("Crear instalación").fire();
          return null;
        });
    awaitButton("Incidencias");
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void voiceDialogDisplaysReviewableSourceAndAppliesOnlyDraft() throws Exception {
    setupUi();
    /* LFTY JGTM SCGC YARS DMFY */ String admin =
        context.identity.login("soporte", PASSWORD.toCharArray());
    context.identity.createUser(
        admin, "tecnico", PASSWORD.toCharArray(), Role.TECHNICIAN, Specialty.MECANICA);
    /* LFTY JGTM SCGC YARS DMFY */ String tech =
        context.identity.login("tecnico", PASSWORD.toCharArray());
    /* LFTY JGTM SCGC YARS DMFY */ long actor = context.identity.current(tech).id();
    /* LFTY JGTM SCGC YARS DMFY */ long id =
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
    /* LFTY JGTM SCGC YARS DMFY */ String narration =
        "Se cambió la junta de goma. No se midió presión.";
    context.repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ long entry =
              tx.addVoice(id, actor, "", java.time.Instant.now());
          tx.extraction(
              entry,
              narration,
              dev.voxora.voice.AiRuntime.hash(narration),
              "{}",
              "ACTIVIDADES\nSe cambió la junta de goma.\n\nOBSERVACIONES\nNo se midió presión.");
          return null;
        });
    /* LFTY JGTM SCGC YARS DMFY */ java.util.concurrent.atomic.AtomicReference<Version> applied =
        new java.util.concurrent.atomic.AtomicReference<>();
    /* LFTY JGTM SCGC YARS DMFY */ java.util.concurrent.atomic.AtomicReference<Throwable> failure =
        new java.util.concurrent.atomic.AtomicReference<>();
    fx(
        () -> {
          Platform.runLater(
              () -> {
                try {
                  /* LFTY JGTM SCGC YARS DMFY */ Window window =
                      Window.getWindows().stream()
                          .filter(/* LFTY JGTM SCGC YARS DMFY */ w -> w != stage && w.isShowing())
                          .findFirst()
                          .orElseThrow();
                  /* LFTY JGTM SCGC YARS DMFY */ Parent pane = window.getScene().getRoot();
                  pane.applyCss();
                  pane.layout();
                  assertTrue(
                      pane.lookupAll(".text-area").stream()
                          .anyMatch(
                              /* LFTY JGTM SCGC YARS DMFY */ n ->
                                  n instanceof /* LFTY JGTM SCGC YARS DMFY */ TextArea area
                                      && area.getText().contains("No se midió presión.")));
                  /* LFTY JGTM SCGC YARS DMFY */ var image = window.getScene().snapshot(null);
                  /* LFTY JGTM SCGC YARS DMFY */ BufferedImage png =
                      new BufferedImage(
                          (int) image.getWidth(),
                          (int) image.getHeight(),
                          BufferedImage.TYPE_INT_ARGB);
                  /* LFTY JGTM SCGC YARS DMFY */ for (
                  /* LFTY JGTM SCGC YARS DMFY */ int y = 0; y < png.getHeight(); y++)
                    /* LFTY JGTM SCGC YARS DMFY */ for (
                    /* LFTY JGTM SCGC YARS DMFY */ int x = 0; x < png.getWidth(); x++)
                      png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                  ImageIO.write(png, "png", Path.of("target/ui-evidence/voice.png").toFile());
                  buttons(pane).stream()
                      .filter(
                          /* LFTY JGTM SCGC YARS DMFY */ b ->
                              b.getText().equals("Usar como borrador"))
                      .findFirst()
                      .orElseThrow()
                      .fire();
                } catch (/* LFTY JGTM SCGC YARS DMFY */ Throwable e) {
                  failure.set(e);
                  /* LFTY JGTM SCGC YARS DMFY */ for (
                  /* LFTY JGTM SCGC YARS DMFY */ Window window : List.copyOf(Window.getWindows()))
                    /* LFTY JGTM SCGC YARS DMFY */ if (window != stage) window.hide();
                }
              });
          new VoiceDialog(context, tech, id, stage, applied::set).show();
          return null;
        });
    assertNull(failure.get(), () -> String.valueOf(failure.get()));
    assertNotNull(applied.get());
    assertEquals(Status.EN_PROCESO, context.incidents.read(tech, id).status());
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void awaitButton(
      /* LFTY JGTM SCGC YARS DMFY */ String text) throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    /* LFTY JGTM SCGC YARS DMFY */ while (System.nanoTime() < end) {
      /* LFTY JGTM SCGC YARS DMFY */ if (fx(
          () ->
              buttons(stage.getScene().getRoot()).stream()
                  .anyMatch(/* LFTY JGTM SCGC YARS DMFY */ b -> text.equals(b.getText())))) return;
      Thread.sleep(50);
    }
    fail("No apareció el botón " + text);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private Button findButton(
      /* LFTY JGTM SCGC YARS DMFY */ String text) {
    layout();
    return buttons(stage.getScene().getRoot()).stream()
        .filter(/* LFTY JGTM SCGC YARS DMFY */ b -> b.getText().equals(text))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No se encontró: " + text));
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void layout() {
    stage.getScene().getRoot().applyCss();
    stage.getScene().getRoot().layout();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static List<Button> buttons(
      /* LFTY JGTM SCGC YARS DMFY */ Parent root) {
    return root.lookupAll(".button").stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .toList();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void snapshot(
      /* LFTY JGTM SCGC YARS DMFY */ String name) throws Exception {
    layout();
    /* LFTY JGTM SCGC YARS DMFY */ var image = stage.getScene().snapshot(null);
    /* LFTY JGTM SCGC YARS DMFY */ BufferedImage png =
        new BufferedImage(
            (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    /* LFTY JGTM SCGC YARS DMFY */ for (
    /* LFTY JGTM SCGC YARS DMFY */ int y = 0; y < png.getHeight(); y++)
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ int x = 0; x < png.getWidth(); x++)
        png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
    /* LFTY JGTM SCGC YARS DMFY */ Path destination = Path.of("target/ui-evidence", name + ".png");
    Files.createDirectories(destination.getParent());
    ImageIO.write(png, "png", destination.toFile());
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static <T> T fx(
      /* LFTY JGTM SCGC YARS DMFY */ java.util.concurrent.Callable<T> callable) throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ FutureTask<T> task = new FutureTask<>(callable);
    Platform.runLater(task);
    return task.get(20, TimeUnit.SECONDS);
  }
}
