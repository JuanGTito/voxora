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
/* LFTY JGTM SCGC YARS DMFY */ public final class AiRuntime {
  /* LFTY JGTM SCGC YARS DMFY */ public static final ObjectMapper JSON = new ObjectMapper();
  /* LFTY JGTM SCGC YARS DMFY */ private final Path root;
  /* LFTY JGTM SCGC YARS DMFY */ private Path home;
  /* LFTY JGTM SCGC YARS DMFY */ private final Map<Path, String> verified = new HashMap<>();

  /* LFTY JGTM SCGC YARS DMFY */ public AiRuntime(/* LFTY JGTM SCGC YARS DMFY */ Path root) {
    this.root = root;
    this.home = defaultHome();
    loadSetting();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private Path defaultHome() {
    /* LFTY JGTM SCGC YARS DMFY */ String explicit =
        System.getProperty("voxora.ai.home", System.getenv("VOXORA_AI_HOME"));
    /* LFTY JGTM SCGC YARS DMFY */ if (explicit != null && !explicit.isBlank())
      return Path.of(explicit).toAbsolutePath().normalize();
    try {
      /* LFTY JGTM SCGC YARS DMFY */ Path bundled =
          Path.of(AiRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI())
              .getParent()
              .resolve("ai");
      /* LFTY JGTM SCGC YARS DMFY */ if (Files.isDirectory(bundled)) return bundled;
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception ignored) {
    }
    /* LFTY JGTM SCGC YARS DMFY */ Path project = Path.of(".local-runtime").toAbsolutePath();
    /* LFTY JGTM SCGC YARS DMFY */ if (Files.isDirectory(project)) return project;
    return AppContext.defaultRoot().resolve("ai");
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void loadSetting() {
    /* LFTY JGTM SCGC YARS DMFY */ Path setting = root.resolve("ai-home.properties");
    /* LFTY JGTM SCGC YARS DMFY */ if (!Files.isRegularFile(setting)) return;
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var in = Files.newInputStream(setting)) {
      /* LFTY JGTM SCGC YARS DMFY */ Properties p = new Properties();
      p.load(in);
      home = Path.of(p.getProperty("home")).toAbsolutePath().normalize();
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      throw new IllegalStateException("La ruta configurada de IA no es válida.", e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public synchronized Path home() {
    return home;
  }

  /* LFTY JGTM SCGC YARS DMFY */ public synchronized void configure(
      /* LFTY JGTM SCGC YARS DMFY */ Path path) throws IOException {
    /* LFTY JGTM SCGC YARS DMFY */ Path previous = home;
    home = path.toAbsolutePath().normalize();
    verified.clear();
    try {
      verify();
      /* LFTY JGTM SCGC YARS DMFY */ Properties p = new Properties();
      p.setProperty("home", home.toString());
      /* LFTY JGTM SCGC YARS DMFY */ Path staged = root.resolve("ai-home.properties.tmp");
      try (
      /* LFTY JGTM SCGC YARS DMFY */ var out = Files.newOutputStream(staged)) {
        p.store(out, "Voxora local AI path");
      }
      Files.move(
          staged,
          root.resolve("ai-home.properties"),
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      home = previous;
      verified.clear();
      /* LFTY JGTM SCGC YARS DMFY */ if (e instanceof /* LFTY JGTM SCGC YARS DMFY */ IOException io)
        throw io;
      throw e;
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public synchronized boolean available() {
    return Files.isRegularFile(home.resolve("models/ggml-tiny.bin"))
        && Files.isRegularFile(home.resolve("models/Qwen3-4B-Q4_K_M.gguf"))
        && Files.isRegularFile(home.resolve("whisper/Release/whisper-cli.exe"))
        && Files.isRegularFile(home.resolve("llama/llama-server.exe"));
  }

  /* LFTY JGTM SCGC YARS DMFY */ public synchronized void verify() {
    require(
        available(),
        "Prepara los motores y modelos con scripts/prepare-ai.ps1 o selecciona su carpeta en"
            + " Configuración.");
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var in =
        AiRuntime.class.getResourceAsStream("/ai/components.json")) {
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ JsonNode component :
          JSON.readTree(Objects.requireNonNull(in)).path("components")) {
        /* LFTY JGTM SCGC YARS DMFY */ Path file = home.resolve(component.path("file").asText());
        /* LFTY JGTM SCGC YARS DMFY */ String stamp =
            Files.size(file) + ":" + Files.getLastModifiedTime(file);
        /* LFTY JGTM SCGC YARS DMFY */ if (stamp.equals(verified.get(file))) continue;
        require(
            Files.size(file) == component.path("size").asLong()
                && hash(file).equals(component.path("sha256").asText()),
            "Un componente local de IA no coincide con su SHA-256: "
                + component.path("id").asText());
        verified.put(file, stamp);
      }
      // Verify extracted executables and DLLs too, not only their downloaded archives.
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ String archive :
          List.of("downloads/whisper-bin-x64.zip", "downloads/llama-b11838-bin-win-cpu-x64.zip")) {
        /* LFTY JGTM SCGC YARS DMFY */ String directory =
            archive.contains("whisper") ? "whisper" : "llama";
        try (
        /* LFTY JGTM SCGC YARS DMFY */ var zip =
            new java.util.zip.ZipFile(home.resolve(archive).toFile())) {
          /* LFTY JGTM SCGC YARS DMFY */ var entries = zip.entries();
          /* LFTY JGTM SCGC YARS DMFY */ while (entries.hasMoreElements()) {
            /* LFTY JGTM SCGC YARS DMFY */ var entry = entries.nextElement();
            /* LFTY JGTM SCGC YARS DMFY */ if (entry.isDirectory()
                || !(entry.getName().endsWith(".exe") || entry.getName().endsWith(".dll")))
              continue;
            /* LFTY JGTM SCGC YARS DMFY */ Path local =
                home.resolve(directory).resolve(entry.getName()).normalize();
            require(local.startsWith(home.resolve(directory)), "Ruta del motor inválida.");
            /* LFTY JGTM SCGC YARS DMFY */ String stamp =
                Files.size(local) + ":" + Files.getLastModifiedTime(local);
            /* LFTY JGTM SCGC YARS DMFY */ if (stamp.equals(verified.get(local))) continue;
            try (
            /* LFTY JGTM SCGC YARS DMFY */ var original = zip.getInputStream(entry);
                /* LFTY JGTM SCGC YARS DMFY */ var actual = Files.newInputStream(local)) {
              require(
                  Arrays.equals(digest(original), digest(actual)),
                  "El motor extraído fue modificado: " + entry.getName());
            }
            verified.put(local, stamp);
          }
        }
      }
    } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException e) {
      throw new IllegalStateException("No se pudieron verificar los componentes de IA.", e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Path whisper() {
    return home().resolve("whisper/Release/whisper-cli.exe");
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Path tiny() {
    return home().resolve("models/ggml-tiny.bin");
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Path llama() {
    return home().resolve("llama/llama-server.exe");
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Path qwen() {
    return home().resolve("models/Qwen3-4B-Q4_K_M.gguf");
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Path work() throws IOException {
    /* LFTY JGTM SCGC YARS DMFY */ Path dir = root.resolve("cache/ai-work");
    Files.createDirectories(dir);
    return dir;
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static String hash(
      /* LFTY JGTM SCGC YARS DMFY */ Path path) throws IOException {
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var in = Files.newInputStream(path)) {
      return HexFormat.of().formatHex(digest(in));
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static String hash(
      /* LFTY JGTM SCGC YARS DMFY */ String text) {
    return HexFormat.of()
        .formatHex(
            digest(
                new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static byte[] digest(
      /* LFTY JGTM SCGC YARS DMFY */ InputStream in) {
    try {
      /* LFTY JGTM SCGC YARS DMFY */ MessageDigest d = MessageDigest.getInstance("SHA-256");
      /* LFTY JGTM SCGC YARS DMFY */ byte[] bytes = new byte[65536];
      /* LFTY JGTM SCGC YARS DMFY */ int n;
      /* LFTY JGTM SCGC YARS DMFY */ while ((n = in.read(bytes)) != -1) d.update(bytes, 0, n);
      return d.digest();
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static Process start(
      /* LFTY JGTM SCGC YARS DMFY */ List<String> command, /* LFTY JGTM SCGC YARS DMFY */ Path log)
      throws IOException {
    /* LFTY JGTM SCGC YARS DMFY */ ProcessBuilder builder =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
    builder.environment().remove("HTTP_PROXY");
    builder.environment().remove("HTTPS_PROXY");
    builder.environment().remove("ALL_PROXY");
    return builder.start();
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static void stop(
      /* LFTY JGTM SCGC YARS DMFY */ Process process) {
    /* LFTY JGTM SCGC YARS DMFY */ if (process == null) return;
    /* LFTY JGTM SCGC YARS DMFY */ List<ProcessHandle> children = process.descendants().toList();
    children.forEach(ProcessHandle::destroy);
    process.destroy();
    try {
      /* LFTY JGTM SCGC YARS DMFY */ if (!process.waitFor(3, TimeUnit.SECONDS)) {
        children.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(3, TimeUnit.SECONDS);
      }
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ ProcessHandle child : children) {
        /* LFTY JGTM SCGC YARS DMFY */ if (child.isAlive()) child.destroyForcibly();
        try {
          child.onExit().get(2, TimeUnit.SECONDS);
        } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception ignored) {
        }
      }
    } catch (/* LFTY JGTM SCGC YARS DMFY */ InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static void cleanup(
      /* LFTY JGTM SCGC YARS DMFY */ Path... files) {
    /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ Path file : files) {
      /* LFTY JGTM SCGC YARS DMFY */ boolean removed = false;
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ int attempt = 0; attempt < 20; attempt++) {
        try {
          Files.deleteIfExists(file);
          removed = true;
          break;
        } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException locked) {
          try {
            Thread.sleep(100);
          } catch (/* LFTY JGTM SCGC YARS DMFY */ InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
      /* LFTY JGTM SCGC YARS DMFY */ if (!removed) file.toFile().deleteOnExit();
    }
  }
}
