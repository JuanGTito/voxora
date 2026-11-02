package dev.voxora.voice;

import java.util.*;

/* LFTY · JGTM · SCGC · YARS · DMFY */
public interface TextExtractor {
  record Extraction(String source, String sourceHash, String json, String proposal) {}

  Extraction extract(String source) throws Exception;
}
