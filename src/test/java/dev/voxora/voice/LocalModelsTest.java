package dev.voxora.voice;

import static org.junit.jupiter.api.Assertions.*;

import dev.voxora.bootstrap.AppContext;
import dev.voxora.domain.Model.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Explicit integration test: real local CPU engines, synthetic Spanish audio, isolated data. */
/* LFTY JGTM SCGC YARS DMFY */ @EnabledIfSystemProperty(named = "voxora.realAi", matches = "true")
class LocalModelsTest {
  /* LFTY JGTM SCGC YARS DMFY */ @TempDir Path root;

  /* LFTY JGTM SCGC YARS DMFY */ @Test
  void realAudioToDraftReviewAndPdf() throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ Path fixture =
        Path.of(System.getProperty("voxora.audioFixture"));
    /* LFTY JGTM SCGC YARS DMFY */ Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("at", Instant.now().toString());
    evidence.put("audioFixtureSha256", AiRuntime.hash(fixture));
    evidence.put("microphoneTested", false);
    evidence.put("microphoneFormatDetected", AudioFiles.microphoneSupported());
    try (
    /* LFTY JGTM SCGC YARS DMFY */ AppContext context = new AppContext(root)) {
      /* LFTY JGTM SCGC YARS DMFY */ String password = "Synthetic-test-2026!";
      context.identity.setup("soporte", password.toCharArray());
      /* LFTY JGTM SCGC YARS DMFY */ String admin =
          context.identity.login("soporte", password.toCharArray());
      context.identity.createUser(
          admin, "tecnico", password.toCharArray(), Role.TECHNICIAN, Specialty.MECANICA);
      /* LFTY JGTM SCGC YARS DMFY */ String tech =
          context.identity.login("tecnico", password.toCharArray());
      /* LFTY JGTM SCGC YARS DMFY */ long incident =
          context.incidents.create(
              admin,
              "Inspeccionar bomba",
              "B-01",
              "Taller",
              "Prueba aislada con audio sintético.",
              Priority.MEDIA,
              Specialty.MECANICA);
      context.incidents.assign(admin, incident, context.identity.current(tech).id());
      context.incidents.start(tech, incident);
      /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry =
          context.voice.importAudio(tech, incident, fixture);
      /* LFTY JGTM SCGC YARS DMFY */ long begin = System.nanoTime();
      entry = context.voice.transcribe(tech, entry.id());
      evidence.put("transcriptionSeconds", (System.nanoTime() - begin) / 1e9);
      evidence.put("transcription", entry.rawText());
      assertTrue(entry.rawText().length() > 30);
      assertTrue(entry.rawText().toLowerCase(Locale.ROOT).contains("bomba"), entry.rawText());
      begin = System.nanoTime();
      /* LFTY JGTM SCGC YARS DMFY */ String corrected =
          "Se revisó la bomba del taller. Se cambió la junta de goma. No se observaron fugas. No se"
              + " midió presión. El equipo quedó operativo.";
      evidence.put("correctionSuppliedByTest", true);
      evidence.put("correctedTranscript", corrected);
      entry = context.voice.extract(tech, entry.id(), corrected);
      evidence.put("extractionSeconds", (System.nanoTime() - begin) / 1e9);
      evidence.put("indices", AiRuntime.JSON.readTree(entry.extractionJson()));
      evidence.put("proposal", entry.proposal());
      assertTrue(entry.proposal().contains("No se midió presión."));
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ var test :
          AiRuntime.JSON.readTree(entry.extractionJson()).path("pruebas"))
        assertNotEquals(3, test.intValue());
      /* LFTY JGTM SCGC YARS DMFY */ Version v =
          context.voice.apply(tech, entry.id(), entry.correctedText());
      assertEquals(Status.EN_PROCESO, context.incidents.read(tech, incident).status());
      context.incidents.submit(tech, incident, v.id());
      /* LFTY JGTM SCGC YARS DMFY */ Path pdf = context.incidents.approve(admin, incident);
      assertTrue(Files.size(pdf) > 1000);
      evidence.put("finalStatus", context.incidents.read(admin, incident).status().name());
      /* LFTY JGTM SCGC YARS DMFY */ Path folder = Path.of("target/ai-evidence");
      Files.createDirectories(folder);
      Files.copy(pdf, folder.resolve("synthetic-ticket.pdf"), StandardCopyOption.REPLACE_EXISTING);
      AiRuntime.JSON
          .writerWithDefaultPrettyPrinter()
          .writeValue(folder.resolve("local-models.json").toFile(), evidence);
    }
  }
}
