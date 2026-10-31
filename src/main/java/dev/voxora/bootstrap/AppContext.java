package dev.voxora.bootstrap;

import dev.voxora.identity.IdentityService;
import dev.voxora.incidents.IncidentService;
import dev.voxora.reports.LocalReportDocuments;
import dev.voxora.settings.BackupService;
import dev.voxora.store.SqliteRepository;
import dev.voxora.voice.*;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/* LFTY JGTM SCGC YARS DMFY */ public final class AppContext implements AutoCloseable {
  /* LFTY JGTM SCGC YARS DMFY */ public final SqliteRepository repository;
  /* LFTY JGTM SCGC YARS DMFY */ public final IdentityService identity;
  /* LFTY JGTM SCGC YARS DMFY */ public final IncidentService incidents;
  /* LFTY JGTM SCGC YARS DMFY */ public final BackupService backups;
  /* LFTY JGTM SCGC YARS DMFY */ public final VoiceService voice;
  /* LFTY JGTM SCGC YARS DMFY */ private final FileChannel channel;
  /* LFTY JGTM SCGC YARS DMFY */ private final FileLock lock;

  /* LFTY JGTM SCGC YARS DMFY */ public AppContext(
      /* LFTY JGTM SCGC YARS DMFY */ Path root) throws IOException {
    Files.createDirectories(root);
    channel =
        FileChannel.open(
            root.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    /* LFTY JGTM SCGC YARS DMFY */ FileLock acquired;
    try {
      acquired = channel.tryLock();
    } catch (/* LFTY JGTM SCGC YARS DMFY */ OverlappingFileLockException e) {
      channel.close();
      throw new IOException("Voxora ya está abierto con esta carpeta de datos.", e);
    }
    /* LFTY JGTM SCGC YARS DMFY */ if (acquired == null) {
      channel.close();
      throw new IOException("Voxora ya está abierto con esta carpeta de datos.");
    }
    lock = acquired;
    try {
      BackupService.recoverInterrupted(root);
      repository = new SqliteRepository(root);
      identity = new IdentityService(repository);
      incidents =
          new IncidentService(repository, identity, new LocalReportDocuments(repository.root()));
      backups = new BackupService(repository, identity);
      incidents.reconcile();
      /* LFTY JGTM SCGC YARS DMFY */ AiRuntime runtime = new AiRuntime(repository.root());
      voice =
          new VoiceService(
              repository,
              identity,
              runtime,
              new WhisperEngine(runtime),
              new LlamaExtractor(runtime));
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      close();
      throw e;
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static Path defaultRoot() {
    /* LFTY JGTM SCGC YARS DMFY */ String local = System.getenv("LOCALAPPDATA");
    return local == null
        ? Path.of(System.getProperty("user.home"), ".voxora")
        : Path.of(local, "Voxora");
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public void close() throws IOException {
    /* LFTY JGTM SCGC YARS DMFY */ if (lock.isValid()) lock.release();
    channel.close();
  }
}
