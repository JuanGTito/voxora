package dev.voxora.ui;

import dev.voxora.bootstrap.AppContext;
import dev.voxora.domain.Model.*;
import dev.voxora.voice.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.*;
import javafx.util.Duration;

/** One guided action for capture, automatic preparation, and explicit human review. */
final class VoiceDialog {
  private enum Step {
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    READY,
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    LISTENING,
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    TRANSCRIBING,
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    ORGANIZING,
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    REVIEW,
    /* LFTY · JGTM · SCGC · YARS · DMFY */
    ERROR
  }

  private final VoiceDraftSession session;
  private final Runnable humanActivity;
  private final Dialog<ButtonType> dialog = new Dialog<>();
  private final TextArea source = new TextArea(), proposal = new TextArea();
  private final ComboBox<VoiceEntry> entries = new ComboBox<>();
  private final Label status =
      new Label("Cuéntanos qué ocurrió y qué hiciste. Al detener, prepararemos el texto por ti.");
  private final Label stageTitle = new Label("¿Qué quieres documentar?"),
      elapsed = new Label("Micrófono apagado");
  private final HBox steps = new HBox(12);
  private final Button speak = new Button("Hablar"),
      prepare = new Button("Preparar texto"),
      apply = new Button(),
      importAudio = new Button("Importar un audio");
  private final Circle pulse = new Circle(28);
  private final ProgressIndicator progress = new ProgressIndicator();
  private final VBox review = new VBox(10);
  private final TitledPane correction = new TitledPane(), history = new TitledPane();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "voxora-guided-voice");
            t.setDaemon(true);
            return t;
          });
  private final PauseTransition debounce = new PauseTransition(Duration.seconds(1));
  private final Timeline timer;
  private final ScaleTransition listeningAnimation =
      new ScaleTransition(Duration.millis(650), pulse);
  private AudioFiles.Recording recording;
  private Task<?> task;
  private Step step = Step.READY;
  private boolean loading, busy, closed;
  private boolean recordAfterOpening;
  private long recordingStarted;

  VoiceDialog(
      AppContext context, String token, long incidentId, Window owner, Consumer<Version> onApply) {
    this(
        VoiceDraftSession.report(context, token, incidentId, onApply),
        owner,
        () -> context.identity.activity(token));
  }

  static VoiceDialog forIncident(
      AppContext context, String token, Window owner, boolean replaces, Consumer<String> accepted) {
    return new VoiceDialog(
        VoiceDraftSession.incident(context, token, replaces, accepted),
        owner,
        () -> context.identity.activity(token));
  }

  VoiceDialog(VoiceDraftSession session, Window owner, Runnable activity) {
    this.session = session;
    humanActivity = activity;
    apply.setText(session.acceptLabel());
    dialog.initOwner(owner);
    dialog.setTitle("Hablar y preparar el texto");
    dialog.setResizable(true);
    dialog.getDialogPane().setPrefSize(860, 760);
    dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
    for (String title : List.of("1  Habla", "2  Preparamos", "3  Revisa")) {
      Label item = new Label(title);
      item.getStyleClass().add("voice-step");
      steps.getChildren().add(item);
    }
    stageTitle.getStyleClass().add("section-title");
    status.setWrapText(true);
    status.setId("voice-status");
    pulse.getStyleClass().add("listening-orb");
    pulse.setAccessibleText("Indicador visual de grabación");
    listeningAnimation.setFromX(1);
    listeningAnimation.setFromY(1);
    listeningAnimation.setToX(1.28);
    listeningAnimation.setToY(1.28);
    listeningAnimation.setAutoReverse(true);
    listeningAnimation.setCycleCount(Animation.INDEFINITE);
    progress.setPrefSize(34, 34);
    progress.setMaxSize(34, 34);
    speak.getStyleClass().addAll("primary", "speak-button");
    speak.setId("voice-speak");
    speak.setGraphic(microphoneIcon());
    HBox capture = new HBox(24, new StackPane(pulse), new VBox(8, stageTitle, elapsed));
    capture.setAlignment(Pos.CENTER_LEFT);
    HBox actions = new HBox(12, speak, prepare, progress);
    actions.setAlignment(Pos.CENTER_LEFT);
    VBox hero = new VBox(18, capture, status, actions);
    hero.getStyleClass().add("voice-hero");
    source.setWrapText(true);
    source.setPromptText("También puedes escribir aquí o corregir lo que entendimos.");
    source.setPrefRowCount(5);
    source.setId("voice-source");
    proposal.setWrapText(true);
    proposal.setPrefRowCount(9);
    proposal.setId("voice-result");
    proposal.setPromptText("Aquí aparecerá tu texto organizado.");
    Label explanation =
        new Label(
            "Corrige lo que necesites. El texto se guardará cuando pulses el botón de abajo.");
    explanation.setWrapText(true);
    explanation.getStyleClass().add("muted");
    apply.getStyleClass().add("primary");
    apply.setId("voice-apply");
    review
        .getChildren()
        .addAll(new Label("Tu texto, listo para revisar"), proposal, explanation, apply);
    review.getStyleClass().add("voice-review");
    Button refresh = new Button("Actualizar resultado");
    refresh.setOnAction(e -> prepareWritten());
    VBox sourceBox = new VBox(10, source, refresh, importAudio);
    correction.setText("Escribir o corregir la narración");
    correction.setContent(sourceBox);
    correction.setExpanded(false);
    history.setText("Recuperar una narración anterior");
    history.setContent(entries);
    history.setExpanded(false);
    entries.setMaxWidth(Double.MAX_VALUE);
    entries.setCellFactory(v -> cell());
    entries.setButtonCell(cell());
    try {
      entries.setItems(FXCollections.observableArrayList(session.history()));
    } catch (Exception e) {
      status.setText(e.getMessage());
    }
    VBox form = new VBox(16, steps, hero, review, correction, history);
    form.setPadding(new Insets(8));
    ScrollPane scroll = new ScrollPane(form);
    scroll.setFitToWidth(true);
    dialog.getDialogPane().setContent(scroll);
    dialog
        .getDialogPane()
        .getButtonTypes()
        .add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
    entries
        .valueProperty()
        .addListener(
            (o, old, value) -> {
              if (loading) return;
              if (old != null) correct(old);
              load(value);
            });
    source
        .textProperty()
        .addListener(
            (o, a, b) -> {
              if (loading) return;
              apply.setDisable(true);
              debounce.playFromStart();
              if (!proposal.getText().isBlank())
                status.setText(
                    "Cambiaste la narración. Pulsa Actualizar resultado para preparar tu"
                        + " corrección.");
            });
    proposal.textProperty().addListener((o, a, b) -> updateButtons());
    debounce.setOnFinished(e -> saveSource());
    speak.setOnAction(
        e -> {
          if (recording == null) beginRecording();
          else stopRecording(true);
        });
    prepare.setOnAction(
        e -> {
          VoiceEntry entry = entries.getValue();
          if (entry != null && source.getText().isBlank() && !entry.audioPath().isBlank())
            processAudio(entry);
          else {
            correction.setExpanded(true);
            prepareWritten();
          }
        });
    importAudio.setOnAction(
        e -> {
          FileChooser chooser = new FileChooser();
          chooser.setTitle("Importar narración");
          chooser
              .getExtensionFilters()
              .add(new FileChooser.ExtensionFilter("Audio WAV (*.wav)", "*.wav"));
          var file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
          if (file != null && saveSource())
            run(
                Step.TRANSCRIBING,
                () -> {
                  VoiceEntry entry = session.create(true);
                  AudioFiles.normalize(file.toPath(), session.audio(entry));
                  return process(entry);
                },
                this::readyToReview);
        });
    apply.setOnAction(e -> accept());
    timer =
        new Timeline(
            new KeyFrame(
                Duration.seconds(1),
                e -> {
                  if (recording != null) {
                    long seconds = (System.nanoTime() - recordingStarted) / 1_000_000_000;
                    elapsed.setText(
                        "Escuchando · %02d:%02d / 03:00".formatted(seconds / 60, seconds % 60));
                    if (!recording.active()) stopRecording(true);
                  }
                }));
    timer.setCycleCount(Animation.INDEFINITE);
    dialog.setOnShown(
        e -> {
          var scene = dialog.getDialogPane().getScene();
          scene.addEventFilter(KeyEvent.KEY_PRESSED, k -> activity());
          scene.addEventFilter(MouseEvent.MOUSE_PRESSED, k -> activity());
          scene.addEventFilter(ScrollEvent.SCROLL, k -> activity());
          if (recordAfterOpening) {
            recordAfterOpening = false;
            Platform.runLater(
                () -> {
                  if (dialog.isShowing()) beginRecording();
                });
          }
        });
    dialog.setOnHidden(
        e -> {
          closed = true;
          debounce.stop();
          stopRecording(false);
          saveSource();
          if (task != null) task.cancel(true);
          worker.shutdownNow();
          listeningAnimation.stop();
          timer.stop();
        });
    if (!entries.getItems().isEmpty()) entries.setValue(entries.getItems().getFirst());
    setStep(Step.READY);
    if (entries.getValue() != null && !entries.getValue().proposal().isBlank())
      setStep(Step.REVIEW);
    updateButtons();
  }

  private javafx.scene.Node microphoneIcon() {
    javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
    icon.setContent(
        "M 8 2 A 4 4 0 0 1 16 2 L 16 10 A 4 4 0 0 1 8 10 Z M 5 9 L 5 10 A 7 7 0 0 0 19 10 L 19 9 M"
            + " 12 17 L 12 22 M 7 22 L 17 22");
    icon.getStyleClass().add("microphone-icon");
    return icon;
  }

  private ListCell<VoiceEntry> cell() {
    return new ListCell<>() {
      @Override
      protected void updateItem(VoiceEntry entry, boolean empty) {
        super.updateItem(entry, empty);
        setText(
            empty || entry == null
                ? null
                : (entry.audioPath().isBlank() ? "Narración escrita" : "Grabación")
                    + " · "
                    + java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")
                        .withZone(java.time.ZoneId.systemDefault())
                        .format(entry.createdAt()));
      }
    };
  }

  void show() {
    dialog.showAndWait();
  }

  void showAndRecord() {
    recordAfterOpening = true;
    show();
  }

  private void activity() {
    try {
      humanActivity.run();
    } catch (Exception e) {
      status.setText("La sesión terminó. Ingresa nuevamente para continuar.");
    }
  }

  private boolean correct(VoiceEntry entry) {
    try {
      session.correct(entry, source.getText());
      return true;
    } catch (Exception e) {
      status.setText(e.getMessage());
      return false;
    }
  }

  private boolean saveSource() {
    if (loading || busy) return true;
    VoiceEntry entry = entries.getValue();
    if (entry == null && source.getText().isBlank()) return true;
    try {
      if (entry == null) {
        String text = source.getText();
        entry = session.create(false);
        loading = true;
        entries.getItems().addFirst(entry);
        entries.setValue(entry);
        loading = false;
        source.setText(text);
      }
      return correct(entry);
    } catch (Exception e) {
      loading = false;
      status.setText(e.getMessage());
      return false;
    }
  }

  private void replace(VoiceEntry entry) {
    loading = true;
    entries.getItems().removeIf(old -> old.id() == entry.id());
    entries.getItems().addFirst(entry);
    entries.setValue(entry);
    loading = false;
    load(entry);
  }

  private void load(VoiceEntry entry) {
    loading = true;
    source.setText(entry == null ? "" : entry.correctedText());
    proposal.setText(entry == null ? "" : entry.proposal());
    loading = false;
    if (!busy && recording == null)
      setStep(entry != null && !entry.proposal().isBlank() ? Step.REVIEW : Step.READY);
    updateButtons();
  }

  private void readyToReview(VoiceEntry entry) {
    replace(entry);
    setStep(Step.REVIEW);
    status.setText(
        "Listo. Lee el resultado, corrige si hace falta y guarda cuando estés conforme.");
  }

  private void setStep(Step next) {
    step = next;
    for (int i = 0; i < steps.getChildren().size(); i++) {
      var styles = steps.getChildren().get(i).getStyleClass();
      styles.remove("voice-step-active");
      int current =
          switch (next) {
            case TRANSCRIBING, ORGANIZING -> 1;
            case REVIEW -> 2;
            default -> 0;
          };
      if (i == current) styles.add("voice-step-active");
    }
    String title =
        switch (next) {
          case READY -> "¿Qué quieres documentar?";
          case LISTENING -> "Te estamos escuchando";
          case TRANSCRIBING -> "Estamos pasando tu voz a texto";
          case ORGANIZING -> "Estamos ordenando tu narración";
          case REVIEW -> "Revisa tu texto";
          case ERROR -> "Tu trabajo sigue guardado";
        };
    stageTitle.setText(title);
    if (next == Step.REVIEW)
      status.setText("Listo. Revisa el texto y corrige lo que necesites antes de guardarlo.");
    if (next == Step.LISTENING) listeningAnimation.playFromStart();
    else {
      listeningAnimation.stop();
      pulse.setScaleX(1);
      pulse.setScaleY(1);
    }
    progress.setVisible(next == Step.TRANSCRIBING || next == Step.ORGANIZING);
    progress.setManaged(progress.isVisible());
    if (next != Step.LISTENING)
      elapsed.setText(
          next == Step.TRANSCRIBING || next == Step.ORGANIZING
              ? "Procesamiento local · Puedes cancelar cerrando"
              : "Micrófono apagado");
    review.setVisible(next == Step.REVIEW || !proposal.getText().isBlank());
    review.setManaged(review.isVisible());
    updateButtons();
  }

  private void updateButtons() {
    boolean capturing = recording != null;
    VoiceEntry entry = entries.getValue();
    speak.setText(capturing ? "Detener" : step == Step.REVIEW ? "Volver a hablar" : "Hablar");
    speak.setDisable(busy);
    prepare.setDisable(busy || capturing);
    prepare.setText(step == Step.ERROR ? "Reintentar" : "Preparar texto");
    prepare.setVisible(!capturing && step != Step.REVIEW);
    prepare.setManaged(prepare.isVisible());
    importAudio.setDisable(busy || capturing);
    source.setEditable(!busy && !capturing);
    proposal.setEditable(!busy && !capturing);
    entries.setDisable(busy || capturing);
    apply.setDisable(
        busy
            || capturing
            || entry == null
            || proposal.getText().isBlank()
            || entry.proposal().isBlank()
            || !entry.sourceHash().equals(AiRuntime.hash(source.getText().strip())));
    correction.setDisable(busy || capturing);
    history.setDisable(busy || capturing);
  }

  private void beginRecording() {
    if (!saveSource()) return;
    try {
      VoiceEntry entry = session.create(true);
      replace(entry);
      recording = new AudioFiles.Recording(session.audio(entry));
      recordingStarted = System.nanoTime();
      timer.play();
      setStep(Step.LISTENING);
      status.setText(
          "Habla con naturalidad. Al terminar, pulsa Detener. Nosotros preparamos el texto.");
    } catch (Exception e) {
      setStep(Step.ERROR);
      status.setText(e.getMessage() + " Puedes escribir la narración abajo.");
      correction.setExpanded(true);
    }
  }

  private void stopRecording(boolean process) {
    if (recording == null) return;
    boolean saved = false;
    try {
      recording.close();
      saved = true;
    } catch (Exception e) {
      status.setText(e.getMessage());
    } finally {
      recording = null;
      timer.stop();
      listeningAnimation.stop();
      updateButtons();
    }
    if (saved && process && !closed) processAudio(entries.getValue());
    else setStep(Step.READY);
  }

  // Package visibility allows a deterministic pipeline test without recording ambient audio.
  void processAudio(VoiceEntry entry) {
    run(Step.TRANSCRIBING, () -> process(entry), this::readyToReview);
  }

  private VoiceEntry process(VoiceEntry entry) throws Exception {
    VoiceEntry transcript = session.transcribe(entry);
    Platform.runLater(
        () -> {
          if (!closed) {
            replace(transcript);
            setStep(Step.ORGANIZING);
            status.setText(
                "Ya tenemos la transcripción. Ahora ordenamos las ideas para que puedas"
                    + " revisarlas.");
          }
        });
    return session.organize(transcript, transcript.correctedText());
  }

  private void prepareWritten() {
    if (source.getText().isBlank()) {
      correction.setExpanded(true);
      source.requestFocus();
      status.setText("Escribe tu narración o pulsa Hablar para comenzar.");
      return;
    }
    if (!saveSource()) return;
    VoiceEntry entry = entries.getValue();
    String text = source.getText();
    run(Step.ORGANIZING, () -> session.organize(entry, text), this::readyToReview);
  }

  private void accept() {
    try {
      VoiceEntry entry = entries.getValue();
      if (session.replacesContent()) {
        Alert confirm =
            new Alert(
                Alert.AlertType.CONFIRMATION,
                "Se reemplazará el texto actual del formulario. Las versiones guardadas del reporte"
                    + " se conservan.",
                ButtonType.OK,
                ButtonType.CANCEL);
        confirm.initOwner(dialog.getDialogPane().getScene().getWindow());
        confirm.setHeaderText("¿Usar el texto revisado?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
      }
      session.accept(entry, source.getText(), proposal.getText());
      dialog.close();
    } catch (Exception e) {
      status.setText(e.getMessage());
    }
  }

  private <T> void run(Step next, Callable<T> operation, Consumer<T> success) {
    if (busy || closed) return;
    busy = true;
    debounce.stop();
    setStep(next);
    status.setText(
        next == Step.TRANSCRIBING
            ? "Tu audio está guardado. Estamos preparando el texto…"
            : "Estamos preparando tu texto…");
    Task<T> job =
        new Task<>() {
          @Override
          protected T call() throws Exception {
            return operation.call();
          }
        };
    task = job;
    job.setOnSucceeded(
        e -> {
          busy = false;
          if (!closed) success.accept(job.getValue());
          updateButtons();
        });
    job.setOnFailed(
        e -> {
          busy = false;
          if (!closed) {
            setStep(Step.ERROR);
            status.setText(
                job.getException().getMessage() + " Puedes reintentar o corregir la narración.");
            correction.setExpanded(true);
          }
          updateButtons();
        });
    worker.submit(job);
  }
}
