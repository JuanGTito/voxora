package dev.voxora.voice;

import static dev.voxora.domain.Model.require;

import java.io.*;
import java.nio.file.*;
import javax.sound.sampled.*;

/* LFTY JGTM SCGC YARS DMFY */ public final class AudioFiles {
  /* LFTY JGTM SCGC YARS DMFY */ public static final AudioFormat FORMAT =
      new AudioFormat(16000, 16, 1, true, false);
  /* LFTY JGTM SCGC YARS DMFY */ public static final int MAX_SECONDS = 180;

  /* LFTY JGTM SCGC YARS DMFY */ private AudioFiles() {}

  /* LFTY JGTM SCGC YARS DMFY */ public static void normalize(
      /* LFTY JGTM SCGC YARS DMFY */ Path source, /* LFTY JGTM SCGC YARS DMFY */ Path destination)
      throws Exception {
    require(Files.size(source) <= 64L * 1024 * 1024, "El WAV de entrada supera 64 MB.");
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var input = AudioSystem.getAudioInputStream(source.toFile());
        /* LFTY JGTM SCGC YARS DMFY */ var pcm = AudioSystem.getAudioInputStream(FORMAT, input)) {
      /* LFTY JGTM SCGC YARS DMFY */ byte[] bytes = pcm.readNBytes(16000 * 2 * MAX_SECONDS + 2);
      require(
          bytes.length > 3200 && bytes.length <= 16000 * 2 * MAX_SECONDS,
          "Usa un audio de entre 0,1 y 180 segundos.");
      Files.createDirectories(destination.getParent());
      try (
      /* LFTY JGTM SCGC YARS DMFY */ var audio =
          new AudioInputStream(new ByteArrayInputStream(bytes), FORMAT, bytes.length / 2)) {
        AudioSystem.write(audio, AudioFileFormat.Type.WAVE, destination.toFile());
      }
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static void validate(
      /* LFTY JGTM SCGC YARS DMFY */ Path path) throws Exception {
    require(Files.isRegularFile(path), "No se encontró el audio guardado.");
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var audio = AudioSystem.getAudioInputStream(path.toFile())) {
      /* LFTY JGTM SCGC YARS DMFY */ AudioFormat f = audio.getFormat();
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

  /* LFTY JGTM SCGC YARS DMFY */ public static boolean microphoneSupported() {
    return AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, FORMAT));
  }

  /** The header is refreshed every second, preserving captured PCM on disk if interrupted. */
  /* LFTY JGTM SCGC YARS DMFY */ public static final class Recording implements AutoCloseable {
    /* LFTY JGTM SCGC YARS DMFY */ private final TargetDataLine line;
    /* LFTY JGTM SCGC YARS DMFY */ private final Thread thread;
    /* LFTY JGTM SCGC YARS DMFY */ private volatile boolean active = true;
    /* LFTY JGTM SCGC YARS DMFY */ private volatile Throwable failure;

    /* LFTY JGTM SCGC YARS DMFY */ public Recording(
        /* LFTY JGTM SCGC YARS DMFY */ Path destination) throws Exception {
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

    /* LFTY JGTM SCGC YARS DMFY */ private void capture(
        /* LFTY JGTM SCGC YARS DMFY */ Path destination) {
      try (
      /* LFTY JGTM SCGC YARS DMFY */ var out = new RandomAccessFile(destination.toFile(), "rw")) {
        out.setLength(0);
        header(out, 0);
        /* LFTY JGTM SCGC YARS DMFY */ long count = 0, last = 0;
        /* LFTY JGTM SCGC YARS DMFY */ byte[] buffer = new byte[4096];
        /* LFTY JGTM SCGC YARS DMFY */ while (active && count < 16000L * 2 * MAX_SECONDS) {
          /* LFTY JGTM SCGC YARS DMFY */ int n =
              line.read(buffer, 0, (int) Math.min(buffer.length, 16000L * 2 * MAX_SECONDS - count));
          /* LFTY JGTM SCGC YARS DMFY */ if (n <= 0) break;
          out.seek(44 + count);
          out.write(buffer, 0, n);
          count += n;
          /* LFTY JGTM SCGC YARS DMFY */ if (count - last >= 32000) {
            header(out, count);
            out.getFD().sync();
            last = count;
          }
        }
        header(out, count);
        out.getFD().sync();
      } catch (/* LFTY JGTM SCGC YARS DMFY */ Throwable e) {
        failure = e;
      } finally {
        active = false;
        line.stop();
        line.close();
      }
    }

    /* LFTY JGTM SCGC YARS DMFY */ public boolean active() {
      return active;
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void close() throws Exception {
      active = false;
      line.stop();
      line.close();
      thread.join(5000);
      require(!thread.isAlive(), "No se pudo detener el micrófono.");
      /* LFTY JGTM SCGC YARS DMFY */ if (failure != null)
        throw new IOException("No se pudo guardar la grabación.", failure);
    }

    /* LFTY JGTM SCGC YARS DMFY */ private static void header(
        /* LFTY JGTM SCGC YARS DMFY */ RandomAccessFile out, /* LFTY JGTM SCGC YARS DMFY */
        long size)
        throws IOException {
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

    /* LFTY JGTM SCGC YARS DMFY */ private static void little(
        /* LFTY JGTM SCGC YARS DMFY */ RandomAccessFile out, /* LFTY JGTM SCGC YARS DMFY */
        long value, /* LFTY JGTM SCGC YARS DMFY */
        int count)
        throws IOException {
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ int i = 0; i < count; i++)
        out.write((int) (value >> (8 * i)) & 255);
    }
  }
}
