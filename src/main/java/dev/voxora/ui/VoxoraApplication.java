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

/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class VoxoraApplication extends Application {
  private AppContext context;
  private Stage stage;
  private BorderPane shell;
  private String token;
  private User user;
  private WorkspaceViewModel model;
  private final Label feedback = new Label();
  private final Label idleWarning = new Label();
  private final ExecutorService worker =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "voxora-io");
            t.setDaemon(true);
            return t;
          });
  private Timeline timer;
  private Runnable autosave = () -> {};
  private String currentPage = "Inicio";
  private final Map<String, Button> navigationButtons = new LinkedHashMap<>();
  private final Label breadcrumb = new Label();
  private boolean busy;
  private int inactivityMinutes = 15;
  private final boolean failFast;
  private final DateTimeFormatter dates =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public VoxoraApplication() {
    failFast = false;
  }

  VoxoraApplication(AppContext context) {
    this.context = context;
    failFast = true;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  @Override
  public void start(Stage stage) {
    this.stage = stage;
    try {
      if (context == null) {
        String path = getParameters().getNamed().get("data");
        context = new AppContext(path == null ? AppContext.defaultRoot() : Path.of(path));
      }
      stage.setTitle("Voxora · Gestión técnica");
      stage
          .getIcons()
          .add(
              new javafx.scene.image.Image(
                  Objects.requireNonNull(getClass().getResourceAsStream("/icons/voxora.png"))));
      stage.setMinWidth(1040);
      stage.setMinHeight(700);
      shell = new BorderPane();
      Scene scene = new Scene(shell, 1280, 820);
      scene
          .getStylesheets()
          .add(
              Objects.requireNonNull(getClass().getResource("/styles/voxora.css"))
                  .toExternalForm());
      scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> activity());
      scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> activity());
      scene.addEventFilter(ScrollEvent.SCROLL, e -> activity());
      stage.setScene(scene);
      stage.setOnCloseRequest(
          e -> {
            try {
              autosave.run();
            } catch (Exception failure) {
              e.consume();
              error(failure);
            }
          });
      login();
      stage.show();
      timer = new Timeline(new KeyFrame(Duration.seconds(5), e -> tick()));
      timer.setCycleCount(Animation.INDEFINITE);
      timer.play();
    } catch (Exception e) {
      if (failFast) throw new IllegalStateException(e);
      e.printStackTrace(System.err);
      error(e);
      Platform.exit();
    }
  }

  private void activity() {
    if (token != null && !busy)
      try {
        context.identity.activity(token);
      } catch (Exception expired) {
        logout(true);
      }
  }

  private void tick() {
    if (token == null || busy) return;
    try {
      long seconds = context.identity.idle(token).toSeconds();
      if (seconds >= inactivityMinutes * 60L) {
        logout(true);
        return;
      }
      autosave.run();
      idleWarning.setText(
          seconds >= (inactivityMinutes - 1) * 60L
              ? "La sesión se bloqueará en menos de un minuto. Interactúa para continuar."
              : "");
    } catch (Exception e) {
      if (token != null) {
        feedback.setText(e.getMessage());
        try {
          context.identity.current(token);
        } catch (Exception expired) {
          logout(true);
        }
      }
    }
  }

  // Sesión y navegación
  private void login() {
    autosave = () -> {};
    shell.setLeft(null);
    shell.setTop(null);
    shell.setBottom(null);
    boolean setup = context.identity.needsSetup();
    VBox form = new VBox(16);
    form.getStyleClass().add("login-card");
    form.setMaxWidth(440);
    form.setMaxHeight(Region.USE_PREF_SIZE);
    Label brand = label("voxora", "brand-login");
    Label heading = label(setup ? "Prepara tu instalación" : "Bienvenido de nuevo", "title");
    Label hint =
        muted(
            setup
                ? "Crea la primera cuenta de soporte. Después podrás añadir supervisores y"
                    + " técnicos."
                : "Ingresa a tu espacio de trabajo para continuar.");
    hint.setWrapText(true);
    TextField username = field("Usuario");
    PasswordField password = new PasswordField();
    password.setPromptText(setup ? "Contraseña (mínimo 10 caracteres)" : "Contraseña");
    PasswordField confirmation = new PasswordField();
    confirmation.setPromptText("Repite la contraseña");
    Label error = new Label();
    error.getStyleClass().add("error");
    error.setWrapText(true);
    Button submit = new Button(setup ? "Crear instalación" : "Iniciar sesión");
    submit.getStyleClass().add("primary");
    submit.setMaxWidth(Double.MAX_VALUE);
    submit.setDefaultButton(true);
    form.getChildren()
        .addAll(
            brand, heading, hint, labeled("Usuario", username), labeled("Contraseña", password));
    if (setup) form.getChildren().add(labeled("Confirmación", confirmation));
    form.getChildren()
        .addAll(error, submit, muted("Trabajo local · Revisión humana · Sin conexión"));
    StackPane center = new StackPane(form);
    center.setPadding(new Insets(28));
    shell.setCenter(center);
    submit.setOnAction(
        e -> {
          if (setup && !password.getText().equals(confirmation.getText())) {
            error.setText("Las contraseñas no coinciden.");
            return;
          }
          char[] secret = password.getText().toCharArray();
          String name = username.getText();
          password.clear();
          confirmation.clear();
          async(
              () -> {
                try {
                  if (setup) context.identity.setup(name, secret);
                  return context.identity.login(name, secret);
                } finally {
                  Arrays.fill(secret, '\0');
                }
              },
              session -> {
                token = session;
                user = context.identity.current(token);
                inactivityMinutes = context.identity.inactivityMinutes(token);
                model = new WorkspaceViewModel(context.incidents, token);
                workspace();
              },
              failure -> error.setText(failure.getMessage()));
        });
    Platform.runLater(username::requestFocus);
  }

  private void workspace() {
    VBox navigation = new VBox(8);
    navigation.getStyleClass().add("sidebar");
    navigation.setPrefWidth(224);
    navigationButtons.clear();
    navigation
        .getChildren()
        .addAll(label("voxora", "brand"), label("GESTIÓN TÉCNICA", "eyebrow"), new Separator());
    List<String> pages = new ArrayList<>(List.of("Inicio", "Mi jornada", "Incidencias", "Tablero"));
    if (user.role().managesIncidents())
      pages.addAll(List.of("Revisiones", "Indicadores", "Auditoría"));
    if (user.role() == Role.SUPERADMIN) pages.addAll(List.of("Usuarios", "Configuración"));
    for (String page : pages) {
      Button b = button(page, () -> navigate(page));
      b.getStyleClass().add("nav-button");
      navigationButtons.put(page, b);
      b.setMaxWidth(Double.MAX_VALUE);
      navigation.getChildren().add(b);
    }
    Region spring = new Region();
    VBox.setVgrow(spring, javafx.scene.layout.Priority.ALWAYS);
    navigation
        .getChildren()
        .addAll(spring, muted("Voxora 0.2.0"), muted("Mantenimiento · Voz local"));
    shell.setLeft(navigation);
    Label identity = label(user.username() + " · " + user.role(), "identity");
    Region spacer = new Region();
    HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
    HBox header =
        new HBox(
            14,
            breadcrumb,
            spacer,
            identity,
            button("Actualizar", () -> navigate(currentPage)),
            button("Cerrar sesión", () -> logout(false)));
    header.getStyleClass().add("topbar");
    header.setAlignment(Pos.CENTER_LEFT);
    shell.setTop(header);
    HBox footer = new HBox(16, feedback, idleWarning);
    footer.getStyleClass().add("footer");
    idleWarning.getStyleClass().add("error");
    shell.setBottom(footer);
    feedback.setText("Sesión iniciada. Todos los cambios se guardan en esta instalación.");
    navigate("Inicio");
  }

  private void navigate(String page) {
    safe(
        () -> {
          autosave.run();
          autosave = () -> {};
          currentPage = page;
          breadcrumb.setText("Mantenimiento  /  " + page);
          breadcrumb.getStyleClass().setAll("header-label");
          navigationButtons.forEach(
              (name, b) -> {
                b.getStyleClass().remove("nav-active");
                if (name.equals(page)) b.getStyleClass().add("nav-active");
              });
          model.refresh();
          switch (page) {
            case "Inicio" -> dashboard();
            case "Mi jornada" -> journey();
            case "Indicadores" -> indicators();
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

  private VBox page(String title, String subtitle) {
    VBox box = new VBox(22, label(title, "title"), muted(subtitle));
    box.getStyleClass().add("page");
    return box;
  }

  private void show(Node node) {
    ScrollPane scroll = new ScrollPane(node);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("page-scroll");
    shell.setCenter(scroll);
  }

  // Seguimiento del trabajo
  private void dashboard() {
    VBox page =
        page(
            "Tu operación, de un vistazo",
            java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " · Prioriza, documenta y da seguimiento al trabajo.");
    FlowPane actions = new FlowPane(10, 10);
    if (user.role().managesIncidents())
      actions.getChildren().add(primary("Nueva incidencia", this::createIncident));
    actions
        .getChildren()
        .addAll(
            button("Mi jornada", () -> navigate("Mi jornada")),
            button("Abrir tablero", () -> navigate("Tablero")));
    page.getChildren().addAll(actions, metrics());
    HBox section = new HBox(12, label("Qué hacer ahora", "section-title"));
    page.getChildren()
        .addAll(
            section,
            muted(
                "Trabajos abiertos, ordenados por prioridad. Abre una incidencia para continuar."));
    List<Incident> active =
        ordered(model.all().stream().filter(i -> i.status() != Status.COMPLETADO).toList());
    if (active.isEmpty())
      page.getChildren()
          .add(
              empty(
                  "Todo al día",
                  user.role().managesIncidents()
                      ? "Crea una incidencia y asígnala a un técnico para comenzar."
                      : "Tus trabajos aparecerán aquí cuando el supervisor los asigne."));
    else page.getChildren().add(incidentTable(active.stream().limit(8).toList()));
    show(page);
  }

  private List<Incident> ordered(List<Incident> incidents) {
    return incidents.stream()
        .sorted(Comparator.comparing(Incident::priority).thenComparingLong(Incident::id))
        .toList();
  }

  private Node metrics() {
    TilePane cards = new TilePane(12, 12);
    cards.setPrefColumns(5);
    cards.setPrefTileWidth(170);
    for (Status status : Status.values()) {
      long count = model.all().stream().filter(i -> i.status() == status).count();
      VBox card = new VBox(10, muted(status.toString()), label(Long.toString(count), "metric"));
      card.getStyleClass()
          .addAll("card", "metric-card", "state-" + status.name().toLowerCase(Locale.ROOT));
      Button link =
          button(
              "Ver detalle →",
              () -> {
                navigate("Incidencias");
                model.status.set(status);
              });
      link.getStyleClass().add("text-button");
      card.getChildren().add(link);
      cards.getChildren().add(card);
    }
    return cards;
  }

  private void journey() {
    VBox page =
        page(
            "Plan de trabajo",
            user.role().managesIncidents()
                ? "Trabajos asignados y en atención de esta instalación, por prioridad."
                : "Tus trabajos asignados y en atención, por prioridad.");
    List<Incident> work =
        ordered(
            model.all().stream()
                .filter(i -> i.status() == Status.ASIGNADO || i.status() == Status.EN_PROCESO)
                .toList());
    page.getChildren()
        .add(
            muted(
                work.size()
                    + " trabajos disponibles · La revisión de reportes se consulta por separado."));
    if (work.isEmpty())
      page.getChildren()
          .add(
              empty(
                  "Tu jornada está despejada",
                  "No hay trabajos asignados o en proceso para mostrar."));
    for (Incident i : work) page.getChildren().add(incidentCard(i));
    show(page);
  }

  private Node incidentCard(Incident i) {
    VBox text =
        new VBox(
            10,
            label(
                i.code() + " · " + i.priority(),
                "priority-" + i.priority().name().toLowerCase(Locale.ROOT)),
            label(i.title(), "section-title"),
            muted(i.equipment() + " · " + i.specialty() + " · " + i.technician()));
    HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);
    VBox right =
        new VBox(
            18,
            statusBadge(i.status()),
            primary(
                user.role().managesIncidents() ? "Ver asignación" : "Abrir atención",
                () -> detail(i.id())));
    right.setAlignment(Pos.CENTER_RIGHT);
    HBox row = new HBox(20, text, right);
    row.setAlignment(Pos.CENTER_LEFT);
    row.getStyleClass()
        .addAll("card", "work-card", "work-" + i.priority().name().toLowerCase(Locale.ROOT));
    return row;
  }

  private Label statusBadge(Status status) {
    return label(status.toString(), "badge state-" + status.name().toLowerCase(Locale.ROOT));
  }

  private void incidentList(boolean reviews) {
    VBox page =
        page(
            reviews ? "Reportes por validar" : "Incidencias",
            reviews
                ? "Comprueba la versión enviada y aprueba o solicita una corrección con motivo."
                : "Busca por código, actividad, equipo, ubicación o responsable.");
    model.status.set(reviews ? Status.EN_REVISION : null);
    model.query.set("");
    model.priority.set(null);
    model.technician.set(null);
    TextField search = field("Buscar incidencias…");
    search.setId("incident-search");
    search.textProperty().bindBidirectional(model.query);
    search.setPrefWidth(320);
    ComboBox<Status> filter = new ComboBox<>(FXCollections.observableArrayList(Status.values()));
    filter.setPromptText("Todos los estados");
    filter.valueProperty().bindBidirectional(model.status);
    filter.setDisable(reviews);
    ComboBox<dev.voxora.domain.Model.Priority> priority =
        new ComboBox<>(
            FXCollections.observableArrayList(dev.voxora.domain.Model.Priority.values()));
    priority.setPromptText("Todas las prioridades");
    priority.setId("priority-filter");
    priority.valueProperty().bindBidirectional(model.priority);
    FlowPane filters = new FlowPane(12, 12);
    filters.getStyleClass().add("filter-panel");
    filters.getChildren().addAll(search, labeled("Estado", filter), labeled("Prioridad", priority));
    if (user.role().managesIncidents()) {
      ComboBox<String> tech =
          new ComboBox<>(
              FXCollections.observableArrayList(
                  model.all().stream().map(Incident::technician).distinct().sorted().toList()));
      tech.setPromptText("Todos los técnicos");
      tech.setId("technician-filter");
      tech.valueProperty().bindBidirectional(model.technician);
      filters.getChildren().add(labeled("Responsable", tech));
    }
    filters
        .getChildren()
        .add(
            button(
                "Limpiar",
                () -> {
                  model.query.set("");
                  model.status.set(reviews ? Status.EN_REVISION : null);
                  model.priority.set(null);
                  model.technician.set(null);
                }));
    if (!reviews && user.role().managesIncidents())
      filters.getChildren().add(primary("Nueva incidencia", this::createIncident));
    Label count = muted("");
    count
        .textProperty()
        .bind(
            javafx.beans.binding.Bindings.size(model.visible)
                .asString("%d incidencias encontradas"));
    TableView<Incident> table = incidentTable(model.visible);
    table.setId("incident-table");
    table.setPlaceholder(
        empty(
            reviews ? "Estás al día" : "No hay coincidencias",
            reviews
                ? "Los reportes enviados aparecerán aquí para su revisión."
                : "Prueba otros filtros o crea una incidencia nueva."));
    Button open =
        button(
            reviews ? "Revisar seleccionada" : "Abrir seleccionada",
            () -> {
              Incident i = table.getSelectionModel().getSelectedItem();
              if (i != null) detail(i.id());
            });
    open.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    page.getChildren().addAll(filters, count, table, open);
    show(page);
  }

  private TableView<Incident> incidentTable(List<Incident> values) {
    return incidentTable(FXCollections.observableArrayList(values));
  }

  private TableView<Incident> incidentTable(javafx.collections.ObservableList<Incident> values) {
    TableView<Incident> table = new TableView<>(values);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table
        .prefHeightProperty()
        .bind(
            javafx.beans.binding.Bindings.size(values)
                .multiply(48)
                .add(58)
                .map(height -> Math.max(210, Math.min(490, height.doubleValue()))));
    table.setMinHeight(210);
    table
        .getColumns()
        .addAll(
            column("Código", Incident::code, 100),
            column("Actividad", Incident::title, 270),
            column("Equipo", Incident::equipment, 125),
            column("Prioridad", i -> i.priority().name(), 95),
            column("Responsable", Incident::technician, 125),
            column("Estado", i -> i.status().toString(), 130));
    Runnable open =
        () -> {
          Incident i = table.getSelectionModel().getSelectedItem();
          if (i != null) detail(i.id());
        };
    table.setOnMouseClicked(
        e -> {
          if (e.getClickCount() == 2) open.run();
        });
    table.setOnKeyPressed(
        e -> {
          if (e.getCode() == KeyCode.ENTER) open.run();
        });
    table.setRowFactory(
        v -> {
          TableRow<Incident> row = new TableRow<>();
          row.itemProperty()
              .addListener(
                  (o, a, b) -> {
                    row.setTooltip(b == null ? null : new Tooltip(b.title() + "\n" + b.location()));
                  });
          return row;
        });
    return table;
  }

  private void kanban() {
    VBox page =
        page(
            "Tablero operativo",
            "Sigue el avance del trabajo. Abre una tarjeta para realizar la siguiente acción.");
    HBox columns = new HBox(14);
    columns.setAlignment(Pos.TOP_LEFT);
    for (Status status : Status.values()) {
      List<Incident> incidents =
          ordered(model.all().stream().filter(i -> i.status() == status).toList());
      Region space = new Region();
      HBox.setHgrow(space, javafx.scene.layout.Priority.ALWAYS);
      HBox heading =
          new HBox(
              8,
              label(status.toString(), "section-title"),
              space,
              label(Integer.toString(incidents.size()), "badge"));
      VBox column = new VBox(14, heading);
      column.setPrefWidth(205);
      column.setMinWidth(180);
      column.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(column, javafx.scene.layout.Priority.ALWAYS);
      column.getStyleClass().add("kanban-column");
      for (Incident i : incidents) {
        Label title = label(i.title(), "section-title");
        VBox card =
            new VBox(
                12,
                label(
                    i.code() + " · " + i.priority(),
                    "priority-" + i.priority().name().toLowerCase(Locale.ROOT)),
                title,
                muted(i.equipment()),
                muted(i.technician()),
                button("Abrir", () -> detail(i.id())));
        card.getStyleClass().add("kanban-card");
        column.getChildren().add(card);
      }
      if (incidents.isEmpty()) column.getChildren().add(muted("Sin incidencias"));
      columns.getChildren().add(column);
    }
    page.getChildren().add(columns);
    show(page);
  }

  private void indicators() {
    VBox page =
        page(
            "Indicadores de mantenimiento",
            "Información de las incidencias registradas en esta instalación.");
    page.getChildren().add(metrics());
    double[] hours =
        model.all().stream()
            .filter(
                i ->
                    i.status() == Status.COMPLETADO
                        && i.startedAt() != null
                        && i.finishedAt() != null
                        && !i.finishedAt().isBefore(i.startedAt()))
            .mapToDouble(
                i ->
                    java.time.Duration.between(i.startedAt(), i.finishedAt()).toMillis()
                        / 3600000.0)
            .sorted()
            .toArray();
    String median =
        hours.length == 0
            ? "Sin cierres registrados"
            : String.format(
                Locale.forLanguageTag("es"),
                "%.2f h",
                hours.length % 2 == 1
                    ? hours[hours.length / 2]
                    : (hours[hours.length / 2 - 1] + hours[hours.length / 2]) / 2);
    VBox timing =
        new VBox(
            12,
            label("Mediana de inicio a cierre", "section-title"),
            label(median, "metric"),
            muted(
                "Incluye la espera de revisión. No representa horas efectivas ni descuenta"
                    + " pausas."));
    timing.getStyleClass().add("card");
    page.getChildren().add(timing);
    page.getChildren().add(label("Distribución por prioridad", "section-title"));
    for (dev.voxora.domain.Model.Priority priority : dev.voxora.domain.Model.Priority.values()) {
      long count = model.all().stream().filter(i -> i.priority() == priority).count();
      ProgressBar bar =
          new ProgressBar(model.all().isEmpty() ? 0 : (double) count / model.all().size());
      bar.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(bar, javafx.scene.layout.Priority.ALWAYS);
      HBox row =
          new HBox(
              16,
              label(priority.name(), "priority-" + priority.name().toLowerCase(Locale.ROOT)),
              bar,
              muted(count + " incidencias"));
      row.setAlignment(Pos.CENTER_LEFT);
      page.getChildren().add(row);
    }
    page.getChildren()
        .add(
            muted(
                "La reincidencia por tipo correctivo requiere un catálogo de tipos antes de"
                    + " calcularse."));
    show(page);
  }

  // Incidencias y atención técnica
  private void createIncident() {
    Dialog<ButtonType> dialog = dialog("Nueva incidencia", 760, 650);
    TextField title = field("Actividad solicitada"),
        equipment = field("Código o nombre del equipo"),
        location = field("Área o ubicación");
    TextArea description = area("Describe el problema o la necesidad", 6);
    Button dictate =
        primary(
            "Hablar",
            () ->
                VoiceDialog.forIncident(
                        context,
                        token,
                        dialog.getDialogPane().getScene().getWindow(),
                        !description.getText().isBlank(),
                        description::setText)
                    .showAndRecord());
    Label voiceHint =
        muted(
            "Cuéntanos el problema. Preparamos la descripción y tú confirmas los datos antes de"
                + " crear la incidencia.");
    ComboBox<dev.voxora.domain.Model.Priority> priority =
        combo(dev.voxora.domain.Model.Priority.values(), dev.voxora.domain.Model.Priority.MEDIA);
    ComboBox<Specialty> specialty = combo(Specialty.values(), Specialty.GENERAL);
    VBox form =
        new VBox(
            14,
            new HBox(12, dictate, voiceHint),
            labeled("Título", title),
            labeled("Equipo", equipment),
            labeled("Ubicación", location),
            labeled("Prioridad", priority),
            labeled("Especialidad", specialty),
            labeled("Descripción", description));
    dialog.getDialogPane().setContent(scroll(form));
    ButtonType create = new ButtonType("Crear incidencia", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);
    Button b = (Button) dialog.getDialogPane().lookupButton(create);
    b.addEventFilter(
        javafx.event.ActionEvent.ACTION,
        e -> {
          e.consume();
          safe(
              () -> {
                long id =
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

  private void detail(long id) {
    safe(() -> showDetail(id));
  }

  private void showDetail(long id) {
    Incident i = context.incidents.read(token, id);
    boolean editable =
        user.role() == Role.TECHNICIAN
            && Objects.equals(i.assignedTo(), user.id())
            && i.status() == Status.EN_PROCESO;
    Dialog<ButtonType> dialog = dialog(i.code() + " · " + i.title(), 960, 780);
    VBox content = new VBox(16);
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
    if (!i.observation().isBlank())
      content
          .getChildren()
          .add(labeled("Observación del supervisor", readArea(i.observation(), 3)));
    HBox actions = new HBox(12);
    if (user.role().managesIncidents()
        && (i.status() == Status.PENDIENTE || i.status() == Status.ASIGNADO)) {
      ComboBox<User> technicians =
          new ComboBox<>(
              FXCollections.observableArrayList(
                  context.identity.users(token).stream()
                      .filter(
                          u ->
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
                            if (technicians.getValue() == null)
                              throw new RuleViolation("Selecciona un técnico.");
                            context.incidents.assign(token, id, technicians.getValue().id());
                            dialog.close();
                            navigate(currentPage);
                            detail(id);
                          })));
    }
    if (user.role() == Role.TECHNICIAN && i.status() == Status.ASIGNADO)
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
    TextArea draft = area("Describe las actividades, pruebas y resultados…", 10);
    draft.setText(i.draft());
    draft.setEditable(editable);
    if (i.startedAt() != null)
      content
          .getChildren()
          .addAll(label(editable ? "Reporte de atención" : "Reporte", "section-title"), draft);
    AtomicReference<String> saved = new AtomicReference<>(i.draft());
    Label savedLabel =
        muted(editable ? "Guardado automático cada 5 segundos." : "Versión de solo lectura.");
    Runnable save =
        () -> {
          if (editable && token != null && !saved.get().equals(draft.getText())) {
            Version v = context.incidents.save(token, id, draft.getText());
            saved.set(draft.getText());
            savedLabel.setText("Borrador guardado · Versión " + v.number());
          }
        };
    if (editable) {
      autosave = save;
      content.getChildren().add(savedLabel);
      actions
          .getChildren()
          .add(
              button(
                  "Hablar",
                  () -> {
                    save.run();
                    new VoiceDialog(
                            context,
                            token,
                            id,
                            dialog.getDialogPane().getScene().getWindow(),
                            v -> {
                              draft.setText(v.content());
                              saved.set(v.content());
                              savedLabel.setText(
                                  "Propuesta guardada · Versión "
                                      + v.number()
                                      + " · revisa antes de enviar");
                            })
                        .showAndRecord();
                  }));
      actions
          .getChildren()
          .addAll(
              button(
                  "Guardar borrador",
                  () ->
                      safe(
                          () -> {
                            Version v = context.incidents.save(token, id, draft.getText());
                            saved.set(draft.getText());
                            savedLabel.setText("Guardado · Versión " + v.number());
                          })),
              primary(
                  "Vista previa y envío",
                  () ->
                      safe(
                          () -> {
                            Version v = context.incidents.preview(token, id, draft.getText());
                            saved.set(draft.getText());
                            Dialog<ButtonType> preview =
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
                            ButtonType confirm =
                                new ButtonType(
                                    "Confirmar envío a supervisión", ButtonBar.ButtonData.OK_DONE);
                            preview
                                .getDialogPane()
                                .getButtonTypes()
                                .addAll(
                                    confirm,
                                    new ButtonType(
                                        "Volver y corregir", ButtonBar.ButtonData.CANCEL_CLOSE));
                            if (preview.showAndWait().orElse(ButtonType.CANCEL) == confirm) {
                              context.incidents.submit(token, id, v.id());
                              autosave = () -> {};
                              dialog.close();
                              navigate(currentPage);
                              detail(id);
                            }
                          })));
    }
    if (user.role().managesIncidents() && i.status() == Status.EN_REVISION) {
      actions
          .getChildren()
          .addAll(
              button(
                  "Solicitar corrección",
                  () -> {
                    TextInputDialog reason = new TextInputDialog();
                    reason.initOwner(stage);
                    reason.setTitle("Devolver reporte");
                    reason.setHeaderText("Indica qué debe corregir el técnico");
                    reason
                        .showAndWait()
                        .ifPresent(
                            text ->
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
                    if (confirm(
                        "Aprobar reporte",
                        "Se aprobará la versión enviada y se completará la incidencia."))
                      async(
                          () -> context.incidents.approve(token, id),
                          path -> {
                            dialog.close();
                            navigate(currentPage);
                            notice(
                                "Reporte aprobado",
                                "El PDF quedó guardado. Puedes abrirlo desde la incidencia.");
                          });
                  }));
    }
    if (i.status() == Status.COMPLETADO)
      actions
          .getChildren()
          .addAll(
              primary(
                  "Abrir PDF",
                  () -> async(() -> context.incidents.export(token, id, false), this::openFile)),
              button(
                  "Abrir DOCX",
                  () -> async(() -> context.incidents.export(token, id, true), this::openFile)));
    FlowPane flow = new FlowPane(12, 12);
    flow.getChildren().addAll(new ArrayList<>(actions.getChildren()));
    content.getChildren().add(flow);
    if (i.startedAt() != null)
      content.getChildren().add(button("Consultar versiones", () -> versions(id)));
    content.getChildren().add(label("Historial de la incidencia", "section-title"));
    for (Event event : context.incidents.events(token, id))
      content.getChildren().add(eventNode(event));
    dialog.getDialogPane().setContent(scroll(content));
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    dialog.setOnCloseRequest(
        e -> {
          try {
            save.run();
            autosave = () -> {};
          } catch (Exception failure) {
            e.consume();
            error(failure);
          }
        });
    // Dialogs have their own scenes; their input is human activity too.
    dialog.showAndWait();
    autosave = () -> {};
  }

  private void versions(long id) {
    Dialog<ButtonType> dialog = dialog("Versiones del reporte", 850, 650);
    ListView<Version> list =
        new ListView<>(FXCollections.observableArrayList(context.incidents.versions(token, id)));
    list.setPrefHeight(180);
    list.setCellFactory(
        v ->
            new ListCell<>() {
              @Override
              protected void updateItem(Version item, boolean empty) {
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
    TextArea text = readArea("Selecciona una versión para consultar su contenido.", 14);
    list.getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (o, a, b) -> {
              if (b != null) text.setText(b.content());
            });
    dialog.getDialogPane().setContent(new VBox(14, list, text));
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    dialog.showAndWait();
  }

  // Administración y configuración
  private void users() {
    VBox page =
        page(
            "Usuarios de la instalación",
            "Crea cuentas, asigna perfiles y desactiva accesos conservando el historial.");
    TableView<User> table =
        new TableView<>(FXCollections.observableArrayList(context.identity.users(token)));
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    table.setPrefHeight(420);
    table.getColumns().add(column("Usuario", User::username, 220));
    table.getColumns().add(column("Perfil", u -> u.role().toString(), 240));
    table.getColumns().add(column("Especialidad", u -> u.specialty().toString(), 200));
    table.getColumns().add(column("Acceso", u -> u.active() ? "Activo" : "Desactivado", 140));
    page.getChildren()
        .addAll(
            new HBox(
                12,
                primary("Crear usuario", () -> userForm(null)),
                button(
                    "Editar seleccionado",
                    () -> {
                      User selected = table.getSelectionModel().getSelectedItem();
                      if (selected != null) userForm(selected);
                    })),
            table,
            muted(
                "Si un técnico tiene incidencias abiertas, primero reasígnalas o finalízalas antes"
                    + " de desactivarlo."));
    show(page);
  }

  private void userForm(User target) {
    Dialog<ButtonType> dialog =
        dialog(target == null ? "Crear usuario" : "Editar usuario", 600, 620);
    TextField name = field("Nombre de usuario");
    if (target != null) {
      name.setText(target.username());
      name.setEditable(false);
    }
    ComboBox<Role> role = combo(Role.values(), target == null ? Role.TECHNICIAN : target.role());
    ComboBox<Specialty> specialty =
        combo(Specialty.values(), target == null ? Specialty.GENERAL : target.specialty());
    CheckBox active = new CheckBox("Cuenta activa");
    active.setSelected(target == null || target.active());
    PasswordField password = new PasswordField();
    password.setPromptText("Mínimo 10 caracteres");
    VBox form =
        new VBox(
            14,
            labeled("Usuario", name),
            labeled("Perfil", role),
            labeled("Especialidad", specialty),
            active);
    if (target == null) form.getChildren().add(labeled("Contraseña inicial", password));
    else form.getChildren().add(button("Restablecer contraseña…", () -> resetPassword(target)));
    dialog.getDialogPane().setContent(form);
    ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
    dialog
        .getDialogPane()
        .lookupButton(save)
        .addEventFilter(
            javafx.event.ActionEvent.ACTION,
            e -> {
              e.consume();
              char[] secret = password.getText().toCharArray();
              String username = name.getText();
              Role selectedRole = role.getValue();
              Specialty selectedSpecialty = specialty.getValue();
              boolean enabled = active.isSelected();
              async(
                  () -> {
                    try {
                      if (target == null)
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
                  ok -> {
                    password.clear();
                    dialog.close();
                    navigate("Usuarios");
                  });
            });
    dialog.showAndWait();
  }

  private void resetPassword(User target) {
    Dialog<ButtonType> dialog = dialog("Restablecer contraseña · " + target.username(), 480, 300);
    PasswordField field = new PasswordField();
    field.setPromptText("Nueva contraseña, mínimo 10 caracteres");
    dialog.getDialogPane().setContent(field);
    ButtonType save = new ButtonType("Restablecer", ButtonBar.ButtonData.OK_DONE);
    dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
    dialog
        .getDialogPane()
        .lookupButton(save)
        .addEventFilter(
            javafx.event.ActionEvent.ACTION,
            e -> {
              e.consume();
              char[] secret = field.getText().toCharArray();
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
                  ok -> {
                    dialog.close();
                    if (target.id() == user.id()) logout(false);
                    else
                      notice(
                          "Contraseña restablecida", "Las sesiones de esa cuenta fueron cerradas.");
                  });
            });
    dialog.showAndWait();
  }

  private void settings() {
    VBox page =
        page(
            "Configuración",
            "Administra la sesión y conserva copias de la información de esta instalación.");
    Spinner<Integer> minutes = new Spinner<>(5, 30, context.identity.inactivityMinutes(token));
    VBox session =
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
    VBox backup =
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
                      FileChooser chooser = zipChooser("Guardar respaldo");
                      chooser.setInitialFileName(
                          "Voxora-"
                              + java.time.LocalDateTime.now()
                                  .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                              + ".zip");
                      java.io.File file = chooser.showSaveDialog(stage);
                      if (file != null)
                        async(
                            () -> {
                              context.backups.backup(token, file.toPath());
                              return true;
                            },
                            ok ->
                                notice(
                                    "Respaldo creado",
                                    "La copia quedó guardada en la ubicación elegida."));
                    }),
                button(
                    "Restaurar respaldo…",
                    () -> {
                      java.io.File file = zipChooser("Seleccionar respaldo").showOpenDialog(stage);
                      if (file != null
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
                            ok -> {
                              logout(false);
                              notice(
                                  "Restauración completada",
                                  "Inicia sesión con una cuenta del respaldo restaurado.");
                            });
                    })));
    backup.getStyleClass().add("card");
    Label aiState =
        muted(
            context.voice.runtime().available()
                ? "Componentes locales encontrados: Whisper Tiny y Qwen3 4B."
                : "Componentes no preparados. Ejecuta scripts/prepare-ai.ps1 o selecciona una"
                    + " carpeta preparada.");
    VBox ai =
        new VBox(
            14,
            label("Voz e inteligencia local", "section-title"),
            aiState,
            muted(context.voice.runtime().home().toString()),
            button(
                "Seleccionar carpeta de motores y modelos…",
                () -> {
                  DirectoryChooser chooser = new DirectoryChooser();
                  chooser.setTitle("Componentes locales de Voxora");
                  var directory = chooser.showDialog(stage);
                  if (directory != null)
                    async(
                        () -> {
                          context.voice.configure(token, directory.toPath());
                          return true;
                        },
                        ok -> navigate("Configuración"));
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

  private void audit() {
    VBox page = page("Auditoría", "Acciones registradas con responsable y fecha.");
    for (Event event : context.incidents.events(token, null))
      page.getChildren().add(eventNode(event));
    show(page);
  }

  private Node eventNode(Event event) {
    Label detail = muted(event.detail());
    detail.setWrapText(true);
    VBox row =
        new VBox(
            6,
            label(event.action().replace('_', ' ') + " · " + event.actor(), "event-title"),
            detail,
            muted(dates.format(event.at())));
    row.getStyleClass().add("event");
    return row;
  }

  private void logout(boolean expired) {
    if (token == null) return;
    String previous = token;
    try {
      autosave.run();
    } catch (Exception e) {
      feedback.setText("No se pudo guardar el último cambio: " + e.getMessage());
    }
    autosave = () -> {};
    token = null;
    user = null;
    for (Window window : List.copyOf(Window.getWindows())) if (window != stage) window.hide();
    context.identity.logout(previous, expired);
    idleWarning.setText("");
    login();
    if (expired)
      notice(
          "Sesión bloqueada",
          "Inicia sesión para continuar. Los borradores guardados permanecen disponibles.");
  }

  private <T> void async(Callable<T> action, Consumer<T> success) {
    async(action, success, this::error);
  }

  private <T> void async(Callable<T> action, Consumer<T> success, Consumer<Throwable> failure) {
    if (busy) return;
    busy = true;
    String session = token;
    List<Window> windows = List.copyOf(Window.getWindows());
    for (Window w : windows) if (w.getScene() != null) w.getScene().getRoot().setDisable(true);
    feedback.setText("Procesando…");
    Task<T> task =
        new Task<>() {
          @Override
          protected T call() throws Exception {
            return action.call();
          }
        };
    Runnable enable =
        () -> {
          busy = false;
          for (Window w : windows)
            if (w.getScene() != null) w.getScene().getRoot().setDisable(false);
          feedback.setText("Listo.");
        };
    task.setOnSucceeded(
        e -> {
          enable.run();
          if (Objects.equals(session, token)) safe(() -> success.accept(task.getValue()));
        });
    task.setOnFailed(
        e -> {
          enable.run();
          failure.accept(task.getException());
        });
    worker.submit(task);
  }

  private void safe(Runnable action) {
    try {
      action.run();
    } catch (Exception e) {
      error(e);
    }
  }

  private void error(Throwable e) {
    Alert alert = new Alert(Alert.AlertType.ERROR);
    if (stage != null && stage.isShowing()) alert.initOwner(stage);
    alert.setTitle("Voxora");
    alert.setHeaderText("No se pudo completar la acción");
    alert.setContentText(
        e.getMessage() == null ? "Revisa los datos e intenta nuevamente." : e.getMessage());
    alert.showAndWait();
  }

  private void notice(String title, String message) {
    Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
    a.initOwner(stage);
    a.setTitle("Voxora");
    a.setHeaderText(title);
    a.showAndWait();
  }

  private boolean confirm(String title, String message) {
    Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
    a.initOwner(stage);
    a.setTitle("Voxora");
    a.setHeaderText(title);
    return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }

  private Dialog<ButtonType> dialog(String title, double width, double height) {
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.initOwner(stage);
    dialog.setTitle(title);
    dialog.setResizable(true);
    dialog.getDialogPane().setPrefSize(width, height);
    dialog.getDialogPane().getStylesheets().add(stage.getScene().getStylesheets().getFirst());
    dialog.setOnShown(
        e -> {
          Scene scene = dialog.getDialogPane().getScene();
          scene.addEventFilter(KeyEvent.KEY_PRESSED, k -> activity());
          scene.addEventFilter(MouseEvent.MOUSE_PRESSED, k -> activity());
          scene.addEventFilter(ScrollEvent.SCROLL, k -> activity());
        });
    return dialog;
  }

  private static FileChooser zipChooser(String title) {
    FileChooser c = new FileChooser();
    c.setTitle(title);
    c.getExtensionFilters()
        .add(new FileChooser.ExtensionFilter("Respaldo Voxora (*.zip)", "*.zip"));
    return c;
  }

  private void openFile(Path file) {
    getHostServices().showDocument(file.toUri().toString());
  }

  private static Label label(String text, String style) {
    Label label = new Label(text);
    label.getStyleClass().addAll(style.split("\\s+"));
    label.setWrapText(true);
    return label;
  }

  private static Label muted(String text) {
    return label(text, "muted");
  }

  private static TextField field(String prompt) {
    TextField f = new TextField();
    f.setPromptText(prompt);
    return f;
  }

  private static TextArea area(String prompt, int rows) {
    TextArea t = new TextArea();
    t.setPromptText(prompt);
    t.setPrefRowCount(rows);
    t.setWrapText(true);
    return t;
  }

  private static TextArea readArea(String text, int rows) {
    TextArea a = area("", rows);
    a.setText(text);
    a.setEditable(false);
    return a;
  }

  private static VBox labeled(String title, Node input) {
    return new VBox(6, label(title, "field-label"), input);
  }

  private Button button(String text, Runnable action) {
    Button b = new Button(text);
    b.setOnAction(e -> safe(action));
    return b;
  }

  private Button primary(String text, Runnable action) {
    Button b = button(text, action);
    b.getStyleClass().add("primary");
    return b;
  }

  private static <T> ComboBox<T> combo(T[] values, T selected) {
    ComboBox<T> c = new ComboBox<>(FXCollections.observableArrayList(values));
    c.setValue(selected);
    c.setMaxWidth(Double.MAX_VALUE);
    return c;
  }

  private static ScrollPane scroll(Node content) {
    ScrollPane s = new ScrollPane(content);
    s.setFitToWidth(true);
    s.setPadding(new Insets(8));
    return s;
  }

  private static Node empty(String title, String subtitle) {
    VBox box = new VBox(10, label(title, "section-title"), muted(subtitle));
    box.getStyleClass().add("card");
    return box;
  }

  private static <T> TableColumn<T, String> column(
      String title, Function<T, String> value, double width) {
    TableColumn<T, String> c = new TableColumn<>(title);
    c.setCellValueFactory(row -> new ReadOnlyStringWrapper(value.apply(row.getValue())));
    c.setPrefWidth(width);
    return c;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  @Override
  public void stop() throws Exception {
    if (timer != null) timer.stop();
    worker.shutdown();
    if (!worker.awaitTermination(5, TimeUnit.SECONDS)) worker.shutdownNow();
    if (context != null) context.close();
  }
}
