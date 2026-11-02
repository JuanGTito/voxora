package dev.voxora.ui;

import static dev.voxora.domain.Model.*;

import dev.voxora.bootstrap.AppContext;
import dev.voxora.domain.Model.*;
import dev.voxora.voice.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Two adapters for the same guided flow: an existing attention or an uncreated incident. */
interface VoiceDraftSession {
  List<VoiceEntry> history() throws Exception;

  VoiceEntry create(boolean audio) throws Exception;

  Path audio(VoiceEntry entry);

  VoiceEntry transcribe(VoiceEntry entry) throws Exception;

  VoiceEntry organize(VoiceEntry entry, String source) throws Exception;

  void correct(VoiceEntry entry, String source) throws Exception;

  void accept(VoiceEntry entry, String source, String reviewed) throws Exception;

  boolean replacesContent();

  String acceptLabel();

  static VoiceDraftSession report(
      AppContext context, String token, long incidentId, Consumer<Version> accepted) {
    return new VoiceDraftSession() {
      /* LFTY · JGTM · SCGC · YARS · DMFY */
      public List<VoiceEntry> history() {
        return context.voice.entries(token, incidentId);
      }

      /* LFTY · JGTM · SCGC · YARS · DMFY */
      public VoiceEntry create(boolean audio) {
        return context.voice.create(token, incidentId, audio);
      }

      /* LFTY · JGTM · SCGC · YARS · DMFY */
      public Path audio(VoiceEntry entry) {
        return context.voice.audioPath(entry);
      }

      /* LFTY · JGTM · SCGC · YARS · DMFY */
      public VoiceEntry transcribe(VoiceEntry entry) throws Exception {
        return context.voice.transcribe(token, entry.id());
      }

      /* LFTY · JGTM · SCGC · YARS · DMFY */
      public VoiceEntry organize(VoiceEntry entry, String source) throws Exception {
        return context.voice.extract(token, entry.id(), source);
      }

      /* LFTY · JGTM · SCGC · YARS · DMFY */
      public void correct(VoiceEntry entry, String source) {
        context.voice.saveCorrection(token, entry.id(), source);
      }

      /* LFTY · JGTM · SCGC · YARS · DMFY */
      public void accept(VoiceEntry entry, String source, String reviewed) {
        accepted.accept(context.voice.apply(token, entry.id(), source, reviewed));
      }

      /* LFTY · JGTM · SCGC · YARS · DMFY */
      public boolean replacesContent() {
        return !context.incidents.read(token, incidentId).draft().isBlank();
      }

      /* LFTY · JGTM · SCGC · YARS · DMFY */
      public String acceptLabel() {
        return "Guardar borrador";
      }
    };
  }

  static VoiceDraftSession incident(
      AppContext context, String token, boolean replaces, Consumer<String> accepted) {
    return new PendingIncident(context, token, replaces, accepted);
  }

  /** Pending dictation is saved per actor, before there is any incident to reference. */
  final class PendingIncident implements VoiceDraftSession {
    private final AppContext context;
    private final String token;
    private final Consumer<String> accepted;
    private final boolean replaces;
    private final long actor;
    private final Path folder;

    PendingIncident(AppContext context, String token, boolean replaces, Consumer<String> accepted) {
      this.context = context;
      this.token = token;
      this.accepted = accepted;
      this.replaces = replaces;
      actor = authorize().id();
      folder = context.repository.root().resolve("pending-dictation/u-" + actor);
    }

    private User authorize() {
      return context.repository.transact(
          tx -> context.identity.requirePermission(tx, token, "incidents.manage"));
    }

    private Path metadata(long id) {
      return folder.resolve(Long.toUnsignedString(id) + ".properties");
    }

    private void store(VoiceEntry entry) throws IOException {
      Files.createDirectories(folder);
      Properties values = new Properties();
      values.setProperty("id", Long.toString(entry.id()));
      values.setProperty("audio", entry.audioPath());
      values.setProperty("raw", entry.rawText());
      values.setProperty("corrected", entry.correctedText());
      values.setProperty("hash", entry.sourceHash());
      values.setProperty("json", entry.extractionJson());
      values.setProperty("proposal", entry.proposal());
      values.setProperty("at", entry.createdAt().toString());
      Path file = metadata(entry.id()),
          temporary = file.resolveSibling(file.getFileName() + ".tmp");
      try (var out = Files.newOutputStream(temporary)) {
        values.store(out, "Voxora pending incident dictation");
      }
      Files.move(
          temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private VoiceEntry load(long id) throws IOException {
      Properties p = new Properties();
      try (var in = Files.newInputStream(metadata(id))) {
        p.load(in);
      }
      return new VoiceEntry(
          id,
          0,
          actor,
          p.getProperty("audio", ""),
          p.getProperty("raw", ""),
          p.getProperty("corrected", ""),
          p.getProperty("hash", ""),
          p.getProperty("json", ""),
          p.getProperty("proposal", ""),
          Instant.parse(p.getProperty("at")));
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public List<VoiceEntry> history() throws Exception {
      authorize();
      if (!Files.isDirectory(folder)) return List.of();
      List<VoiceEntry> result = new ArrayList<>();
      try (var files = Files.list(folder)) {
        for (Path file :
            files.filter(p -> p.getFileName().toString().endsWith(".properties")).toList()) {
          String name = file.getFileName().toString();
          result.add(load(Long.parseUnsignedLong(name.substring(0, name.length() - 11))));
        }
      }
      return result.stream()
          .sorted(Comparator.comparing(VoiceEntry::createdAt).reversed())
          .toList();
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public VoiceEntry create(boolean audio) throws Exception {
      authorize();
      long id = UUID.randomUUID().getMostSignificantBits();
      VoiceEntry entry =
          new VoiceEntry(
              id,
              0,
              actor,
              audio ? Long.toUnsignedString(id) + ".wav" : "",
              "",
              "",
              "",
              "",
              "",
              Instant.now());
      store(entry);
      return entry;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public Path audio(VoiceEntry entry) {
      Path path = folder.resolve(entry.audioPath()).normalize();
      require(
          !entry.audioPath().isBlank() && path.startsWith(folder), "Ruta de grabación inválida.");
      return path;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public VoiceEntry transcribe(VoiceEntry entry) throws Exception {
      authorize();
      entry = load(entry.id());
      if (!entry.rawText().isBlank()) return entry;
      String raw = new WhisperEngine(context.voice.runtime()).transcribe(audio(entry));
      authorize();
      VoiceEntry result =
          new VoiceEntry(
              entry.id(), 0, actor, entry.audioPath(), raw, raw, "", "", "", entry.createdAt());
      store(result);
      return result;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public VoiceEntry organize(VoiceEntry entry, String source) throws Exception {
      correct(entry, source);
      var result = new LlamaExtractor(context.voice.runtime()).extract(source);
      authorize();
      entry = load(entry.id());
      require(
          entry.correctedText().equals(result.source()),
          "La narración cambió. Prepara el texto nuevamente.");
      VoiceEntry organized =
          new VoiceEntry(
              entry.id(),
              0,
              actor,
              entry.audioPath(),
              entry.rawText(),
              result.source(),
              result.sourceHash(),
              result.json(),
              completeDescription(result),
              entry.createdAt());
      store(organized);
      return organized;
    }

    static String completeDescription(TextExtractor.Extraction result) throws IOException {
      Set<Integer> selected = new HashSet<>();
      for (var category : AiRuntime.JSON.readTree(result.json()))
        for (var index : category) selected.add(index.intValue());
      List<String> units = LlamaExtractor.units(result.source());
      StringBuilder description = new StringBuilder(result.proposal());
      boolean heading = false;
      for (int i = 0; i < units.size(); i++)
        if (!selected.contains(i)) {
          if (!heading) {
            description.append("\n\nOTROS DETALLES DE LA NARRACIÓN\n");
            heading = true;
          }
          description.append("• ").append(units.get(i)).append('\n');
        }
      return description.toString().strip();
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public void correct(VoiceEntry entry, String source) throws Exception {
      authorize();
      require(
          source != null && source.length() <= 9000, "La narración admite hasta 9000 caracteres.");
      entry = load(entry.id());
      if (entry.correctedText().equals(source.strip())) return;
      store(
          new VoiceEntry(
              entry.id(),
              0,
              actor,
              entry.audioPath(),
              entry.rawText(),
              source.strip(),
              "",
              "",
              "",
              entry.createdAt()));
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public void accept(VoiceEntry entry, String source, String reviewed) throws Exception {
      authorize();
      entry = load(entry.id());
      require(
          !entry.proposal().isBlank() && entry.sourceHash().equals(AiRuntime.hash(source.strip())),
          "Corrige y prepara nuevamente la narración antes de continuar.");
      accepted.accept(required(reviewed, "descripción", 5000));
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public boolean replacesContent() {
      return replaces;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public String acceptLabel() {
      return "Usar descripción";
    }
  }
}
