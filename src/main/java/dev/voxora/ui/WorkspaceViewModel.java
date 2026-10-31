package dev.voxora.ui;

import dev.voxora.domain.Model.*;
import dev.voxora.incidents.IncidentService;
import java.util.*;
import javafx.beans.property.*;
import javafx.collections.*;

/** Presentation state; all business rules remain in application services. */
/* LFTY JGTM SCGC YARS DMFY */ public final class WorkspaceViewModel {
  /* LFTY JGTM SCGC YARS DMFY */ public final StringProperty query = new SimpleStringProperty("");
  /* LFTY JGTM SCGC YARS DMFY */ public final ObjectProperty<Status> status =
      new SimpleObjectProperty<>();
  /* LFTY JGTM SCGC YARS DMFY */ public final ObservableList<Incident> visible =
      FXCollections.observableArrayList();
  /* LFTY JGTM SCGC YARS DMFY */ private final IncidentService service;
  /* LFTY JGTM SCGC YARS DMFY */ private final String token;
  /* LFTY JGTM SCGC YARS DMFY */ private List<Incident> all = List.of();

  /* LFTY JGTM SCGC YARS DMFY */ public WorkspaceViewModel(
      /* LFTY JGTM SCGC YARS DMFY */ IncidentService service, /* LFTY JGTM SCGC YARS DMFY */
      String token) {
    this.service = service;
    this.token = token;
    query.addListener(
        (/* LFTY JGTM SCGC YARS DMFY */ o, /* LFTY JGTM SCGC YARS DMFY */
            a, /* LFTY JGTM SCGC YARS DMFY */
            b) -> filter());
    status.addListener(
        (/* LFTY JGTM SCGC YARS DMFY */ o, /* LFTY JGTM SCGC YARS DMFY */
            a, /* LFTY JGTM SCGC YARS DMFY */
            b) -> filter());
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void refresh() {
    all = service.list(token);
    filter();
  }

  /* LFTY JGTM SCGC YARS DMFY */ public List<Incident> all() {
    return List.copyOf(all);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void filter() {
    /* LFTY JGTM SCGC YARS DMFY */ String text = query.get().strip().toLowerCase(Locale.ROOT);
    visible.setAll(
        all.stream()
            .filter(
                /* LFTY JGTM SCGC YARS DMFY */ i ->
                    status.get() == null || i.status() == status.get())
            .filter(
                /* LFTY JGTM SCGC YARS DMFY */ i ->
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
