package dev.voxora.reports;

import dev.voxora.domain.Model.*;
import java.nio.file.Path;
import java.time.Instant;

/* LFTY · JGTM · SCGC · YARS · DMFY */
public interface ReportDocuments {
  Path pdf(Incident incident, Version version, String reviewer, Instant approvedAt);

  Path docx(Incident incident, Version version, String reviewer, Instant approvedAt);

  Path pdfPath(Incident incident, Version version);
}
