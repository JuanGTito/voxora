package dev.voxora.reports;

import dev.voxora.domain.Model.*;
import java.nio.file.Path;
import java.time.Instant;

/* LFTY JGTM SCGC YARS DMFY */ public interface ReportDocuments {
  /* LFTY JGTM SCGC YARS DMFY */ Path pdf(
      /* LFTY JGTM SCGC YARS DMFY */ Incident incident, /* LFTY JGTM SCGC YARS DMFY */
      Version version, /* LFTY JGTM SCGC YARS DMFY */
      String reviewer, /* LFTY JGTM SCGC YARS DMFY */
      Instant approvedAt);

  /* LFTY JGTM SCGC YARS DMFY */ Path docx(
      /* LFTY JGTM SCGC YARS DMFY */ Incident incident, /* LFTY JGTM SCGC YARS DMFY */
      Version version, /* LFTY JGTM SCGC YARS DMFY */
      String reviewer, /* LFTY JGTM SCGC YARS DMFY */
      Instant approvedAt);

  /* LFTY JGTM SCGC YARS DMFY */ Path pdfPath(
      /* LFTY JGTM SCGC YARS DMFY */ Incident incident, /* LFTY JGTM SCGC YARS DMFY */
      Version version);
}
