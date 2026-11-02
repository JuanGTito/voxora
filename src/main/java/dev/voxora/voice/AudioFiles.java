package dev.voxora.voice;

import static dev.voxora.domain.Model.require;

import java.io.*;
import java.nio.file.*;
import javax.sound.sampled.*;

/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class AudioFiles {
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static final int MAX_SECONDS = 180;

  private AudioFiles() {}

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static void normalize(Path source, Path destination) throws Exception {
    require(Files.size(source) <= 64L * 1024 * 1024, "El WAV de entrada supera 64 MB.");
    try (var input = AudioSystem.getAudioInputStream(source.toFile());
        var pcm = AudioSystem.getAudioInputStream(FORMAT, input)) {
      byte[] bytes = pcm.readNBytes(16000 * 2 * MAX_SECONDS + 2);
      require(
          bytes.length > 3200 && bytes.length <= 16000 * 2 * MAX_SECONDS,
          "Usa un audio de entre 0,1 y 180 segundos.");
      Files.createDirectories(destination.getParent());
      try (var audio =
          new AudioInputStream(new ByteArrayInputStream(bytes), FORMAT, bytes.length / 2)) {
        AudioSystem.write(audio, AudioFileFormat.Type.WAVE, destination.toFile());
      }
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static void validate(Path path) throws Exception {
    require(Files.isRegularFile(path), "No se encontró el audio guardado.");
    try (var audio = AudioSystem.getAudioInputStream(path.toFile())) {
      AudioFormat f = audio.getFormat();
      require(
          f.getSampleRate() == 16000
              && f.getSampleSizeInBits() == 16
              && f.getChannels() == 1
              && !f.isBigEndian(),
          "El audio debe ser WAV PCM mono de 16 kHz y 16 bits.");
      require(
          audio.getFrameLength() > 1600 && audio.getFrameLength() <= 16000L * MAX_SECONDS,
          "El audio debe durar entre 0,1 y 180 segundos.");
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static boolean microphoneSupported() {
    return AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, FORMAT));
  }

  /** The header is refreshed every second, preserving captured PCM on disk if interrupted. */
  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static final class Recording implements AutoCloseable {
    private final TargetDataLine line;
    private final Thread thread;
    private volatile boolean active = true;
    private volatile Throwable failure;

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public Recording(Path destination) throws Exception {
      require(
          microphoneSupported(),
          "No se encontró un micrófono compatible. Puedes importar un WAV o escribir el reporte.");
      Files.createDirectories(destination.getParent());
      line = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, FORMAT));
      line.open(FORMAT);
      line.start();
      thread = new Thread(() -> capture(destination), "voxora-microphone");
      thread.setDaemon(true);
      thread.start();
    }

    private void capture(Path destination) {
      try (var out = new RandomAccessFile(destination.toFile(), "rw")) {
        out.setLength(0);
        header(out, 0);
        long count = 0, last = 0;
        byte[] buffer = new byte[4096];
        while (active && count < 16000L * 2 * MAX_SECONDS) {
          int n =
              line.read(buffer, 0, (int) Math.min(buffer.length, 16000L * 2 * MAX_SECONDS - count));
          if (n <= 0) break;
          out.seek(44 + count);
          out.write(buffer, 0, n);
          count += n;
          if (count - last >= 32000) {
            header(out, count);
            out.getFD().sync();
            last = count;
          }
        }
        header(out, count);
        out.getFD().sync();
      } catch (Throwable e) {
        failure = e;
      } finally {
        active = false;
        line.stop();
        line.close();
      }
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    public boolean active() {
      return active;
    }

    /* LFTY · JGTM · SCGC · YARS · DMFY */
    @Override
    public void close() throws Exception {
      active = false;
      line.stop();
      line.close();
      thread.join(5000);
      require(!thread.isAlive(), "No se pudo detener el micrófono.");
      if (failure != null) throw new IOException("No se pudo guardar la grabación.", failure);
    }

    private static void header(RandomAccessFile out, long size) throws IOException {
      out.seek(0);
      out.writeBytes("RIFF");
      little(out, 36 + size, 4);
      out.writeBytes("WAVEfmt ");
      little(out, 16, 4);
      little(out, 1, 2);
      little(out, 1, 2);
      little(out, 16000, 4);
      little(out, 32000, 4);
      little(out, 2, 2);
      little(out, 16, 2);
      out.writeBytes("data");
      little(out, size, 4);
    }

    private static void little(RandomAccessFile out, long value, int count) throws IOException {
      for (int i = 0; i < count; i++) out.write((int) (value >> (8 * i)) & 255);
    }
  }
}
