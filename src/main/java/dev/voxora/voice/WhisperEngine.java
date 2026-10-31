package dev.voxora.voice;

import static dev.voxora.domain.Model.require;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/* LFTY JGTM SCGC YARS DMFY */ public final class WhisperEngine implements SpeechEngine {
  /* LFTY JGTM SCGC YARS DMFY */ private final AiRuntime runtime;

  /* LFTY JGTM SCGC YARS DMFY */ public WhisperEngine(
      /* LFTY JGTM SCGC YARS DMFY */ AiRuntime runtime) {
    this.runtime = runtime;
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public String transcribe(/* LFTY JGTM SCGC YARS DMFY */ Path audio) throws Exception {
    synchronized (runtime) {
      return transcribeLocked(audio);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private String transcribeLocked(
      /* LFTY JGTM SCGC YARS DMFY */ Path audio) throws Exception {
    runtime.verify();
    AudioFiles.validate(audio);
    /* LFTY JGTM SCGC YARS DMFY */ Path prefix =
        runtime.work().resolve(UUID.randomUUID().toString());
    /* LFTY JGTM SCGC YARS DMFY */ Path log = Path.of(prefix + ".log"),
        text = Path.of(prefix + ".txt");
    /* LFTY JGTM SCGC YARS DMFY */ Process process = null;
    try {
      process =
          AiRuntime.start(
              List.of(
                  runtime.whisper().toString(),
                  "-m",
                  runtime.tiny().toString(),
                  "-f",
                  audio.toAbsolutePath().toString(),
                  "-l",
                  "es",
                  "-t",
                  "4",
                  "-bs",
                  "3",
                  "-ng",
                  "-otxt",
                  "-of",
                  prefix.toString(),
                  "-nt"),
              log);
      require(
          process.waitFor(240, TimeUnit.SECONDS),
          "La transcripción superó 240 segundos. Se conserva el audio para reintentar.");
      require(
          process.exitValue() == 0 && Files.isRegularFile(text),
          "El motor de transcripción falló. Se conserva el audio; revisa los componentes locales.");
      /* LFTY JGTM SCGC YARS DMFY */ String result =
          Files.readString(text, StandardCharsets.UTF_8).strip();
      require(
          !result.isBlank(),
          "No se detectó voz reconocible. Puedes reintentar o escribir el texto.");
      return result;
    } finally {
      AiRuntime.stop(process);
      AiRuntime.cleanup(log, text);
    }
  }
}
