package dev.voxora.ui;

import dev.voxora.domain.Model.*;
import dev.voxora.incidents.IncidentService;
import java.util.*;
import javafx.beans.property.*;
import javafx.collections.*;

/** Presentation state; all business rules remain in application services. */
/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class WorkspaceViewModel {
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public final StringProperty query = new SimpleStringProperty("");
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public final ObjectProperty<Status> status = new SimpleObjectProperty<>();
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public final ObjectProperty<Priority> priority = new SimpleObjectProperty<>();
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public final StringProperty technician = new SimpleStringProperty();
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public final ObservableList<Incident> visible = FXCollections.observableArrayList();
  private final IncidentService service;
  private final String token;
  private List<Incident> all = List.of();

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public WorkspaceViewModel(IncidentService service, String token) {
    this.service = service;
    this.token = token;
    priority.addListener((o, a, b) -> filter());
    technician.addListener((o, a, b) -> filter());
    query.addListener((o, a, b) -> filter());
    status.addListener((o, a, b) -> filter());
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void refresh() {
    all = service.list(token);
    filter();
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public List<Incident> all() {
    return List.copyOf(all);
  }

  private void filter() {
    String text = query.get().strip().toLowerCase(Locale.ROOT);
    visible.setAll(
        all.stream()
            .filter(i -> priority.get() == null || i.priority() == priority.get())
            .filter(i -> technician.get() == null || i.technician().equals(technician.get()))
            .filter(i -> status.get() == null || i.status() == status.get())
            .filter(
                i ->
                    (i.code()
                            + " "
                            + i.title()
                            + " "
                            + i.equipment()
                            + " "
                            + i.location()
                            + " "
                            + i.technician())
                        .toLowerCase(Locale.ROOT)
                        .contains(text))
            .toList());
  }
}
