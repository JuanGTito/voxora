package dev.voxora.ui;

import dev.voxora.bootstrap.AppContext;
import dev.voxora.domain.Model.*;
import java.nio.file.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import javafx.animation.*;
import javafx.application.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;

/* LFTY JGTM SCGC YARS DMFY */ public final class VoxoraApplication extends Application {
  /* LFTY JGTM SCGC YARS DMFY */ private AppContext context;
  /* LFTY JGTM SCGC YARS DMFY */ private Stage stage;
  /* LFTY JGTM SCGC YARS DMFY */ private BorderPane shell;
  /* LFTY JGTM SCGC YARS DMFY */ private String token;
  /* LFTY JGTM SCGC YARS DMFY */ private User user;
  /* LFTY JGTM SCGC YARS DMFY */ private WorkspaceViewModel model;
  /* LFTY JGTM SCGC YARS DMFY */ private final Label feedback = new Label();
  /* LFTY JGTM SCGC YARS DMFY */ private final Label idleWarning = new Label();
  /* LFTY JGTM SCGC YARS DMFY */ private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          /* LFTY JGTM SCGC YARS DMFY */ r -> {
            /* LFTY JGTM SCGC YARS DMFY */ Thread t = new Thread(r, "voxora-io");
            t.setDaemon(true);
            return t;
          });
  /* LFTY JGTM SCGC YARS DMFY */ private Timeline timer;
  /* LFTY JGTM SCGC YARS DMFY */ private Runnable autosave = () -> {};
  /* LFTY JGTM SCGC YARS DMFY */ private String currentPage = "Inicio";
  /* LFTY JGTM SCGC YARS DMFY */ private boolean busy;
  /* LFTY JGTM SCGC YARS DMFY */ private int inactivityMinutes = 15;
  /* LFTY JGTM SCGC YARS DMFY */ private final boolean failFast;
  /* LFTY JGTM SCGC YARS DMFY */ private final DateTimeFormatter dates =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

  /* LFTY JGTM SCGC YARS DMFY */ public VoxoraApplication() {
    failFast = false;
  }

  /* LFTY JGTM SCGC YARS DMFY */ VoxoraApplication(
      /* LFTY JGTM SCGC YARS DMFY */ AppContext context) {
    this.context = context;
    failFast = true;
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public void start(/* LFTY JGTM SCGC YARS DMFY */ Stage stage) {
    this.stage = stage;
    try {
      /* LFTY JGTM SCGC YARS DMFY */ if (context == null) {
        /* LFTY JGTM SCGC YARS DMFY */ String path = getParameters().getNamed().get("data");
        context = new AppContext(path == null ? AppContext.defaultRoot() : Path.of(path));
      }
      stage.setTitle("Voxora · Gestión técnica");
      stage.setMinWidth(1040);
      stage.setMinHeight(700);
      shell = new BorderPane();
      /* LFTY JGTM SCGC YARS DMFY */ Scene scene = new Scene(shell, 1280, 820);
      scene
          .getStylesheets()
          .add(
              Objects.requireNonNull(getClass().getResource("/styles/voxora.css"))
                  .toExternalForm());
      scene.addEventFilter(KeyEvent.KEY_PRESSED, /* LFTY JGTM SCGC YARS DMFY */ e -> activity());
      scene.addEventFilter(
          MouseEvent.MOUSE_PRESSED, /* LFTY JGTM SCGC YARS DMFY */ e -> activity());
      scene.addEventFilter(ScrollEvent.SCROLL, /* LFTY JGTM SCGC YARS DMFY */ e -> activity());
      stage.setScene(scene);
      stage.setOnCloseRequest(
          /* LFTY JGTM SCGC YARS DMFY */ e -> {
            try {
              autosave.run();
            } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception failure) {
              e.consume();
              error(failure);
            }
          });
      login();
      stage.show();
      timer =
          new Timeline(
              new KeyFrame(Duration.seconds(5), /* LFTY JGTM SCGC YARS DMFY */ e -> tick()));
      timer.setCycleCount(Animation.INDEFINITE);
      timer.play();
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      /* LFTY JGTM SCGC YARS DMFY */ if (failFast) throw new IllegalStateException(e);
      e.printStackTrace(System.err);
      error(e);
      Platform.exit();
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void activity() {
    /* LFTY JGTM SCGC YARS DMFY */ if (token != null && !busy)
      try {
        context.identity.activity(token);
      } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception expired) {
        logout(true);
      }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void tick() {
    /* LFTY JGTM SCGC YARS DMFY */ if (token == null || busy) return;
    try {
      /* LFTY JGTM SCGC YARS DMFY */ long seconds = context.identity.idle(token).toSeconds();
      /* LFTY JGTM SCGC YARS DMFY */ if (seconds >= inactivityMinutes * 60L) {
        logout(true);
        return;
      }
      autosave.run();
      idleWarning.setText(
          seconds >= (inactivityMinutes - 1) * 60L
              ? "La sesión se bloqueará en menos de un minuto. Interactúa para continuar."
              : "");
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      /* LFTY JGTM SCGC YARS DMFY */ if (token != null) {
        feedback.setText(e.getMessage());
        try {
          context.identity.current(token);
        } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception expired) {
          logout(true);
        }
      }
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void login() {
    autosave = () -> {};
    shell.setLeft(null);
    shell.setTop(null);
    shell.setBottom(null);
    /* LFTY JGTM SCGC YARS DMFY */ boolean setup = context.identity.needsSetup();
    /* LFTY JGTM SCGC YARS DMFY */ VBox form = new VBox(16);
    form.getStyleClass().add("login-card");
    form.setMaxWidth(440);
    form.setMaxHeight(Region.USE_PREF_SIZE);
    /* LFTY JGTM SCGC YARS DMFY */ Label brand = label("voxora", "brand-login");
    /* LFTY JGTM SCGC YARS DMFY */ Label heading =
        label(setup ? "Prepara tu instalación" : "Bienvenido de nuevo", "title");
    /* LFTY JGTM SCGC YARS DMFY */ Label hint =
        muted(
            setup
                ? "Crea la primera cuenta de soporte. Después podrás añadir supervisores y"
                    + " técnicos."
                : "Ingresa a tu espacio de trabajo para continuar.");
    hint.setWrapText(true);
    /* LFTY JGTM SCGC YARS DMFY */ TextField username = field("Usuario");
    /* LFTY JGTM SCGC YARS DMFY */ PasswordField password = new PasswordField();
    password.setPromptText(setup ? "Contraseña (mínimo 10 caracteres)" : "Contraseña");
    /* LFTY JGTM SCGC YARS DMFY */ PasswordField confirmation = new PasswordField();
    confirmation.setPromptText("Repite la contraseña");
    /* LFTY JGTM SCGC YARS DMFY */ Label error = new Label();
    error.getStyleClass().add("error");
    error.setWrapText(true);
    /* LFTY JGTM SCGC YARS DMFY */ Button submit =
        new Button(setup ? "Crear instalación" : "Iniciar sesión");
    submit.getStyleClass().add("primary");
    submit.setMaxWidth(Double.MAX_VALUE);
    submit.setDefaultButton(true);
    form.getChildren()
        .addAll(
            brand, heading, hint, labeled("Usuario", username), labeled("Contraseña", password));
    /* LFTY JGTM SCGC YARS DMFY */ if (setup)
      form.getChildren().add(labeled("Confirmación", confirmation));
    form.getChildren()
        .addAll(error, submit, muted("Trabajo local · Revisión humana · Sin conexión"));
    /* LFTY JGTM SCGC YARS DMFY */ StackPane center = new StackPane(form);
    center.setPadding(new Insets(28));
    shell.setCenter(center);
    submit.setOnAction(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          /* LFTY JGTM SCGC YARS DMFY */ if (setup
              && !password.getText().equals(confirmation.getText())) {
            error.setText("Las contraseñas no coinciden.");
            return;
          }
          /* LFTY JGTM SCGC YARS DMFY */ char[] secret = password.getText().toCharArray();
          /* LFTY JGTM SCGC YARS DMFY */ String name = username.getText();
          password.clear();
          confirmation.clear();
          async(
              () -> {
                try {
                  /* LFTY JGTM SCGC YARS DMFY */ if (setup) context.identity.setup(name, secret);
                  return context.identity.login(name, secret);
                } finally {
                  Arrays.fill(secret, '\0');
                }
              },
              /* LFTY JGTM SCGC YARS DMFY */ session -> {
                token = session;
                user = context.identity.current(token);
                inactivityMinutes = context.identity.inactivityMinutes(token);
                model = new WorkspaceViewModel(context.incidents, token);
                workspace();
              },
              /* LFTY JGTM SCGC YARS DMFY */ failure -> error.setText(failure.getMessage()));
        });
    Platform.runLater(username::requestFocus);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void workspace() {
    /* LFTY JGTM SCGC YARS DMFY */ VBox navigation = new VBox(8);
    navigation.getStyleClass().add("sidebar");
    navigation.setPrefWidth(220);
    navigation
        .getChildren()
        .addAll(label("voxora", "brand"), label("GESTIÓN TÉCNICA", "eyebrow"), new Separator());
    /* LFTY JGTM SCGC YARS DMFY */ List<String> pages =
        new ArrayList<>(List.of("Inicio", "Incidencias", "Tablero"));
    /* LFTY JGTM SCGC YARS DMFY */ if (user.role().managesIncidents())
      pages.addAll(List.of("Revisiones", "Auditoría"));
    /* LFTY JGTM SCGC YARS DMFY */ if (user.role() == Role.SUPERADMIN)
      pages.addAll(List.of("Usuarios", "Configuración"));
    /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ String page : pages) {
      /* LFTY JGTM SCGC YARS DMFY */ Button b = button(page, () -> navigate(page));
      b.getStyleClass().add("nav-button");
      b.setMaxWidth(Double.MAX_VALUE);
      navigation.getChildren().add(b);
    }
    /* LFTY JGTM SCGC YARS DMFY */ Region spring = new Region();
    VBox.setVgrow(spring, javafx.scene.layout.Priority.ALWAYS);
    navigation.getChildren().addAll(spring, muted("Voxora 0.2.0"), muted("Reportes manuales"));
    shell.setLeft(navigation);
    /* LFTY JGTM SCGC YARS DMFY */ Label identity =
        label(user.username() + " · " + user.role(), "identity");
    /* LFTY JGTM SCGC YARS DMFY */ Region spacer = new Region();
    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
    /* LFTY JGTM SCGC YARS DMFY */ HBox header =
        new HBox(
            14,
            label("Tu espacio de trabajo", "header-label"),
            spacer,
            identity,
            button("Actualizar", () -> navigate(currentPage)),
            button("Cerrar sesión", () -> logout(false)));
    header.getStyleClass().add("topbar");
    header.setAlignment(Pos.CENTER_LEFT);
    shell.setTop(header);
    /* LFTY JGTM SCGC YARS DMFY */ HBox footer = new HBox(16, feedback, idleWarning);
    footer.getStyleClass().add("footer");
    idleWarning.getStyleClass().add("error");
    shell.setBottom(footer);
    feedback.setText("Sesión iniciada. Todos los cambios se guardan en esta instalación.");
    navigate("Inicio");
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void navigate(/* LFTY JGTM SCGC YARS DMFY */ String page) {
    safe(
        () -> {
          autosave.run();
          autosave = () -> {};
          currentPage = page;
          model.refresh();
          /* LFTY JGTM SCGC YARS DMFY */ switch (page) {
            case "Inicio" -> dashboard();
            case "Incidencias" -> incidentList(false);
            case "Revisiones" -> incidentList(true);
            case "Tablero" -> kanban();
            case "Usuarios" -> users();
            case "Configuración" -> settings();
            case "Auditoría" -> audit();
            default -> dashboard();
          }
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ private VBox page(
      /* LFTY JGTM SCGC YARS DMFY */ String title, /* LFTY JGTM SCGC YARS DMFY */ String subtitle) {
    /* LFTY JGTM SCGC YARS DMFY */ VBox box = new VBox(22, label(title, "title"), muted(subtitle));
    box.getStyleClass().add("page");
    return box;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void show(/* LFTY JGTM SCGC YARS DMFY */ Node node) {
    /* LFTY JGTM SCGC YARS DMFY */ ScrollPane scroll = new ScrollPane(node);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("page-scroll");
    shell.setCenter(scroll);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void dashboard() {
    /* LFTY JGTM SCGC YARS DMFY */ VBox page =
        page("Todo bajo control", "Consulta el estado del trabajo y continúa con tus incidencias.");
    /* LFTY JGTM SCGC YARS DMFY */ HBox metrics = new HBox(12);
    /* LFTY JGTM SCGC YARS DMFY */ for (
    /* LFTY JGTM SCGC YARS DMFY */ Status status : Status.values()) {
      /* LFTY JGTM SCGC YARS DMFY */ long count =
          model.all().stream()
              .filter(/* LFTY JGTM SCGC YARS DMFY */ i -> i.status() == status)
              .count();
      /* LFTY JGTM SCGC YARS DMFY */ VBox card =
          new VBox(10, muted(status.toString()), label(Long.toString(count), "metric"));
      card.getStyleClass().add("card");
      HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);
      card.setMaxWidth(Double.MAX_VALUE);
      metrics.getChildren().add(card);
    }
    page.getChildren().add(metrics);
    /* LFTY JGTM SCGC YARS DMFY */ HBox actions =
        new HBox(
            12,
            button("Ver incidencias", () -> navigate("Incidencias")),
            button("Abrir tablero", () -> navigate("Tablero")));
    /* LFTY JGTM SCGC YARS DMFY */ if (user.role().managesIncidents())
      actions.getChildren().add(primary("Nueva incidencia", this::createIncident));
    page.getChildren().add(actions);
    page.getChildren().add(label("Actividad reciente", "section-title"));
    /* LFTY JGTM SCGC YARS DMFY */ List<Incident> recent =
        model.all().stream()
            .filter(/* LFTY JGTM SCGC YARS DMFY */ i -> i.status() != Status.COMPLETADO)
            .limit(6)
            .toList();
    /* LFTY JGTM SCGC YARS DMFY */ if (recent.isEmpty())
      page.getChildren()
          .add(
              empty(
                  "No hay incidencias abiertas",
                  user.role().managesIncidents()
                      ? "Crea una incidencia y asígnala a un técnico para comenzar."
                      : "Tus tareas aparecerán aquí cuando el supervisor las asigne."));
    /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ Incident i : recent)
      page.getChildren().add(incidentCard(i));
    show(page);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private Node incidentCard(
      /* LFTY JGTM SCGC YARS DMFY */ Incident i) {
    /* LFTY JGTM SCGC YARS DMFY */ VBox text =
        new VBox(
            6,
            label(i.code() + "  ·  " + i.title(), "section-title"),
            muted(i.equipment() + " · " + i.technician()));
    HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);
    /* LFTY JGTM SCGC YARS DMFY */ HBox row =
        new HBox(
            16, text, label(i.status().toString(), "badge"), button("Abrir", () -> detail(i.id())));
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass().add("card");
    return row;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void incidentList(
      /* LFTY JGTM SCGC YARS DMFY */ boolean reviews) {
    /* LFTY JGTM SCGC YARS DMFY */ VBox page =
        page(
            reviews ? "Reportes por revisar" : "Incidencias",
            reviews
                ? "Revisa la versión enviada y aprueba o solicita una corrección."
                : "Busca por código, título, equipo, ubicación o técnico.");
    model.status.set(reviews ? Status.EN_REVISION : null);
    model.query.set("");
    /* LFTY JGTM SCGC YARS DMFY */ TextField search = field("Buscar incidencias…");
    search.textProperty().bindBidirectional(model.query);
    HBox.setHgrow(search, javafx.scene.layout.Priority.ALWAYS);
    /* LFTY JGTM SCGC YARS DMFY */ ComboBox<Status> filter =
        new ComboBox<>(FXCollections.observableArrayList(Status.values()));
    filter.setPromptText("Todos los estados");
    filter.valueProperty().bindBidirectional(model.status);
    filter.setDisable(reviews);
    /* LFTY JGTM SCGC YARS DMFY */ HBox bar =
        new HBox(
            12,
            search,
            filter,
            button(
                "Limpiar",
                () -> {
                  model.query.set("");
                  model.status.set(reviews ? Status.EN_REVISION : null);
                }));
    /* LFTY JGTM SCGC YARS DMFY */ if (!reviews && user.role().managesIncidents())
      bar.getChildren().add(primary("Nueva incidencia", this::createIncident));
    page.getChildren().add(bar);
    /* LFTY JGTM SCGC YARS DMFY */ TableView<Incident> table = new TableView<>(model.visible);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPrefHeight(490);
    table.setPlaceholder(
        muted(
            reviews
                ? "No hay reportes pendientes de revisión."
                : "No hay incidencias que coincidan con la búsqueda."));
    table.getColumns().add(column("Código", Incident::code, 100));
    table.getColumns().add(column("Incidencia", Incident::title, 280));
    table.getColumns().add(column("Equipo", Incident::equipment, 140));
    table
        .getColumns()
        .add(column("Prioridad", /* LFTY JGTM SCGC YARS DMFY */ i -> i.priority().name(), 95));
    table
        .getColumns()
        .add(column("Estado", /* LFTY JGTM SCGC YARS DMFY */ i -> i.status().toString(), 130));
    table.getColumns().add(column("Técnico", Incident::technician, 140));
    /* LFTY JGTM SCGC YARS DMFY */ Runnable open =
        () -> {
          /* LFTY JGTM SCGC YARS DMFY */ Incident i = table.getSelectionModel().getSelectedItem();
          /* LFTY JGTM SCGC YARS DMFY */ if (i != null) detail(i.id());
        };
    table.setOnMouseClicked(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          /* LFTY JGTM SCGC YARS DMFY */ if (e.getClickCount() == 2) open.run();
        });
    table.setOnKeyPressed(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          /* LFTY JGTM SCGC YARS DMFY */ if (e.getCode() == KeyCode.ENTER) open.run();
        });
    page.getChildren().addAll(table, button("Abrir seleccionada", open));
    show(page);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void kanban() {
    /* LFTY JGTM SCGC YARS DMFY */ VBox page =
        page(
            "Tablero de trabajo",
            "Cada tarjeta representa una incidencia. Ábrela para realizar la siguiente acción.");
    /* LFTY JGTM SCGC YARS DMFY */ HBox columns = new HBox(14);
    /* LFTY JGTM SCGC YARS DMFY */ for (
    /* LFTY JGTM SCGC YARS DMFY */ Status status : Status.values()) {
      /* LFTY JGTM SCGC YARS DMFY */ List<Incident> incidents =
          model.all().stream()
              .filter(/* LFTY JGTM SCGC YARS DMFY */ i -> i.status() == status)
              .toList();
      /* LFTY JGTM SCGC YARS DMFY */ VBox column =
          new VBox(12, label(status + "  ·  " + incidents.size(), "section-title"));
      column.setPrefWidth(215);
      column.setMinWidth(215);
      column.getStyleClass().add("kanban-column");
      /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ Incident i : incidents) {
        /* LFTY JGTM SCGC YARS DMFY */ Button card =
            new Button(
                i.code()
                    + "  ·  "
                    + i.priority()
                    + "\n\n"
                    + i.title()
                    + "\n\n"
                    + i.equipment()
                    + "\n"
                    + i.technician());
        card.setWrapText(true);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("kanban-card");
        card.setOnAction(/* LFTY JGTM SCGC YARS DMFY */ e -> detail(i.id()));
        column.getChildren().add(card);
      }
      /* LFTY JGTM SCGC YARS DMFY */ if (incidents.isEmpty())
        column.getChildren().add(muted("Sin incidencias"));
      columns.getChildren().add(column);
    }
    page.getChildren().add(columns);
    show(page);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void createIncident() {
    /* LFTY JGTM SCGC YARS DMFY */ Dialog<ButtonType> dialog = dialog("Nueva incidencia", 760, 650);
    /* LFTY JGTM SCGC YARS DMFY */ TextField title = field("Actividad solicitada"),
        equipment = field("Código o nombre del equipo"),
        location = field("Área o ubicación");
    /* LFTY JGTM SCGC YARS DMFY */ TextArea description =
        area("Describe el problema o la necesidad", 6);
    /* LFTY JGTM SCGC YARS DMFY */ ComboBox<dev.voxora.domain.Model.Priority> priority =
        combo(dev.voxora.domain.Model.Priority.values(), dev.voxora.domain.Model.Priority.MEDIA);
    /* LFTY JGTM SCGC YARS DMFY */ ComboBox<Specialty> specialty =
        combo(Specialty.values(), Specialty.GENERAL);
    /* LFTY JGTM SCGC YARS DMFY */ VBox form =
        new VBox(
            14,
            labeled("Título", title),
            labeled("Equipo", equipment),
            labeled("Ubicación", location),
            labeled("Prioridad", priority),
            labeled("Especialidad", specialty),
            labeled("Descripción", description));
    dialog.getDialogPane().setContent(scroll(form));
    /* LFTY JGTM SCGC YARS DMFY */ ButtonType create =
        new ButtonType("Crear incidencia", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);
    /* LFTY JGTM SCGC YARS DMFY */ Button b = (Button) dialog.getDialogPane().lookupButton(create);
    b.addEventFilter(
        javafx.event.ActionEvent.ACTION,
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          e.consume();
          safe(
              () -> {
                /* LFTY JGTM SCGC YARS DMFY */ long id =
                    context.incidents.create(
                        token,
                        title.getText(),
                        equipment.getText(),
                        location.getText(),
                        description.getText(),
                        priority.getValue(),
                        specialty.getValue());
                dialog.close();
                navigate(currentPage);
                detail(id);
              });
        });
    dialog.showAndWait();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void detail(/* LFTY JGTM SCGC YARS DMFY */ long id) {
    safe(() -> showDetail(id));
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void showDetail(/* LFTY JGTM SCGC YARS DMFY */ long id) {
    /* LFTY JGTM SCGC YARS DMFY */ Incident i = context.incidents.read(token, id);
    /* LFTY JGTM SCGC YARS DMFY */ boolean editable =
        user.role() == Role.TECHNICIAN
            && Objects.equals(i.assignedTo(), user.id())
            && i.status() == Status.EN_PROCESO;
    /* LFTY JGTM SCGC YARS DMFY */ Dialog<ButtonType> dialog =
        dialog(i.code() + " · " + i.title(), 960, 780);
    /* LFTY JGTM SCGC YARS DMFY */ VBox content = new VBox(16);
    content
        .getChildren()
        .addAll(
            label(i.title(), "title"),
            label(i.status() + " · " + i.priority() + " · " + i.specialty(), "badge"),
            muted(
                i.code()
                    + " · "
                    + i.equipment()
                    + " · "
                    + i.location()
                    + " · Responsable: "
                    + i.technician()),
            labeled("Solicitud", readArea(i.description(), 3)));
    /* LFTY JGTM SCGC YARS DMFY */ if (!i.observation().isBlank())
      content
          .getChildren()
          .add(labeled("Observación del supervisor", readArea(i.observation(), 3)));
    /* LFTY JGTM SCGC YARS DMFY */ HBox actions = new HBox(12);
    /* LFTY JGTM SCGC YARS DMFY */ if (user.role().managesIncidents()
        && (i.status() == Status.PENDIENTE || i.status() == Status.ASIGNADO)) {
      /* LFTY JGTM SCGC YARS DMFY */ ComboBox<User> technicians =
          new ComboBox<>(
              FXCollections.observableArrayList(
                  context.identity.users(token).stream()
                      .filter(
                          /* LFTY JGTM SCGC YARS DMFY */ u ->
                              u.active()
                                  && u.role() == Role.TECHNICIAN
                                  && u.specialty() == i.specialty())
                      .toList()));
      technicians.setPromptText("Selecciona un técnico compatible");
      technicians.setMaxWidth(400);
      actions
          .getChildren()
          .addAll(
              technicians,
              primary(
                  "Asignar",
                  () ->
                      safe(
                          () -> {
                            /* LFTY JGTM SCGC YARS DMFY */ if (technicians.getValue() == null)
                              throw new RuleViolation("Selecciona un técnico.");
                            context.incidents.assign(token, id, technicians.getValue().id());
                            dialog.close();
                            navigate(currentPage);
                            detail(id);
                          })));
    }
    /* LFTY JGTM SCGC YARS DMFY */ if (user.role() == Role.TECHNICIAN
        && i.status() == Status.ASIGNADO)
      actions
          .getChildren()
          .add(
              primary(
                  "Iniciar atención",
                  () ->
                      safe(
                          () -> {
                            context.incidents.start(token, id);
                            dialog.close();
                            navigate(currentPage);
                            detail(id);
                          })));
    /* LFTY JGTM SCGC YARS DMFY */ TextArea draft =
        area("Describe las actividades, pruebas y resultados…", 10);
    draft.setText(i.draft());
    draft.setEditable(editable);
    /* LFTY JGTM SCGC YARS DMFY */ if (i.startedAt() != null)
      content
          .getChildren()
          .addAll(label(editable ? "Reporte de atención" : "Reporte", "section-title"), draft);
    /* LFTY JGTM SCGC YARS DMFY */ AtomicReference<String> saved = new AtomicReference<>(i.draft());
    /* LFTY JGTM SCGC YARS DMFY */ Label savedLabel =
        muted(editable ? "Guardado automático cada 5 segundos." : "Versión de solo lectura.");
    /* LFTY JGTM SCGC YARS DMFY */ Runnable save =
        () -> {
          /* LFTY JGTM SCGC YARS DMFY */ if (editable
              && token != null
              && !saved.get().equals(draft.getText())) {
            /* LFTY JGTM SCGC YARS DMFY */ Version v =
                context.incidents.save(token, id, draft.getText());
            saved.set(draft.getText());
            savedLabel.setText("Borrador guardado · Versión " + v.number());
          }
        };
    /* LFTY JGTM SCGC YARS DMFY */ if (editable) {
      autosave = save;
      content.getChildren().add(savedLabel);
      actions
          .getChildren()
          .add(
              button(
                  "Voz y narración",
                  () -> {
                    save.run();
                    new VoiceDialog(
                            context,
                            token,
                            id,
                            dialog.getDialogPane().getScene().getWindow(),
                            /* LFTY JGTM SCGC YARS DMFY */ v -> {
                              draft.setText(v.content());
                              saved.set(v.content());
                              savedLabel.setText(
                                  "Propuesta guardada · Versión "
                                      + v.number()
                                      + " · revisa antes de enviar");
                            })
                        .show();
                  }));
      actions
          .getChildren()
          .addAll(
              button(
                  "Guardar borrador",
                  () ->
                      safe(
                          () -> {
                            /* LFTY JGTM SCGC YARS DMFY */ Version v =
                                context.incidents.save(token, id, draft.getText());
                            saved.set(draft.getText());
                            savedLabel.setText("Guardado · Versión " + v.number());
                          })),
              primary(
                  "Vista previa y envío",
                  () ->
                      safe(
                          () -> {
                            /* LFTY JGTM SCGC YARS DMFY */ Version v =
                                context.incidents.preview(token, id, draft.getText());
                            saved.set(draft.getText());
                            /* LFTY JGTM SCGC YARS DMFY */ Dialog<ButtonType> preview =
                                dialog(
                                    "Vista previa · " + i.code() + " · Versión " + v.number(),
                                    800,
                                    620);
                            preview
                                .getDialogPane()
                                .setContent(
                                    scroll(
                                        new VBox(
                                            14,
                                            muted(
                                                "Comprueba el contenido. Solo la confirmación lo"
                                                    + " envía al supervisor."),
                                            readArea(v.content(), 20))));
                            /* LFTY JGTM SCGC YARS DMFY */ ButtonType confirm =
                                new ButtonType(
                                    "Confirmar envío a supervisión", ButtonBar.ButtonData.OK_DONE);
                            preview
                                .getDialogPane()
                                .getButtonTypes()
                                .addAll(
                                    confirm,
                                    new ButtonType(
                                        "Volver y corregir", ButtonBar.ButtonData.CANCEL_CLOSE));
                            /* LFTY JGTM SCGC YARS DMFY */ if (preview
                                    .showAndWait()
                                    .orElse(ButtonType.CANCEL)
                                == confirm) {
                              context.incidents.submit(token, id, v.id());
                              autosave = () -> {};
                              dialog.close();
                              navigate(currentPage);
                              detail(id);
                            }
                          })));
    }
    /* LFTY JGTM SCGC YARS DMFY */ if (user.role().managesIncidents()
        && i.status() == Status.EN_REVISION) {
      actions
          .getChildren()
          .addAll(
              button(
                  "Solicitar corrección",
                  () -> {
                    /* LFTY JGTM SCGC YARS DMFY */ TextInputDialog reason = new TextInputDialog();
                    reason.initOwner(stage);
                    reason.setTitle("Devolver reporte");
                    reason.setHeaderText("Indica qué debe corregir el técnico");
                    reason
                        .showAndWait()
                        .ifPresent(
                            /* LFTY JGTM SCGC YARS DMFY */ text ->
                                safe(
                                    () -> {
                                      context.incidents.returnReport(token, id, text);
                                      dialog.close();
                                      navigate(currentPage);
                                      detail(id);
                                    }));
                  }),
              primary(
                  "Aprobar y generar PDF",
                  () -> {
                    /* LFTY JGTM SCGC YARS DMFY */ if (confirm(
                        "Aprobar reporte",
                        "Se aprobará la versión enviada y se completará la incidencia."))
                      async(
                          () -> context.incidents.approve(token, id),
                          /* LFTY JGTM SCGC YARS DMFY */ path -> {
                            dialog.close();
                            navigate(currentPage);
                            notice(
                                "Reporte aprobado",
                                "El PDF quedó guardado. Puedes abrirlo desde la incidencia.");
                          });
                  }));
    }
    /* LFTY JGTM SCGC YARS DMFY */ if (i.status() == Status.COMPLETADO)
      actions
          .getChildren()
          .addAll(
              primary(
                  "Abrir PDF",
                  () -> async(() -> context.incidents.export(token, id, false), this::openFile)),
              button(
                  "Abrir DOCX",
                  () -> async(() -> context.incidents.export(token, id, true), this::openFile)));
    /* LFTY JGTM SCGC YARS DMFY */ FlowPane flow = new FlowPane(12, 12);
    flow.getChildren().addAll(new ArrayList<>(actions.getChildren()));
    content.getChildren().add(flow);
    /* LFTY JGTM SCGC YARS DMFY */ if (i.startedAt() != null)
      content.getChildren().add(button("Consultar versiones", () -> versions(id)));
    content.getChildren().add(label("Historial de la incidencia", "section-title"));
    /* LFTY JGTM SCGC YARS DMFY */ for (
    /* LFTY JGTM SCGC YARS DMFY */ Event event : context.incidents.events(token, id))
      content.getChildren().add(eventNode(event));
    dialog.getDialogPane().setContent(scroll(content));
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    dialog.setOnCloseRequest(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          try {
            save.run();
            autosave = () -> {};
          } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception failure) {
            e.consume();
            error(failure);
          }
        });
    // Dialogs have their own scenes; their input is human activity too.
    dialog.showAndWait();
    autosave = () -> {};
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void versions(/* LFTY JGTM SCGC YARS DMFY */ long id) {
    /* LFTY JGTM SCGC YARS DMFY */ Dialog<ButtonType> dialog =
        dialog("Versiones del reporte", 850, 650);
    /* LFTY JGTM SCGC YARS DMFY */ ListView<Version> list =
        new ListView<>(FXCollections.observableArrayList(context.incidents.versions(token, id)));
    list.setPrefHeight(180);
    list.setCellFactory(
        /* LFTY JGTM SCGC YARS DMFY */ v ->
            new ListCell<>() /* LFTY JGTM SCGC YARS DMFY */ {
              /* LFTY JGTM SCGC YARS DMFY */ @Override
              protected void updateItem(
                  /* LFTY JGTM SCGC YARS DMFY */ Version item, /* LFTY JGTM SCGC YARS DMFY */
                  boolean empty) {
                super.updateItem(item, empty);
                setText(
                    empty || item == null
                        ? null
                        : "Versión "
                            + item.number()
                            + " · "
                            + item.kind()
                            + " · "
                            + item.author()
                            + " · "
                            + dates.format(item.createdAt()));
              }
            });
    /* LFTY JGTM SCGC YARS DMFY */ TextArea text =
        readArea("Selecciona una versión para consultar su contenido.", 14);
    list.getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (/* LFTY JGTM SCGC YARS DMFY */ o, /* LFTY JGTM SCGC YARS DMFY */
                a, /* LFTY JGTM SCGC YARS DMFY */
                b) -> {
              /* LFTY JGTM SCGC YARS DMFY */ if (b != null) text.setText(b.content());
            });
    dialog.getDialogPane().setContent(new VBox(14, list, text));
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    dialog.showAndWait();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void users() {
    /* LFTY JGTM SCGC YARS DMFY */ VBox page =
        page(
            "Usuarios de la instalación",
            "Crea cuentas, asigna perfiles y desactiva accesos conservando el historial.");
    /* LFTY JGTM SCGC YARS DMFY */ TableView<User> table =
        new TableView<>(FXCollections.observableArrayList(context.identity.users(token)));
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPrefHeight(420);
    table.getColumns().add(column("Usuario", User::username, 220));
    table
        .getColumns()
        .add(column("Perfil", /* LFTY JGTM SCGC YARS DMFY */ u -> u.role().toString(), 240));
    table
        .getColumns()
        .add(
            column(
                "Especialidad", /* LFTY JGTM SCGC YARS DMFY */ u -> u.specialty().toString(), 200));
    table
        .getColumns()
        .add(
            column(
                "Acceso", /* LFTY JGTM SCGC YARS DMFY */
                u -> u.active() ? "Activo" : "Desactivado",
                140));
    page.getChildren()
        .addAll(
            new HBox(
                12,
                primary("Crear usuario", () -> userForm(null)),
                button(
                    "Editar seleccionado",
                    () -> {
                      /* LFTY JGTM SCGC YARS DMFY */ User selected =
                          table.getSelectionModel().getSelectedItem();
                      /* LFTY JGTM SCGC YARS DMFY */ if (selected != null) userForm(selected);
                    })),
            table,
            muted(
                "Si un técnico tiene incidencias abiertas, primero reasígnalas o finalízalas antes"
                    + " de desactivarlo."));
    show(page);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void userForm(/* LFTY JGTM SCGC YARS DMFY */ User target) {
    /* LFTY JGTM SCGC YARS DMFY */ Dialog<ButtonType> dialog =
        dialog(target == null ? "Crear usuario" : "Editar usuario", 600, 620);
    /* LFTY JGTM SCGC YARS DMFY */ TextField name = field("Nombre de usuario");
    /* LFTY JGTM SCGC YARS DMFY */ if (target != null) {
      name.setText(target.username());
      name.setEditable(false);
    }
    /* LFTY JGTM SCGC YARS DMFY */ ComboBox<Role> role =
        combo(Role.values(), target == null ? Role.TECHNICIAN : target.role());
    /* LFTY JGTM SCGC YARS DMFY */ ComboBox<Specialty> specialty =
        combo(Specialty.values(), target == null ? Specialty.GENERAL : target.specialty());
    /* LFTY JGTM SCGC YARS DMFY */ CheckBox active = new CheckBox("Cuenta activa");
    active.setSelected(target == null || target.active());
    /* LFTY JGTM SCGC YARS DMFY */ PasswordField password = new PasswordField();
    password.setPromptText("Mínimo 10 caracteres");
    /* LFTY JGTM SCGC YARS DMFY */ VBox form =
        new VBox(
            14,
            labeled("Usuario", name),
            labeled("Perfil", role),
            labeled("Especialidad", specialty),
            active);
    /* LFTY JGTM SCGC YARS DMFY */ if (target == null)
      form.getChildren().add(labeled("Contraseña inicial", password));
    else form.getChildren().add(button("Restablecer contraseña…", () -> resetPassword(target)));
    dialog.getDialogPane().setContent(form);
    /* LFTY JGTM SCGC YARS DMFY */ ButtonType save =
        new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
    dialog
        .getDialogPane()
        .lookupButton(save)
        .addEventFilter(
            javafx.event.ActionEvent.ACTION,
            /* LFTY JGTM SCGC YARS DMFY */ e -> {
              e.consume();
              /* LFTY JGTM SCGC YARS DMFY */ char[] secret = password.getText().toCharArray();
              /* LFTY JGTM SCGC YARS DMFY */ String username = name.getText();
              /* LFTY JGTM SCGC YARS DMFY */ Role selectedRole = role.getValue();
              /* LFTY JGTM SCGC YARS DMFY */ Specialty selectedSpecialty = specialty.getValue();
              /* LFTY JGTM SCGC YARS DMFY */ boolean enabled = active.isSelected();
              async(
                  () -> {
                    try {
                      /* LFTY JGTM SCGC YARS DMFY */ if (target == null)
                        context.identity.createUser(
                            token, username, secret, selectedRole, selectedSpecialty);
                      else
                        context.identity.updateUser(
                            token, target.id(), selectedRole, selectedSpecialty, enabled);
                      return true;
                    } finally {
                      Arrays.fill(secret, '\0');
                    }
                  },
                  /* LFTY JGTM SCGC YARS DMFY */ ok -> {
                    password.clear();
                    dialog.close();
                    navigate("Usuarios");
                  });
            });
    dialog.showAndWait();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void resetPassword(
      /* LFTY JGTM SCGC YARS DMFY */ User target) {
    /* LFTY JGTM SCGC YARS DMFY */ Dialog<ButtonType> dialog =
        dialog("Restablecer contraseña · " + target.username(), 480, 300);
    /* LFTY JGTM SCGC YARS DMFY */ PasswordField field = new PasswordField();
    field.setPromptText("Nueva contraseña, mínimo 10 caracteres");
    dialog.getDialogPane().setContent(field);
    /* LFTY JGTM SCGC YARS DMFY */ ButtonType save =
        new ButtonType("Restablecer", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
    dialog
        .getDialogPane()
        .lookupButton(save)
        .addEventFilter(
            javafx.event.ActionEvent.ACTION,
            /* LFTY JGTM SCGC YARS DMFY */ e -> {
              e.consume();
              /* LFTY JGTM SCGC YARS DMFY */ char[] secret = field.getText().toCharArray();
              field.clear();
              async(
                  () -> {
                    try {
                      context.identity.resetPassword(token, target.id(), secret);
                      return true;
                    } finally {
                      Arrays.fill(secret, '\0');
                    }
                  },
                  /* LFTY JGTM SCGC YARS DMFY */ ok -> {
                    dialog.close();
                    /* LFTY JGTM SCGC YARS DMFY */ if (target.id() == user.id()) logout(false);
                    else
                      notice(
                          "Contraseña restablecida", "Las sesiones de esa cuenta fueron cerradas.");
                  });
            });
    dialog.showAndWait();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void settings() {
    /* LFTY JGTM SCGC YARS DMFY */ VBox page =
        page(
            "Configuración",
            "Administra la sesión y conserva copias de la información de esta instalación.");
    /* LFTY JGTM SCGC YARS DMFY */ Spinner<Integer> minutes =
        new Spinner<>(5, 30, context.identity.inactivityMinutes(token));
    /* LFTY JGTM SCGC YARS DMFY */ VBox session =
        new VBox(
            14,
            label("Bloqueo por inactividad", "section-title"),
            muted("Se mostrará un aviso un minuto antes. El nuevo acceso requiere contraseña."),
            labeled("Minutos sin actividad", minutes),
            button(
                "Guardar intervalo",
                () ->
                    safe(
                        () -> {
                          context.identity.inactivityMinutes(token, minutes.getValue());
                          inactivityMinutes = minutes.getValue();
                          feedback.setText("Intervalo actualizado.");
                        })));
    session.getStyleClass().add("card");
    /* LFTY JGTM SCGC YARS DMFY */ VBox backup =
        new VBox(
            14,
            label("Respaldos y restauración", "section-title"),
            muted(
                "Las copias incluyen la base de datos y los documentos. Guárdalas en una ubicación"
                    + " protegida."),
            new HBox(
                12,
                primary(
                    "Crear respaldo…",
                    () -> {
                      /* LFTY JGTM SCGC YARS DMFY */ FileChooser chooser =
                          zipChooser("Guardar respaldo");
                      chooser.setInitialFileName(
                          "Voxora-"
                              + java.time.LocalDateTime.now()
                                  .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                              + ".zip");
                      /* LFTY JGTM SCGC YARS DMFY */ java.io.File file =
                          chooser.showSaveDialog(stage);
                      /* LFTY JGTM SCGC YARS DMFY */ if (file != null)
                        async(
                            () -> {
                              context.backups.backup(token, file.toPath());
                              return true;
                            },
                            /* LFTY JGTM SCGC YARS DMFY */ ok ->
                                notice(
                                    "Respaldo creado",
                                    "La copia quedó guardada en la ubicación elegida."));
                    }),
                button(
                    "Restaurar respaldo…",
                    () -> {
                      /* LFTY JGTM SCGC YARS DMFY */ java.io.File file =
                          zipChooser("Seleccionar respaldo").showOpenDialog(stage);
                      /* LFTY JGTM SCGC YARS DMFY */ if (file != null
                          && confirm(
                              "Restaurar instalación",
                              "Se reemplazarán los datos y documentos actuales por los del"
                                  + " respaldo. Se cerrarán todas las sesiones."))
                        async(
                            () -> {
                              context.backups.restore(token, file.toPath());
                              context.incidents.reconcile();
                              return true;
                            },
                            /* LFTY JGTM SCGC YARS DMFY */ ok -> {
                              logout(false);
                              notice(
                                  "Restauración completada",
                                  "Inicia sesión con una cuenta del respaldo restaurado.");
                            });
                    })));
    backup.getStyleClass().add("card");
    /* LFTY JGTM SCGC YARS DMFY */ Label aiState =
        muted(
            context.voice.runtime().available()
                ? "Componentes locales encontrados: Whisper Tiny y Qwen3 4B."
                : "Componentes no preparados. Ejecuta scripts/prepare-ai.ps1 o selecciona una"
                    + " carpeta preparada.");
    /* LFTY JGTM SCGC YARS DMFY */ VBox ai =
        new VBox(
            14,
            label("Voz e inteligencia local", "section-title"),
            aiState,
            muted(context.voice.runtime().home().toString()),
            button(
                "Seleccionar carpeta de motores y modelos…",
                () -> {
                  /* LFTY JGTM SCGC YARS DMFY */ DirectoryChooser chooser = new DirectoryChooser();
                  chooser.setTitle("Componentes locales de Voxora");
                  /* LFTY JGTM SCGC YARS DMFY */ var directory = chooser.showDialog(stage);
                  /* LFTY JGTM SCGC YARS DMFY */ if (directory != null)
                    async(
                        () -> {
                          context.voice.configure(token, directory.toPath());
                          return true;
                        },
                        /* LFTY JGTM SCGC YARS DMFY */ ok -> navigate("Configuración"));
                }));
    ai.getStyleClass().add("card");
    page.getChildren()
        .addAll(
            session,
            ai,
            backup,
            labeled("Datos de esta instalación", muted(context.repository.root().toString())));
    show(page);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void audit() {
    /* LFTY JGTM SCGC YARS DMFY */ VBox page =
        page("Auditoría", "Acciones registradas con responsable y fecha.");
    /* LFTY JGTM SCGC YARS DMFY */ for (
    /* LFTY JGTM SCGC YARS DMFY */ Event event : context.incidents.events(token, null))
      page.getChildren().add(eventNode(event));
    show(page);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private Node eventNode(
      /* LFTY JGTM SCGC YARS DMFY */ Event event) {
    /* LFTY JGTM SCGC YARS DMFY */ Label detail = muted(event.detail());
    detail.setWrapText(true);
    /* LFTY JGTM SCGC YARS DMFY */ VBox row =
        new VBox(
            6,
            label(event.action().replace('_', ' ') + " · " + event.actor(), "event-title"),
            detail,
            muted(dates.format(event.at())));
    row.getStyleClass().add("event");
    return row;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void logout(
      /* LFTY JGTM SCGC YARS DMFY */ boolean expired) {
    /* LFTY JGTM SCGC YARS DMFY */ if (token == null) return;
    /* LFTY JGTM SCGC YARS DMFY */ String previous = token;
    try {
      autosave.run();
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      feedback.setText("No se pudo guardar el último cambio: " + e.getMessage());
    }
    autosave = () -> {};
    token = null;
    user = null;
    /* LFTY JGTM SCGC YARS DMFY */ for (
    /* LFTY JGTM SCGC YARS DMFY */ Window window :
        List.copyOf(Window.getWindows())) /* LFTY JGTM SCGC YARS DMFY */
      if (window != stage) window.hide();
    context.identity.logout(previous, expired);
    idleWarning.setText("");
    login();
    /* LFTY JGTM SCGC YARS DMFY */ if (expired)
      notice(
          "Sesión bloqueada",
          "Inicia sesión para continuar. Los borradores guardados permanecen disponibles.");
  }

  /* LFTY JGTM SCGC YARS DMFY */ private <T> void async(
      /* LFTY JGTM SCGC YARS DMFY */ Callable<T> action, /* LFTY JGTM SCGC YARS DMFY */
      Consumer<T> success) {
    async(action, success, this::error);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private <T> void async(
      /* LFTY JGTM SCGC YARS DMFY */ Callable<T> action, /* LFTY JGTM SCGC YARS DMFY */
      Consumer<T> success, /* LFTY JGTM SCGC YARS DMFY */
      Consumer<Throwable> failure) {
    /* LFTY JGTM SCGC YARS DMFY */ if (busy) return;
    busy = true;
    /* LFTY JGTM SCGC YARS DMFY */ String session = token;
    /* LFTY JGTM SCGC YARS DMFY */ List<Window> windows = List.copyOf(Window.getWindows());
    /* LFTY JGTM SCGC YARS DMFY */ for (
    /* LFTY JGTM SCGC YARS DMFY */ Window w : windows) /* LFTY JGTM SCGC YARS DMFY */
      if (w.getScene() != null) w.getScene().getRoot().setDisable(true);
    feedback.setText("Procesando…");
    /* LFTY JGTM SCGC YARS DMFY */ Task<T> task =
        new Task<>() /* LFTY JGTM SCGC YARS DMFY */ {
          /* LFTY JGTM SCGC YARS DMFY */ @Override
          protected T call() throws Exception {
            return action.call();
          }
        };
    /* LFTY JGTM SCGC YARS DMFY */ Runnable enable =
        () -> {
          busy = false;
          /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ Window w : windows)
            /* LFTY JGTM SCGC YARS DMFY */ if (w.getScene() != null)
              w.getScene().getRoot().setDisable(false);
          feedback.setText("Listo.");
        };
    task.setOnSucceeded(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          enable.run();
          /* LFTY JGTM SCGC YARS DMFY */ if (Objects.equals(session, token))
            safe(() -> success.accept(task.getValue()));
        });
    task.setOnFailed(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          enable.run();
          failure.accept(task.getException());
        });
    worker.submit(task);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void safe(/* LFTY JGTM SCGC YARS DMFY */ Runnable action) {
    try {
      action.run();
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      error(e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void error(/* LFTY JGTM SCGC YARS DMFY */ Throwable e) {
    /* LFTY JGTM SCGC YARS DMFY */ Alert alert = new Alert(Alert.AlertType.ERROR);
    /* LFTY JGTM SCGC YARS DMFY */ if (stage != null && stage.isShowing()) alert.initOwner(stage);
    alert.setTitle("Voxora");
    alert.setHeaderText("No se pudo completar la acción");
    alert.setContentText(
        e.getMessage() == null ? "Revisa los datos e intenta nuevamente." : e.getMessage());
    alert.showAndWait();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void notice(
      /* LFTY JGTM SCGC YARS DMFY */ String title, /* LFTY JGTM SCGC YARS DMFY */ String message) {
    /* LFTY JGTM SCGC YARS DMFY */ Alert a =
        new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
    a.initOwner(stage);
    a.setTitle("Voxora");
    a.setHeaderText(title);
    a.showAndWait();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private boolean confirm(
      /* LFTY JGTM SCGC YARS DMFY */ String title, /* LFTY JGTM SCGC YARS DMFY */ String message) {
    /* LFTY JGTM SCGC YARS DMFY */ Alert a =
        new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
    a.initOwner(stage);
    a.setTitle("Voxora");
    a.setHeaderText(title);
    return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private Dialog<ButtonType> dialog(
      /* LFTY JGTM SCGC YARS DMFY */ String title, /* LFTY JGTM SCGC YARS DMFY */
      double width, /* LFTY JGTM SCGC YARS DMFY */
      double height) {
    /* LFTY JGTM SCGC YARS DMFY */ Dialog<ButtonType> dialog = new Dialog<>();
    dialog.initOwner(stage);
    dialog.setTitle(title);
    dialog.setResizable(true);
    dialog.getDialogPane().setPrefSize(width, height);
    dialog.getDialogPane().getStylesheets().add(stage.getScene().getStylesheets().getFirst());
    dialog.setOnShown(
        /* LFTY JGTM SCGC YARS DMFY */ e -> {
          /* LFTY JGTM SCGC YARS DMFY */ Scene scene = dialog.getDialogPane().getScene();
          scene.addEventFilter(
              KeyEvent.KEY_PRESSED, /* LFTY JGTM SCGC YARS DMFY */ k -> activity());
          scene.addEventFilter(
              MouseEvent.MOUSE_PRESSED, /* LFTY JGTM SCGC YARS DMFY */ k -> activity());
          scene.addEventFilter(ScrollEvent.SCROLL, /* LFTY JGTM SCGC YARS DMFY */ k -> activity());
        });
    return dialog;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static FileChooser zipChooser(
      /* LFTY JGTM SCGC YARS DMFY */ String title) {
    /* LFTY JGTM SCGC YARS DMFY */ FileChooser c = new FileChooser();
    c.setTitle(title);
    c.getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Respaldo Voxora (*.zip)", "*.zip"));
    return c;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void openFile(/* LFTY JGTM SCGC YARS DMFY */ Path file) {
    getHostServices().showDocument(file.toUri().toString());
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static Label label(
      /* LFTY JGTM SCGC YARS DMFY */ String text, /* LFTY JGTM SCGC YARS DMFY */ String style) {
    /* LFTY JGTM SCGC YARS DMFY */ Label label = new Label(text);
    label.getStyleClass().add(style);
    label.setWrapText(true);
    return label;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static Label muted(
      /* LFTY JGTM SCGC YARS DMFY */ String text) {
    return label(text, "muted");
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static TextField field(
      /* LFTY JGTM SCGC YARS DMFY */ String prompt) {
    /* LFTY JGTM SCGC YARS DMFY */ TextField f = new TextField();
    f.setPromptText(prompt);
    return f;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static TextArea area(
      /* LFTY JGTM SCGC YARS DMFY */ String prompt, /* LFTY JGTM SCGC YARS DMFY */ int rows) {
    /* LFTY JGTM SCGC YARS DMFY */ TextArea t = new TextArea();
    t.setPromptText(prompt);
    t.setPrefRowCount(rows);
    t.setWrapText(true);
    return t;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static TextArea readArea(
      /* LFTY JGTM SCGC YARS DMFY */ String text, /* LFTY JGTM SCGC YARS DMFY */ int rows) {
    /* LFTY JGTM SCGC YARS DMFY */ TextArea a = area("", rows);
    a.setText(text);
    a.setEditable(false);
    return a;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static VBox labeled(
      /* LFTY JGTM SCGC YARS DMFY */ String title, /* LFTY JGTM SCGC YARS DMFY */ Node input) {
    return new VBox(6, label(title, "field-label"), input);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private Button button(
      /* LFTY JGTM SCGC YARS DMFY */ String text, /* LFTY JGTM SCGC YARS DMFY */ Runnable action) {
    /* LFTY JGTM SCGC YARS DMFY */ Button b = new Button(text);
    b.setOnAction(/* LFTY JGTM SCGC YARS DMFY */ e -> safe(action));
    return b;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private Button primary(
      /* LFTY JGTM SCGC YARS DMFY */ String text, /* LFTY JGTM SCGC YARS DMFY */ Runnable action) {
    /* LFTY JGTM SCGC YARS DMFY */ Button b = button(text, action);
    b.getStyleClass().add("primary");
    return b;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static <T> ComboBox<T> combo(
      /* LFTY JGTM SCGC YARS DMFY */ T[] values, /* LFTY JGTM SCGC YARS DMFY */ T selected) {
    /* LFTY JGTM SCGC YARS DMFY */ ComboBox<T> c =
        new ComboBox<>(FXCollections.observableArrayList(values));
    c.setValue(selected);
    c.setMaxWidth(Double.MAX_VALUE);
    return c;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static ScrollPane scroll(
      /* LFTY JGTM SCGC YARS DMFY */ Node content) {
    /* LFTY JGTM SCGC YARS DMFY */ ScrollPane s = new ScrollPane(content);
    s.setFitToWidth(true);
    s.setPadding(new Insets(8));
    return s;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static Node empty(
      /* LFTY JGTM SCGC YARS DMFY */ String title, /* LFTY JGTM SCGC YARS DMFY */ String subtitle) {
    /* LFTY JGTM SCGC YARS DMFY */ VBox box =
        new VBox(10, label(title, "section-title"), muted(subtitle));
    box.getStyleClass().add("card");
    return box;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static <T> TableColumn<T, String> column(
      /* LFTY JGTM SCGC YARS DMFY */ String title, /* LFTY JGTM SCGC YARS DMFY */
      Function<T, String> value, /* LFTY JGTM SCGC YARS DMFY */
      double width) {
    /* LFTY JGTM SCGC YARS DMFY */ TableColumn<T, String> c = new TableColumn<>(title);
    c.setCellValueFactory(
        /* LFTY JGTM SCGC YARS DMFY */ row ->
            new ReadOnlyStringWrapper(value.apply(row.getValue())));
    c.setPrefWidth(width);
    return c;
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public void stop() throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ if (timer != null) timer.stop();
    worker.shutdown();
    /* LFTY JGTM SCGC YARS DMFY */ if (!worker.awaitTermination(5, TimeUnit.SECONDS))
      worker.shutdownNow();
    /* LFTY JGTM SCGC YARS DMFY */ if (context != null) context.close();
  }
}
