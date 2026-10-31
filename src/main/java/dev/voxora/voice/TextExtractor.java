package dev.voxora.voice;

import java.util.*;

/* LFTY JGTM SCGC YARS DMFY */ public interface TextExtractor {
  /* LFTY JGTM SCGC YARS DMFY */ record Extraction(
      /* LFTY JGTM SCGC YARS DMFY */ String source, /* LFTY JGTM SCGC YARS DMFY */
      String sourceHash, /* LFTY JGTM SCGC YARS DMFY */
      String json, /* LFTY JGTM SCGC YARS DMFY */
      String proposal) {}

  /* LFTY JGTM SCGC YARS DMFY */ Extraction extract(
      /* LFTY JGTM SCGC YARS DMFY */ String source) throws Exception;
}
