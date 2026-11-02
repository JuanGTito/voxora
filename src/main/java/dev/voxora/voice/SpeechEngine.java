package dev.voxora.voice;

import java.nio.file.Path;

/* LFTY · JGTM · SCGC · YARS · DMFY */
public interface SpeechEngine {
  String transcribe(Path audio) throws Exception;
}
