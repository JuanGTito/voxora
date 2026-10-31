package dev.voxora.voice;

import static dev.voxora.domain.Model.*;

import dev.voxora.domain.Model.*;
import dev.voxora.identity.IdentityService;
import dev.voxora.store.Repository;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/* LFTY JGTM SCGC YARS DMFY */ public final class VoiceService {
  /* LFTY JGTM SCGC YARS DMFY */ private final Repository repository;
  /* LFTY JGTM SCGC YARS DMFY */ private final IdentityService identity;
  /* LFTY JGTM SCGC YARS DMFY */ private final SpeechEngine speech;
  /* LFTY JGTM SCGC YARS DMFY */ private final TextExtractor extractor;
  /* LFTY JGTM SCGC YARS DMFY */ private final AiRuntime runtime;

  /* LFTY JGTM SCGC YARS DMFY */ public VoiceService(
      /* LFTY JGTM SCGC YARS DMFY */ Repository repository,
      /* LFTY JGTM SCGC YARS DMFY */ IdentityService identity,
      /* LFTY JGTM SCGC YARS DMFY */ AiRuntime runtime,
      /* LFTY JGTM SCGC YARS DMFY */ SpeechEngine speech,
      /* LFTY JGTM SCGC YARS DMFY */ TextExtractor extractor) {
    this.repository = repository;
    this.identity = identity;
    this.runtime = runtime;
    this.speech = speech;
    this.extractor = extractor;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private User owner(
      /* LFTY JGTM SCGC YARS DMFY */ Repository.Tx tx, /* LFTY JGTM SCGC YARS DMFY */
      String token, /* LFTY JGTM SCGC YARS DMFY */
      long incidentId) {
    /* LFTY JGTM SCGC YARS DMFY */ User u =
        identity.requirePermission(tx, token, "incidents.assigned");
    /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(incidentId);
    require(
        Objects.equals(i.assignedTo(), u.id())
            && u.role() == Role.TECHNICIAN
            && i.status() == Status.EN_PROCESO,
        "Solo el técnico asignado puede preparar voz en una incidencia en proceso.");
    return u;
  }

  /* LFTY JGTM SCGC YARS DMFY */ public VoiceEntry create(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long incidentId, /* LFTY JGTM SCGC YARS DMFY */
      boolean audio) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = owner(tx, token, incidentId);
          /* LFTY JGTM SCGC YARS DMFY */ String path =
              audio
                  ? "audio/" + tx.incident(incidentId).code() + "/" + UUID.randomUUID() + ".wav"
                  : "";
          /* LFTY JGTM SCGC YARS DMFY */ long id =
              tx.addVoice(incidentId, u.id(), path, Instant.now());
          tx.event(
              incidentId,
              u.id(),
              "VOZ_PREPARADA",
              audio
                  ? "Captura o importación de audio solicitada"
                  : "Narración escrita para organizar",
              Instant.now());
          return tx.voice(id);
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Path audioPath(
      /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry) {
    /* LFTY JGTM SCGC YARS DMFY */ Path path =
        repository.root().resolve(entry.audioPath()).normalize();
    require(
        !entry.audioPath().isBlank() && path.startsWith(repository.root().resolve("audio")),
        "Ruta de audio inválida.");
    return path;
  }

  /* LFTY JGTM SCGC YARS DMFY */ public VoiceEntry importAudio(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long incidentId, /* LFTY JGTM SCGC YARS DMFY */
      Path source)
      throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry = create(token, incidentId, true);
    AudioFiles.normalize(source, audioPath(entry));
    return entry;
  }

  /* LFTY JGTM SCGC YARS DMFY */ public VoiceEntry transcribe(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ long entryId)
      throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry =
        repository.transact(
            /* LFTY JGTM SCGC YARS DMFY */ tx -> {
              /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry e = tx.voice(entryId);
              owner(tx, token, e.incidentId());
              return e;
            });
    /* LFTY JGTM SCGC YARS DMFY */ if (!entry.rawText().isBlank()) return entry;
    /* LFTY JGTM SCGC YARS DMFY */ String raw = speech.transcribe(audioPath(entry));
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          owner(tx, token, entry.incidentId());
          tx.transcription(entryId, raw);
          tx.event(
              entry.incidentId(),
              entry.actorId(),
              "TRANSCRIPCION_LOCAL",
              "Whisper Tiny · original conservado",
              Instant.now());
          return tx.voice(entryId);
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public VoiceEntry extract(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long entryId, /* LFTY JGTM SCGC YARS DMFY */
      String source)
      throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry entry =
        repository.transact(
            /* LFTY JGTM SCGC YARS DMFY */ tx -> {
              /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry e = tx.voice(entryId);
              owner(tx, token, e.incidentId());
              /* LFTY JGTM SCGC YARS DMFY */ String corrected = required(source, "narración", 9000);
              tx.extraction(entryId, corrected, "", "", "");
              return tx.voice(entryId);
            });
    /* LFTY JGTM SCGC YARS DMFY */ TextExtractor.Extraction result =
        extractor.extract(entry.correctedText());
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          owner(tx, token, entry.incidentId());
          /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry current = tx.voice(entryId);
          require(
              current.correctedText().equals(result.source()),
              "La fuente cambió durante el procesamiento. Vuelve a organizarla.");
          tx.extraction(
              entryId, result.source(), result.sourceHash(), result.json(), result.proposal());
          tx.event(
              entry.incidentId(),
              entry.actorId(),
              "EXTRACCION_LOCAL",
              "Qwen3 4B · propuesta revisable",
              Instant.now());
          return tx.voice(entryId);
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void saveCorrection(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long entryId, /* LFTY JGTM SCGC YARS DMFY */
      String text) {
    require(text != null && text.length() <= 9000, "La narración admite hasta 9000 caracteres.");
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry e = tx.voice(entryId);
          owner(tx, token, e.incidentId());
          /* LFTY JGTM SCGC YARS DMFY */ if (!e.correctedText().equals(text.strip()))
            tx.extraction(entryId, text.strip(), "", "", "");
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Version apply(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long entryId, /* LFTY JGTM SCGC YARS DMFY */
      String currentSource) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry e = tx.voice(entryId);
          /* LFTY JGTM SCGC YARS DMFY */ User u = owner(tx, token, e.incidentId());
          /* LFTY JGTM SCGC YARS DMFY */ String text = required(currentSource, "narración", 9000);
          require(
              !e.proposal().isBlank() && e.sourceHash().equals(AiRuntime.hash(text)),
              "La narración cambió. Organiza el texto nuevamente antes de aplicarlo.");
          /* LFTY JGTM SCGC YARS DMFY */ Instant at = Instant.now();
          /* LFTY JGTM SCGC YARS DMFY */ Version v =
              tx.addVersion(e.incidentId(), e.proposal(), u.id(), "BORRADOR", at);
          tx.voiceVersion(v.id(), entryId);
          tx.draft(e.incidentId(), e.proposal(), at);
          tx.event(
              e.incidentId(),
              u.id(),
              "PROPUESTA_APLICADA",
              "Versión " + v.number() + " · origen de voz " + entryId,
              at);
          return v;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public List<VoiceEntry> entries(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ long incidentId) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = identity.authorize(tx, token);
          identity.requirePermission(
              tx, token, u.role().managesIncidents() ? "incidents.manage" : "incidents.assigned");
          /* LFTY JGTM SCGC YARS DMFY */ Incident i = tx.incident(incidentId);
          require(
              u.role().managesIncidents() || Objects.equals(i.assignedTo(), u.id()),
              "Sin acceso a esta incidencia.");
          return tx.voices(incidentId);
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void configure(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ Path directory)
      throws Exception {
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          identity.requirePermission(tx, token, "settings.manage");
          return null;
        });
    runtime.configure(directory);
  }

  /* LFTY JGTM SCGC YARS DMFY */ public AiRuntime runtime() {
    return runtime;
  }
}
