package dev.voxora.voice;

import static dev.voxora.domain.Model.require;

import com.fasterxml.jackson.databind.*;
import dev.voxora.bootstrap.AppContext;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Local processes only. No shell interpolation, inference downloads, GPU or LAN listener. */
/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class AiRuntime {
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static final ObjectMapper JSON = new ObjectMapper();
  private final Path root;
  private Path home;
  private final Map<Path, String> verified = new HashMap<>();

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public AiRuntime(Path root) {
    this.root = root;
    this.home = defaultHome();
    loadSetting();
  }

  private Path defaultHome() {
    String explicit = System.getProperty("voxora.ai.home", System.getenv("VOXORA_AI_HOME"));
    if (explicit != null && !explicit.isBlank())
      return Path.of(explicit).toAbsolutePath().normalize();
    try {
      Path bundled =
          Path.of(AiRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI())
              .getParent()
              .resolve("ai");
      if (Files.isDirectory(bundled)) return bundled;
    } catch (Exception ignored) {
    }
    Path project = Path.of(".local-runtime").toAbsolutePath();
    if (Files.isDirectory(project)) return project;
    return AppContext.defaultRoot().resolve("ai");
  }

  private void loadSetting() {
    Path setting = root.resolve("ai-home.properties");
    if (!Files.isRegularFile(setting)) return;
    try (var in = Files.newInputStream(setting)) {
      Properties p = new Properties();
      p.load(in);
      home = Path.of(p.getProperty("home")).toAbsolutePath().normalize();
    } catch (Exception e) {
      throw new IllegalStateException("La ruta configurada de IA no es válida.", e);
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public synchronized Path home() {
    return home;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public synchronized void configure(Path path) throws IOException {
    Path previous = home;
    home = path.toAbsolutePath().normalize();
    verified.clear();
    try {
      verify();
      Properties p = new Properties();
      p.setProperty("home", home.toString());
      Path staged = root.resolve("ai-home.properties.tmp");
      try (var out = Files.newOutputStream(staged)) {
        p.store(out, "Voxora local AI path");
      }
      Files.move(
          staged,
          root.resolve("ai-home.properties"),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      home = previous;
      verified.clear();
      if (e instanceof IOException io) throw io;
      throw e;
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public synchronized boolean available() {
    return Files.isRegularFile(home.resolve("models/ggml-tiny.bin"))
        && Files.isRegularFile(home.resolve("models/Qwen3-4B-Q4_K_M.gguf"))
        && Files.isRegularFile(home.resolve("whisper/Release/whisper-cli.exe"))
        && Files.isRegularFile(home.resolve("llama/llama-server.exe"));
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public synchronized void verify() {
    require(
        available(),
        "Prepara los motores y modelos con scripts/prepare-ai.ps1 o selecciona su carpeta en"
            + " Configuración.");
    try (var in = AiRuntime.class.getResourceAsStream("/ai/components.json")) {
      for (JsonNode component : JSON.readTree(Objects.requireNonNull(in)).path("components")) {
        Path file = home.resolve(component.path("file").asText());
        String stamp = Files.size(file) + ":" + Files.getLastModifiedTime(file);
        if (stamp.equals(verified.get(file))) continue;
        require(
            Files.size(file) == component.path("size").asLong()
                && hash(file).equals(component.path("sha256").asText()),
            "Un componente local de IA no coincide con su SHA-256: "
                + component.path("id").asText());
        verified.put(file, stamp);
      }
      // Verify extracted executables and DLLs too, not only their downloaded archives.
      for (String archive :
          List.of("downloads/whisper-bin-x64.zip", "downloads/llama-b11838-bin-win-cpu-x64.zip")) {
        String directory = archive.contains("whisper") ? "whisper" : "llama";
        try (var zip = new java.util.zip.ZipFile(home.resolve(archive).toFile())) {
          var entries = zip.entries();
          while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (entry.isDirectory()
                || !(entry.getName().endsWith(".exe") || entry.getName().endsWith(".dll")))
              continue;
            Path local = home.resolve(directory).resolve(entry.getName()).normalize();
            require(local.startsWith(home.resolve(directory)), "Ruta del motor inválida.");
            String stamp = Files.size(local) + ":" + Files.getLastModifiedTime(local);
            if (stamp.equals(verified.get(local))) continue;
            try (var original = zip.getInputStream(entry);
                var actual = Files.newInputStream(local)) {
              require(
                  Arrays.equals(digest(original), digest(actual)),
                  "El motor extraído fue modificado: " + entry.getName());
            }
            verified.put(local, stamp);
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("No se pudieron verificar los componentes de IA.", e);
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Path whisper() {
    return home().resolve("whisper/Release/whisper-cli.exe");
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Path tiny() {
    return home().resolve("models/ggml-tiny.bin");
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Path llama() {
    return home().resolve("llama/llama-server.exe");
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Path qwen() {
    return home().resolve("models/Qwen3-4B-Q4_K_M.gguf");
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Path work() throws IOException {
    Path dir = root.resolve("cache/ai-work");
    Files.createDirectories(dir);
    return dir;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static String hash(Path path) throws IOException {
    try (var in = Files.newInputStream(path)) {
      return HexFormat.of().formatHex(digest(in));
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static String hash(String text) {
    return HexFormat.of()
        .formatHex(
            digest(
                new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  }

  private static byte[] digest(InputStream in) {
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      byte[] bytes = new byte[65536];
      int n;
      while ((n = in.read(bytes)) != -1) d.update(bytes, 0, n);
      return d.digest();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static Process start(List<String> command, Path log) throws IOException {
    ProcessBuilder builder =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
    builder.environment().remove("HTTP_PROXY");
    builder.environment().remove("HTTPS_PROXY");
    builder.environment().remove("ALL_PROXY");
    return builder.start();
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static void stop(Process process) {
    if (process == null) return;
    List<ProcessHandle> children = process.descendants().toList();
    children.forEach(ProcessHandle::destroy);
    process.destroy();
    try {
      if (!process.waitFor(3, TimeUnit.SECONDS)) {
        children.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(3, TimeUnit.SECONDS);
      }
      for (ProcessHandle child : children) {
        if (child.isAlive()) child.destroyForcibly();
        try {
          child.onExit().get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
      }
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static void cleanup(Path... files) {
    for (Path file : files) {
      boolean removed = false;
      for (int attempt = 0; attempt < 20; attempt++) {
        try {
          Files.deleteIfExists(file);
          removed = true;
          break;
        } catch (IOException locked) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
      if (!removed) file.toFile().deleteOnExit();
    }
  }
}
