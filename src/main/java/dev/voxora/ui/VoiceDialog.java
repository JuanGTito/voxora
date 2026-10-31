package dev.voxora.ui;

import dev.voxora.bootstrap.AppContext;
import dev.voxora.domain.Model.*;
import dev.voxora.voice.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javafx.animation.*;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

/** Voice preparation is separate from submission and supervisor approval. */
/* LFTY JGTM SCGC YARS DMFY */ final class VoiceDialog {
  /* LFTY JGTM SCGC YARS DMFY */ private final AppContext context;
  /* LFTY JGTM SCGC YARS DMFY */ private final String token;
  /* LFTY JGTM SCGC YARS DMFY */ private final long incidentId;
  /* LFTY JGTM SCGC YARS DMFY */ private final Dialog<ButtonType> dialog = new Dialog<>();
  /* LFTY JGTM SCGC YARS DMFY */ private final TextArea source = new TextArea(),
      proposal = new TextArea();
  /* LFTY JGTM SCGC YARS DMFY */ private final ComboBox<VoiceEntry> entries = new ComboBox<>();
  /* LFTY JGTM SCGC YARS DMFY */ private final Label status =
      new Label("Graba de forma explícita, importa un WAV o escribe tu narración.");
  /* LFTY JGTM SCGC YARS DMFY */ private final FlowPane actions = new FlowPane(10, 10);
  /* LFTY JGTM SCGC YARS DMFY */ private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          /* LFTY JGTM SCGC YARS DMFY */ r -> {
            /* LFTY JGTM SCGC YARS DMFY */ Thread t = new Thread(r, "voxora-voice");
            t.setDaemon(true);
            return t;
          });
  /* LFTY JGTM SCGC YARS DMFY */ private final Consumer<Version> onApply;
  /* LFTY JGTM SCGC YARS DMFY */ private final PauseTransition debounce =
      new PauseTransition(Duration.seconds(1));
  /* LFTY JGTM SCGC YARS DMFY */ private final Timeline recordingTimer;
  /* LFTY JGTM SCGC YARS DMFY */ private AudioFiles.Recording recording;
  /* LFTY JGTM SCGC YARS DMFY */ private Task<?> task;
  /* LFTY JGTM SCGC YARS DMFY */ private boolean loading, busy;
  /* LFTY JGTM SCGC YARS DMFY */ private final Button record = new Button("Grabar micrófono"),
      stop = new Button("Detener y guardar"),
      transcribe = new Button("Transcribir audio"),
      extract = new Button("Organizar con IA"),
      apply = new Button("Usar como borrador"),
      importAudio = new Button("Importar WAV");

  /* LFTY JGTM SCGC YARS DMFY */ VoiceDialog(
      /* LFTY JGTM SCGC YARS DMFY */ AppContext context, /* LFTY JGTM SCGC YARS DMFY */
      String token, /* LFTY JGTM SCGC YARS DMFY */
      long incidentId, /* LFTY JGTM SCGC YARS DMFY */
      Window owner, /* LFTY JGTM SCGC YARS DMFY */
      Consumer<Version> onApply) {
    this.context = context;
    this.token = token;
    this.incidentId = incidentId;
    this.onApply = onApply;
    dialog.initOwner(owner);
    dialog.setTitle("Voz y narración · " + context.incidents.read(token, incidentId).code());
    dialog.setResizable(true);
    dialog.getDialogPane().setPrefSize(1000, 800);
    dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
    entries.setItems(FXCollections.observableArrayList(context.voice.entries(token, incidentId)));
    entries.setMaxWidth(Double.MAX_VALUE);
    entries.setCellFactory(/* LFTY JGTM SCGC YARS DMFY */ v -> cell());
    entries.setButtonCell(cell());
    entries.setPromptText("Capturas y narraciones guardadas");
    source.setWrapText(true);
    source.setPromptText(
        "Corrige la transcripción o escribe la narración. El original se conserva.");
    source.setPrefRowCount(8);
    proposal.setWrapText(true);
    proposal.setEditable(false);
    proposal.setPrefRowCount(9);
    status.setWrapText(true);
    actions.getChildren().addAll(record, stop, importAudio, transcribe, extract, apply);
    apply.getStyleClass().add("primary");
    /* LFTY JGTM SCGC YARS DMFY */ VBox form =
        new VBox(
            12,
            new Label("Capturas guardadas"),
            entries,
            actions,
            status,
            new Label("Narración revisable"),
            source,
            new Label("Propuesta organizada que debes revisar"),
            proposal,
            new Label(
                "La IA clasifica frases originales. No crea tickets, envía reportes ni aprueba"
                    + " cierres."));
    form.setPadding(new Insets(8));
    /* LFTY JGTM SCGC YARS DMFY */ ScrollPane scroll = new ScrollPane(form);
    scroll.setFitToWidth(true);
    dialog.getDialogPane().setContent(scroll);
    dialog
        .getDialogPane()
        .getButtonTypes()
        .add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
    entries
        .valueProperty()
        .addListener(
            (/* LFTY JGTM SCGC YARS DMFY */ o, /* LFTY JGTM SCGC YARS DMFY */
                old, /* LFTY JGTM SCGC YARS DMFY */
                value) -> {
              /* LFTY JGTM SCGC YARS DMFY */ if (loading) return;
              try {
                /* LFTY JGTM SCGC YARS DMFY */ if (old != null)
                  context.voice.saveCorrection(token, old.id(), source.getText());
              } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
                status.setText(e.getMessage());
              }
              load(value);
            });
    source
        .textProperty()
        .addListener(
            (/* LFTY JGTM SCGC YARS DMFY */ o, /* LFTY JGTM SCGC YARS DMFY */
                a, /* LFTY JGTM SCGC YARS DMFY */
                b) -> {
              /* LFTY JGTM SCGC YARS DMFY */ if (loading) return;
              apply.setDisable(true);
              debounce.playFromStart();
            });
    debounce.setOnFinished(/* LFTY JGTM SCGC YARS DMFY */ e -> saveSource());
    record.setOnAction(/* LFTY JGTM SCGC YARS DMFY */ e -> beginRecording());
    stop.setOnAction(/* LFTY JGTM SCGC YARS DMFY */ e -> stopRecording());
    importAudio.setOnAction(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          /* LFTY JGTM SCGC YARS DMFY */ FileChooser chooser = new FileChooser();
          chooser.setTitle("Importar narración WAV");
          chooser
              .getExtensionFilters()
              .add(new FileChooser.ExtensionFilter("Audio WAV (*.wav)", "*.wav"));
          /* LFTY JGTM SCGC YARS DMFY */ var file = chooser.showOpenDialog(owner);
          /* LFTY JGTM SCGC YARS DMFY */ if (file != null) {
            saveSource();
            run(
                () -> context.voice.importAudio(token, incidentId, file.toPath()),
                /* LFTY JGTM SCGC YARS DMFY */ entry -> {
                  select(entry);
                  status.setText("Audio importado y conservado. Pulsa Transcribir audio.");
                });
          }
        });
    transcribe.setOnAction(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry = entries.getValue();
          /* LFTY JGTM SCGC YARS DMFY */ if (entry != null)
            run(
                () -> context.voice.transcribe(token, entry.id()),
                /* LFTY JGTM SCGC YARS DMFY */ value -> {
                  replace(value);
                  status.setText(
                      "Transcripción local completada. Revisa el texto antes de organizarlo.");
                });
        });
    extract.setOnAction(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          /* LFTY JGTM SCGC YARS DMFY */ if (!saveSource()) return;
          /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry = entries.getValue();
          /* LFTY JGTM SCGC YARS DMFY */ String text = source.getText();
          run(
              () -> context.voice.extract(token, entry.id(), text),
              /* LFTY JGTM SCGC YARS DMFY */ value -> {
                replace(value);
                status.setText(
                    "Propuesta lista. Revisa el contenido antes de usarlo como borrador.");
              });
        });
    apply.setOnAction(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          try {
            /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry = entries.getValue();
            /* LFTY JGTM SCGC YARS DMFY */ Incident incident =
                context.incidents.read(token, incidentId);
            /* LFTY JGTM SCGC YARS DMFY */ if (!incident.draft().isBlank()) {
              /* LFTY JGTM SCGC YARS DMFY */ Alert confirm =
                  new Alert(
                      Alert.AlertType.CONFIRMATION,
                      "Se reemplazará el texto vigente del editor. Las versiones anteriores se"
                          + " conservarán.",
                      ButtonType.OK,
                      ButtonType.CANCEL);
              confirm.initOwner(dialog.getDialogPane().getScene().getWindow());
              confirm.setHeaderText("Usar la propuesta como borrador");
              /* LFTY JGTM SCGC YARS DMFY */ if (confirm.showAndWait().orElse(ButtonType.CANCEL)
                  != ButtonType.OK) return;
            }
            /* LFTY JGTM SCGC YARS DMFY */ Version v =
                context.voice.apply(token, entry.id(), source.getText());
            onApply.accept(v);
            dialog.close();
          } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception failure) {
            status.setText(failure.getMessage());
          }
        });
    recordingTimer =
        new Timeline(
            new KeyFrame(
                Duration.seconds(1),
                /* LFTY JGTM SCGC YARS DMFY */ e -> {
                  /* LFTY JGTM SCGC YARS DMFY */ if (recording != null && !recording.active())
                    stopRecording();
                }));
    recordingTimer.setCycleCount(Animation.INDEFINITE);
    dialog.setOnShown(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          /* LFTY JGTM SCGC YARS DMFY */ var scene = dialog.getDialogPane().getScene();
          scene.addEventFilter(
              KeyEvent.KEY_PRESSED, /* LFTY JGTM SCGC YARS DMFY */ k -> activity());
          scene.addEventFilter(
              MouseEvent.MOUSE_PRESSED, /* LFTY JGTM SCGC YARS DMFY */ k -> activity());
          scene.addEventFilter(ScrollEvent.SCROLL, /* LFTY JGTM SCGC YARS DMFY */ k -> activity());
        });
    dialog.setOnHidden(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          debounce.stop();
          recordingTimer.stop();
          saveSource();
          stopRecording();
          /* LFTY JGTM SCGC YARS DMFY */ if (task != null) task.cancel(true);
          worker.shutdownNow();
        });
    /* LFTY JGTM SCGC YARS DMFY */ if (!entries.getItems().isEmpty())
      entries.setValue(entries.getItems().getFirst());
    updateButtons();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private ListCell<VoiceEntry> cell() {
    return new ListCell<>() /* LFTY JGTM SCGC YARS DMFY */ {
      /* LFTY JGTM SCGC YARS DMFY */ @Override
      protected void updateItem(
          /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry, /* LFTY JGTM SCGC YARS DMFY */
          boolean empty) {
        super.updateItem(entry, empty);
        setText(
            empty || entry == null
                ? null
                : "#"
                    + entry.id()
                    + " · "
                    + (entry.audioPath().isBlank() ? "Narración escrita" : "Audio")
                    + " · "
                    + entry.createdAt());
      }
    };
  }

  /* LFTY JGTM SCGC YARS DMFY */ void show() {
    dialog.showAndWait();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void activity() {
    try {
      context.identity.activity(token);
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      status.setText("La sesión terminó. Vuelve a ingresar para continuar.");
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private boolean saveSource() {
    /* LFTY JGTM SCGC YARS DMFY */ if (loading || busy) return true;
    try {
      /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry = entries.getValue();
      /* LFTY JGTM SCGC YARS DMFY */ if (entry == null) {
        /* LFTY JGTM SCGC YARS DMFY */ if (source.getText().isBlank()) {
          status.setText("Escribe o transcribe una narración.");
          return false;
        }
        /* LFTY JGTM SCGC YARS DMFY */ String text = source.getText();
        entry = context.voice.create(token, incidentId, false);
        loading = true;
        entries.getItems().addFirst(entry);
        entries.setValue(entry);
        loading = false;
        source.setText(text);
      }
      context.voice.saveCorrection(token, entry.id(), source.getText());
      return true;
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      loading = false;
      status.setText(e.getMessage());
      return false;
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void select(
      /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry) {
    entries.getItems().addFirst(entry);
    entries.setValue(entry);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void replace(
      /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry) {
    loading = true;
    entries.getItems().removeIf(/* LFTY JGTM SCGC YARS DMFY */ old -> old.id() == entry.id());
    entries.getItems().addFirst(entry);
    entries.setValue(entry);
    loading = false;
    load(entry);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void load(
      /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry) {
    loading = true;
    source.setText(entry == null ? "" : entry.correctedText());
    proposal.setText(entry == null ? "" : entry.proposal());
    loading = false;
    updateButtons();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void updateButtons() {
    /* LFTY JGTM SCGC YARS DMFY */ boolean capturing = recording != null;
    /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry = entries.getValue();
    record.setDisable(busy || capturing);
    stop.setDisable(!capturing);
    importAudio.setDisable(busy || capturing);
    transcribe.setDisable(busy || capturing || entry == null || entry.audioPath().isBlank());
    extract.setDisable(busy || capturing);
    apply.setDisable(
        busy
            || capturing
            || entry == null
            || entry.proposal().isBlank()
            || !entry.sourceHash().equals(AiRuntime.hash(source.getText().strip())));
    source.setEditable(!busy && !capturing);
    entries.setDisable(busy || capturing);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void beginRecording() {
    saveSource();
    try {
      /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry =
          context.voice.create(token, incidentId, true);
      select(entry);
      recording = new AudioFiles.Recording(context.voice.audioPath(entry));
      recordingTimer.play();
      status.setText("Grabando. Pulsa Detener y guardar al terminar. Límite: 3 minutos.");
      updateButtons();
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      status.setText(e.getMessage());
      updateButtons();
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void stopRecording() {
    /* LFTY JGTM SCGC YARS DMFY */ if (recording == null) return;
    try {
      recording.close();
      status.setText("Grabación conservada. Puedes transcribirla.");
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      status.setText(e.getMessage());
    } finally {
      recording = null;
      recordingTimer.stop();
      updateButtons();
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private <T> void run(
      /* LFTY JGTM SCGC YARS DMFY */ Callable<T> operation, /* LFTY JGTM SCGC YARS DMFY */
      Consumer<T> success) {
    /* LFTY JGTM SCGC YARS DMFY */ if (busy) return;
    busy = true;
    debounce.stop();
    status.setText("Procesando localmente. Puedes cerrar para cancelar; se conserva lo guardado.");
    updateButtons();
    /* LFTY JGTM SCGC YARS DMFY */ Task<T> job =
        new Task<>() /* LFTY JGTM SCGC YARS DMFY */ {
          /* LFTY JGTM SCGC YARS DMFY */ @Override
          protected T call() throws Exception {
            return operation.call();
          }
        };
    task = job;
    job.setOnSucceeded(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          busy = false;
          /* LFTY JGTM SCGC YARS DMFY */ if (dialog.isShowing()) success.accept(job.getValue());
          updateButtons();
        });
    job.setOnFailed(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          busy = false;
          /* LFTY JGTM SCGC YARS DMFY */ if (dialog.isShowing())
            status.setText(job.getException().getMessage());
          updateButtons();
        });
    worker.submit(job);
  }
}
