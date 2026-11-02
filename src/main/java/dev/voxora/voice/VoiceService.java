package dev.voxora.voice;

import static dev.voxora.domain.Model.*;

import dev.voxora.domain.Model.*;
import dev.voxora.identity.IdentityService;
import dev.voxora.store.Repository;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class VoiceService {
  private final Repository repository;
  private final IdentityService identity;
  private final SpeechEngine speech;
  private final TextExtractor extractor;
  private final AiRuntime runtime;

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public VoiceService(
      Repository repository,
      IdentityService identity,
      AiRuntime runtime,
      SpeechEngine speech,
      TextExtractor extractor) {
    this.repository = repository;
    this.identity = identity;
    this.runtime = runtime;
    this.speech = speech;
    this.extractor = extractor;
  }

  private User owner(Repository.Tx tx, String token, long incidentId) {
    User u = identity.requirePermission(tx, token, "incidents.assigned");
    Incident i = tx.incident(incidentId);
    require(
        Objects.equals(i.assignedTo(), u.id())
            && u.role() == Role.TECHNICIAN
            && i.status() == Status.EN_PROCESO,
        "Solo el técnico asignado puede preparar voz en una incidencia en proceso.");
    return u;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public VoiceEntry create(String token, long incidentId, boolean audio) {
    return repository.transact(
        tx -> {
          User u = owner(tx, token, incidentId);
          String path =
              audio
                  ? "audio/" + tx.incident(incidentId).code() + "/" + UUID.randomUUID() + ".wav"
                  : "";
          long id = tx.addVoice(incidentId, u.id(), path, Instant.now());
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

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Path audioPath(VoiceEntry entry) {
    Path path = repository.root().resolve(entry.audioPath()).normalize();
    require(
        !entry.audioPath().isBlank() && path.startsWith(repository.root().resolve("audio")),
        "Ruta de audio inválida.");
    return path;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public VoiceEntry importAudio(String token, long incidentId, Path source) throws Exception {
    VoiceEntry entry = create(token, incidentId, true);
    AudioFiles.normalize(source, audioPath(entry));
    return entry;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public VoiceEntry transcribe(String token, long entryId) throws Exception {
    VoiceEntry entry =
        repository.transact(
            tx -> {
              VoiceEntry e = tx.voice(entryId);
              owner(tx, token, e.incidentId());
              return e;
            });
    if (!entry.rawText().isBlank()) return entry;
    String raw = speech.transcribe(audioPath(entry));
    return repository.transact(
        tx -> {
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

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public VoiceEntry extract(String token, long entryId, String source) throws Exception {
    VoiceEntry entry =
        repository.transact(
            tx -> {
              VoiceEntry e = tx.voice(entryId);
              owner(tx, token, e.incidentId());
              String corrected = required(source, "narración", 9000);
              tx.extraction(entryId, corrected, "", "", "");
              return tx.voice(entryId);
            });
    TextExtractor.Extraction result = extractor.extract(entry.correctedText());
    return repository.transact(
        tx -> {
          owner(tx, token, entry.incidentId());
          VoiceEntry current = tx.voice(entryId);
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

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void saveCorrection(String token, long entryId, String text) {
    require(text != null && text.length() <= 9000, "La narración admite hasta 9000 caracteres.");
    repository.transact(
        tx -> {
          VoiceEntry e = tx.voice(entryId);
          owner(tx, token, e.incidentId());
          if (!e.correctedText().equals(text.strip()))
            tx.extraction(entryId, text.strip(), "", "", "");
          return null;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Version apply(String token, long entryId, String currentSource) {
    return apply(token, entryId, currentSource, null);
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Version apply(String token, long entryId, String currentSource, String reviewedProposal) {
    return repository.transact(
        tx -> {
          VoiceEntry e = tx.voice(entryId);
          User u = owner(tx, token, e.incidentId());
          String text = required(currentSource, "narración", 9000);
          require(
              !e.proposal().isBlank() && e.sourceHash().equals(AiRuntime.hash(text)),
              "La narración cambió. Organiza el texto nuevamente antes de aplicarlo.");
          Instant at = Instant.now();
          String content =
              reviewedProposal == null
                  ? e.proposal()
                  : required(reviewedProposal, "reporte revisado", 30000);
          Version v = tx.addVersion(e.incidentId(), content, u.id(), "BORRADOR", at);
          tx.voiceVersion(v.id(), entryId);
          tx.draft(e.incidentId(), content, at);
          tx.event(
              e.incidentId(),
              u.id(),
              "PROPUESTA_APLICADA",
              "Versión " + v.number() + " · origen de voz " + entryId,
              at);
          return v;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public List<VoiceEntry> entries(String token, long incidentId) {
    return repository.transact(
        tx -> {
          User u = identity.authorize(tx, token);
          identity.requirePermission(
              tx, token, u.role().managesIncidents() ? "incidents.manage" : "incidents.assigned");
          Incident i = tx.incident(incidentId);
          require(
              u.role().managesIncidents() || Objects.equals(i.assignedTo(), u.id()),
              "Sin acceso a esta incidencia.");
          return tx.voices(incidentId);
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void configure(String token, Path directory) throws Exception {
    repository.transact(
        tx -> {
          identity.requirePermission(tx, token, "settings.manage");
          return null;
        });
    runtime.configure(directory);
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public AiRuntime runtime() {
    return runtime;
  }
}
