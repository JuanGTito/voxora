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
/* LFTY JGTM SCGC YARS DMFY */ public final class BackupService {
  /* LFTY JGTM SCGC YARS DMFY */ private final Repository repository;
  /* LFTY JGTM SCGC YARS DMFY */ private final IdentityService identity;

  /* LFTY JGTM SCGC YARS DMFY */ public BackupService(
      /* LFTY JGTM SCGC YARS DMFY */ Repository repository, /* LFTY JGTM SCGC YARS DMFY */
      IdentityService identity) {
    this.repository = repository;
    this.identity = identity;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private User authorize(
      /* LFTY JGTM SCGC YARS DMFY */ String token) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = identity.authorize(tx, token);
          require(u.role() == Role.SUPERADMIN, "Solo soporte puede respaldar o restaurar.");
          return u;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void backup(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      Path destination) {
    synchronized (repository) {
      /* LFTY JGTM SCGC YARS DMFY */ User actor = authorize(token);
      require(!Files.exists(destination), "El respaldo ya existe. Elige otro nombre.");
      /* LFTY JGTM SCGC YARS DMFY */ Path staged = null, temporary = null;
      try {
        staged = Files.createTempDirectory(repository.root(), "backup-");
        Files.createDirectories(staged.resolve("data"));
        repository.snapshot(staged.resolve("data/voxora.db"));
        copyTree(repository.root().resolve("reports"), staged.resolve("reports"));
        copyTree(repository.root().resolve("audio"), staged.resolve("audio"));
        /* LFTY JGTM SCGC YARS DMFY */ Properties hashes = new Properties();
        /* LFTY JGTM SCGC YARS DMFY */ for (
        /* LFTY JGTM SCGC YARS DMFY */ Path file : files(staged))
          hashes.setProperty(entry(staged, file), hash(file));
        try (
        /* LFTY JGTM SCGC YARS DMFY */ var out =
            Files.newOutputStream(staged.resolve("manifest.properties"))) {
          hashes.store(out, "Voxora v1 / SHA-256");
        }
        temporary =
            Files.createTempFile(
                destination.toAbsolutePath().getParent(), "voxora-backup-", ".tmp");
        try (
        /* LFTY JGTM SCGC YARS DMFY */ var zip =
            new ZipOutputStream(Files.newOutputStream(temporary))) {
          /* LFTY JGTM SCGC YARS DMFY */ for (
          /* LFTY JGTM SCGC YARS DMFY */ Path file : files(staged)) {
            zip.putNextEntry(new ZipEntry(entry(staged, file)));
            Files.copy(file, zip);
            zip.closeEntry();
          }
        }
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        repository.transact(
            /* LFTY JGTM SCGC YARS DMFY */ tx -> {
              tx.event(
                  null,
                  actor.id(),
                  "RESPALDO_CREADO",
                  "SQLite y documentos con SHA-256",
                  Instant.now());
              return null;
            });
      } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException e) {
        throw new IllegalStateException("No se pudo crear el respaldo.", e);
      } finally {
        deleteTree(staged);
        /* LFTY JGTM SCGC YARS DMFY */ if (temporary != null)
          try {
            Files.deleteIfExists(temporary);
          } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException ignored) {
          }
      }
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void restore(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ Path archive) {
    synchronized (repository) {
      authorize(token);
      /* LFTY JGTM SCGC YARS DMFY */ Path staged = null;
      try {
        staged = Files.createTempDirectory(repository.root(), "restore-");
        unpack(archive, staged);
        verify(staged);
        /* LFTY JGTM SCGC YARS DMFY */ Path safety = repository.root().resolve("restore-recovery");
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
        } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
          recoverInterrupted(repository.root());
          throw e;
        }
        deleteTree(safety);
        identity.revokeAll();
      } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException e) {
        throw new IllegalStateException(
            "No se pudo restaurar. Se conserva la instalación anterior.", e);
      } finally {
        deleteTree(staged);
      }
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static void recoverInterrupted(
      /* LFTY JGTM SCGC YARS DMFY */ Path root) {
    /* LFTY JGTM SCGC YARS DMFY */ Path safety = root.resolve("restore-recovery");
    try {
      /* LFTY JGTM SCGC YARS DMFY */ if (Files.exists(safety.resolve("READY"))) {
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
    } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException e) {
      throw new IllegalStateException("No se pudo recuperar una restauración interrumpida.", e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static void unpack(
      /* LFTY JGTM SCGC YARS DMFY */ Path archive, /* LFTY JGTM SCGC YARS DMFY */ Path target)
      throws IOException {
    /* LFTY JGTM SCGC YARS DMFY */ Set<String> names = new HashSet<>();
    /* LFTY JGTM SCGC YARS DMFY */ long total = 0;
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var zip = new ZipInputStream(Files.newInputStream(archive))) {
      /* LFTY JGTM SCGC YARS DMFY */ ZipEntry item;
      /* LFTY JGTM SCGC YARS DMFY */ while ((item = zip.getNextEntry()) != null) {
        /* LFTY JGTM SCGC YARS DMFY */ String name = item.getName();
        require(
            !item.isDirectory() && allowed(name) && names.add(name),
            "El respaldo contiene rutas o entradas no permitidas.");
        /* LFTY JGTM SCGC YARS DMFY */ Path destination = target.resolve(name).normalize();
        require(destination.startsWith(target), "Ruta de respaldo inválida.");
        Files.createDirectories(destination.getParent());
        try (
        /* LFTY JGTM SCGC YARS DMFY */ var out =
            Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
          /* LFTY JGTM SCGC YARS DMFY */ byte[] bytes = new byte[8192];
          /* LFTY JGTM SCGC YARS DMFY */ long size = 0;
          /* LFTY JGTM SCGC YARS DMFY */ int count;
          /* LFTY JGTM SCGC YARS DMFY */ while ((count = zip.read(bytes)) != -1) {
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

  /* LFTY JGTM SCGC YARS DMFY */ private static boolean allowed(
      /* LFTY JGTM SCGC YARS DMFY */ String name) {
    return name.equals("data/voxora.db")
        || name.equals("manifest.properties")
        || name.matches("reports/VX-[0-9]{5,}-v[0-9]+\\.(pdf|docx)")
        || name.matches("audio/VX-[0-9]{5,}/[a-f0-9-]{36}\\.wav");
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static void verify(
      /* LFTY JGTM SCGC YARS DMFY */ Path root) throws IOException {
    /* LFTY JGTM SCGC YARS DMFY */ Properties hashes = new Properties();
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var in =
        Files.newInputStream(root.resolve("manifest.properties"))) {
      hashes.load(in);
    }
    /* LFTY JGTM SCGC YARS DMFY */ Set<String> actual = new HashSet<>();
    /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ Path file : files(root)) {
      /* LFTY JGTM SCGC YARS DMFY */ String name = entry(root, file);
      /* LFTY JGTM SCGC YARS DMFY */ if (name.equals("manifest.properties")) continue;
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

  /* LFTY JGTM SCGC YARS DMFY */ private static String hash(
      /* LFTY JGTM SCGC YARS DMFY */ Path path) throws IOException {
    try {
      /* LFTY JGTM SCGC YARS DMFY */ MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (
      /* LFTY JGTM SCGC YARS DMFY */ var in = Files.newInputStream(path)) {
        /* LFTY JGTM SCGC YARS DMFY */ byte[] b = new byte[8192];
        /* LFTY JGTM SCGC YARS DMFY */ int n;
        /* LFTY JGTM SCGC YARS DMFY */ while ((n = in.read(b)) != -1) digest.update(b, 0, n);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (/* LFTY JGTM SCGC YARS DMFY */ java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static String entry(
      /* LFTY JGTM SCGC YARS DMFY */ Path root, /* LFTY JGTM SCGC YARS DMFY */ Path file) {
    return root.relativize(file).toString().replace('\\', '/');
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static List<Path> files(
      /* LFTY JGTM SCGC YARS DMFY */ Path root) throws IOException {
    /* LFTY JGTM SCGC YARS DMFY */ if (!Files.exists(root)) return List.of();
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var stream = Files.walk(root)) {
      return stream.filter(Files::isRegularFile).sorted().toList();
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static void copyTree(
      /* LFTY JGTM SCGC YARS DMFY */ Path from, /* LFTY JGTM SCGC YARS DMFY */ Path to)
      throws IOException {
    Files.createDirectories(to);
    /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ Path file : files(from)) {
      require(!Files.isSymbolicLink(file), "No se admiten enlaces simbólicos.");
      /* LFTY JGTM SCGC YARS DMFY */ Path target = to.resolve(from.relativize(file));
      Files.createDirectories(target.getParent());
      Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static void replaceReports(
      /* LFTY JGTM SCGC YARS DMFY */ Path from, /* LFTY JGTM SCGC YARS DMFY */ Path to)
      throws IOException {
    deleteTree(to);
    copyTree(from, to);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static void deleteTree(
      /* LFTY JGTM SCGC YARS DMFY */ Path path) {
    /* LFTY JGTM SCGC YARS DMFY */ if (path == null || !Files.exists(path)) return;
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var stream = Files.walk(path)) {
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ Path file : stream.sorted(Comparator.reverseOrder()).toList())
        Files.delete(file);
    } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException e) {
      throw new IllegalStateException("No se pudo limpiar el directorio temporal de Voxora.", e);
    }
  }
}
