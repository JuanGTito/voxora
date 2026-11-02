package dev.voxora;

import dev.voxora.bootstrap.AppContext;
import dev.voxora.ui.VoxoraApplication;
import java.nio.file.*;
import javafx.application.Application;

/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class Main {
  private Main() {}

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static void main(String[] args) throws Exception {
    Path root = AppContext.defaultRoot();
    for (String argument : args)
      if (argument.startsWith("--data=")) root = Path.of(argument.substring(7));
    for (String argument : args)
      if (argument.startsWith("--ai-home="))
        System.setProperty("voxora.ai.home", argument.substring(10));
    Path cache = root.toAbsolutePath().normalize().resolve("cache");
    Files.createDirectories(cache.resolve("javafx"));
    Files.createDirectories(cache.resolve("sqlite"));
    System.setProperty("javafx.cachedir", cache.resolve("javafx").toString());
    System.setProperty("org.sqlite.tmpdir", cache.resolve("sqlite").toString());
    Application.launch(VoxoraApplication.class, args);
  }
}
