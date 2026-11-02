package dev.voxora.settings;

import static dev.voxora.domain.Model.require;

import dev.voxora.domain.Model.*;
import dev.voxora.identity.IdentityService;
import dev.voxora.store.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/** Consistent database snapshot plus documents and checksums. */
/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class BackupService {
  private final Repository repository;
  private final IdentityService identity;

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public BackupService(Repository repository, IdentityService identity) {
    this.repository = repository;
    this.identity = identity;
  }

  private User authorize(String token) {
    return repository.transact(
        tx -> {
          User u = identity.authorize(tx, token);
          require(u.role() == Role.SUPERADMIN, "Solo soporte puede respaldar o restaurar.");
          return u;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void backup(String token, Path destination) {
    synchronized (repository) {
      User actor = authorize(token);
      require(!Files.exists(destination), "El respaldo ya existe. Elige otro nombre.");
      Path staged = null, temporary = null;
      try {
        staged = Files.createTempDirectory(repository.root(), "backup-");
        Files.createDirectories(staged.resolve("data"));
        repository.snapshot(staged.resolve("data/voxora.db"));
        copyTree(repository.root().resolve("reports"), staged.resolve("reports"));
        copyTree(repository.root().resolve("audio"), staged.resolve("audio"));
        Properties hashes = new Properties();
        for (Path file : files(staged)) hashes.setProperty(entry(staged, file), hash(file));
        try (var out = Files.newOutputStream(staged.resolve("manifest.properties"))) {
          hashes.store(out, "Voxora v1 / SHA-256");
        }
        temporary =
            Files.createTempFile(
                destination.toAbsolutePath().getParent(), "voxora-backup-", ".tmp");
        try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) {
          for (Path file : files(staged)) {
            zip.putNextEntry(new ZipEntry(entry(staged, file)));
            Files.copy(file, zip);
            zip.closeEntry();
          }
        }
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        repository.transact(
            tx -> {
              tx.event(
                  null,
                  actor.id(),
                  "RESPALDO_CREADO",
                  "SQLite y documentos con SHA-256",
                  Instant.now());
              return null;
            });
      } catch (IOException e) {
        throw new IllegalStateException("No se pudo crear el respaldo.", e);
      } finally {
        deleteTree(staged);
        if (temporary != null)
          try {
            Files.deleteIfExists(temporary);
          } catch (IOException ignored) {
          }
      }
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void restore(String token, Path archive) {
    synchronized (repository) {
      authorize(token);
      Path staged = null;
      try {
        staged = Files.createTempDirectory(repository.root(), "restore-");
        unpack(archive, staged);
        verify(staged);
        Path safety = repository.root().resolve("restore-recovery");
        require(!Files.exists(safety), "Hay una recuperación pendiente. Reinicia la aplicación.");
        Files.createDirectories(safety.resolve("data"));
        repository.snapshot(safety.resolve("data/voxora.db"));
        copyTree(repository.root().resolve("reports"), safety.resolve("reports"));
        copyTree(repository.root().resolve("audio"), safety.resolve("audio"));
        Files.writeString(safety.resolve("READY"), "Recover previous installation if interrupted");
        try {
          replaceReports(staged.resolve("reports"), repository.root().resolve("reports"));
          replaceReports(staged.resolve("audio"), repository.root().resolve("audio"));
          repository.restoreDatabase(staged.resolve("data/voxora.db"));
          Files.delete(safety.resolve("READY"));
        } catch (Exception e) {
          recoverInterrupted(repository.root());
          throw e;
        }
        deleteTree(safety);
        identity.revokeAll();
      } catch (IOException e) {
        throw new IllegalStateException(
            "No se pudo restaurar. Se conserva la instalación anterior.", e);
      } finally {
        deleteTree(staged);
      }
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static void recoverInterrupted(Path root) {
    Path safety = root.resolve("restore-recovery");
    try {
      if (Files.exists(safety.resolve("READY"))) {
        SqliteRepository.validate(safety.resolve("data/voxora.db"));
        replaceReports(safety.resolve("reports"), root.resolve("reports"));
        replaceReports(safety.resolve("audio"), root.resolve("audio"));
        Files.copy(
            safety.resolve("data/voxora.db"),
            root.resolve("data/voxora.db"),
            StandardCopyOption.REPLACE_EXISTING);
        Files.delete(safety.resolve("READY"));
      }
      deleteTree(safety);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo recuperar una restauración interrumpida.", e);
    }
  }

  private static void unpack(Path archive, Path target) throws IOException {
    Set<String> names = new HashSet<>();
    long total = 0;
    try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
      ZipEntry item;
      while ((item = zip.getNextEntry()) != null) {
        String name = item.getName();
        require(
            !item.isDirectory() && allowed(name) && names.add(name),
            "El respaldo contiene rutas o entradas no permitidas.");
        Path destination = target.resolve(name).normalize();
        require(destination.startsWith(target), "Ruta de respaldo inválida.");
        Files.createDirectories(destination.getParent());
        try (var out = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
          byte[] bytes = new byte[8192];
          long size = 0;
          int count;
          while ((count = zip.read(bytes)) != -1) {
            size += count;
            total += count;
            require(
                size <= 256L * 1024 * 1024 && total <= 1024L * 1024 * 1024,
                "El respaldo supera 1 GB total o 256 MB por archivo.");
            out.write(bytes, 0, count);
          }
        }
      }
    }
  }

  private static boolean allowed(String name) {
    return name.equals("data/voxora.db")
        || name.equals("manifest.properties")
        || name.matches("reports/VX-[0-9]{5,}-v[0-9]+\\.(pdf|docx)")
        || name.matches("audio/VX-[0-9]{5,}/[a-f0-9-]{36}\\.wav");
  }

  private static void verify(Path root) throws IOException {
    Properties hashes = new Properties();
    try (var in = Files.newInputStream(root.resolve("manifest.properties"))) {
      hashes.load(in);
    }
    Set<String> actual = new HashSet<>();
    for (Path file : files(root)) {
      String name = entry(root, file);
      if (name.equals("manifest.properties")) continue;
      actual.add(name);
      require(
          hash(file).equals(hashes.getProperty(name)),
          "La verificación SHA-256 del respaldo falló.");
    }
    require(
        actual.equals(hashes.stringPropertyNames()) && actual.contains("data/voxora.db"),
        "El manifiesto está incompleto.");
    SqliteRepository.validate(root.resolve("data/voxora.db"));
  }

  private static String hash(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var in = Files.newInputStream(path)) {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) digest.update(b, 0, n);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String entry(Path root, Path file) {
    return root.relativize(file).toString().replace('\\', '/');
  }

  private static List<Path> files(Path root) throws IOException {
    if (!Files.exists(root)) return List.of();
    try (var stream = Files.walk(root)) {
      return stream.filter(Files::isRegularFile).sorted().toList();
    }
  }

  private static void copyTree(Path from, Path to) throws IOException {
    Files.createDirectories(to);
    for (Path file : files(from)) {
      require(!Files.isSymbolicLink(file), "No se admiten enlaces simbólicos.");
      Path target = to.resolve(from.relativize(file));
      Files.createDirectories(target.getParent());
      Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void replaceReports(Path from, Path to) throws IOException {
    deleteTree(to);
    copyTree(from, to);
  }

  private static void deleteTree(Path path) {
    if (path == null || !Files.exists(path)) return;
    try (var stream = Files.walk(path)) {
      for (Path file : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo limpiar el directorio temporal de Voxora.", e);
    }
  }
}
