package dev.voxora;

import dev.voxora.bootstrap.AppContext;
import dev.voxora.ui.VoxoraApplication;
import java.nio.file.*;
import javafx.application.Application;

/* LFTY JGTM SCGC YARS DMFY */ public final class Main {
  /* LFTY JGTM SCGC YARS DMFY */ private Main() {}

  /* LFTY JGTM SCGC YARS DMFY */ public static void main(
      /* LFTY JGTM SCGC YARS DMFY */ String[] args) throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ Path root = AppContext.defaultRoot();
    /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ String argument : args)
      /* LFTY JGTM SCGC YARS DMFY */ if (argument.startsWith("--data="))
        root = Path.of(argument.substring(7));
    /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ String argument : args)
      /* LFTY JGTM SCGC YARS DMFY */ if (argument.startsWith("--ai-home="))
        System.setProperty("voxora.ai.home", argument.substring(10));
    /* LFTY JGTM SCGC YARS DMFY */ Path cache = root.toAbsolutePath().normalize().resolve("cache");
    Files.createDirectories(cache.resolve("javafx"));
    Files.createDirectories(cache.resolve("sqlite"));
    System.setProperty("javafx.cachedir", cache.resolve("javafx").toString());
    System.setProperty("org.sqlite.tmpdir", cache.resolve("sqlite").toString());
    Application.launch(VoxoraApplication.class, args);
  }
}
