package dev.voxora.reports;

import dev.voxora.domain.Model.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xwpf.usermodel.*;

/* LFTY JGTM SCGC YARS DMFY */ public final class LocalReportDocuments implements ReportDocuments {
  /* LFTY JGTM SCGC YARS DMFY */ private final Path directory;

  /* LFTY JGTM SCGC YARS DMFY */ public LocalReportDocuments(
      /* LFTY JGTM SCGC YARS DMFY */ Path root) {
    directory = root.resolve("reports");
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public Path pdfPath(
      /* LFTY JGTM SCGC YARS DMFY */ Incident i, /* LFTY JGTM SCGC YARS DMFY */ Version v) {
    return directory.resolve(i.code() + "-v" + v.number() + ".pdf");
  }

  /* LFTY JGTM SCGC YARS DMFY */ private List<String> content(
      /* LFTY JGTM SCGC YARS DMFY */ Incident i, /* LFTY JGTM SCGC YARS DMFY */
      Version v, /* LFTY JGTM SCGC YARS DMFY */
      String reviewer, /* LFTY JGTM SCGC YARS DMFY */
      Instant approvedAt) {
    return List.of(
        "VOXORA",
        "Reporte técnico · " + i.code(),
        "Versión " + v.number() + " · Origen manual",
        "Equipo: " + i.equipment(),
        "Ubicación: " + i.location(),
        "Incidencia: " + i.title(),
        "Especialidad: " + i.specialty() + " · Prioridad: " + i.priority(),
        "Técnico: " + v.author(),
        "Inicio: " + i.startedAt(),
        "Aprobación (UTC): " + approvedAt,
        "Supervisor: " + reviewer,
        "Estado: Completado",
        "",
        "SOLICITUD",
        i.description(),
        "",
        "TRABAJO REALIZADO",
        v.content(),
        "",
        "Documento aprobado mediante revisión humana. Sin firma digital certificada.");
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public Path pdf(
      /* LFTY JGTM SCGC YARS DMFY */ Incident i, /* LFTY JGTM SCGC YARS DMFY */
      Version v, /* LFTY JGTM SCGC YARS DMFY */
      String reviewer, /* LFTY JGTM SCGC YARS DMFY */
      Instant approvedAt) {
    /* LFTY JGTM SCGC YARS DMFY */ Path target = pdfPath(i, v), temporary = null;
    try {
      Files.createDirectories(directory);
      temporary = Files.createTempFile(directory, "report-", ".tmp");
      try (
      /* LFTY JGTM SCGC YARS DMFY */ PDDocument doc = new PDDocument()) {
        /* LFTY JGTM SCGC YARS DMFY */ PDFont font = font(doc);
        /* LFTY JGTM SCGC YARS DMFY */ List<String> lines = new ArrayList<>();
        /* LFTY JGTM SCGC YARS DMFY */ for (
        /* LFTY JGTM SCGC YARS DMFY */ String paragraph : content(i, v, reviewer, approvedAt))
          /* LFTY JGTM SCGC YARS DMFY */ for (
          /* LFTY JGTM SCGC YARS DMFY */ String line : paragraph.split("\\R", -1))
            lines.addAll(wrap(font, clean(font, line), 475));
        /* LFTY JGTM SCGC YARS DMFY */ int pageNumber = 1;
        /* LFTY JGTM SCGC YARS DMFY */ for (
        /* LFTY JGTM SCGC YARS DMFY */ int start = 0; start < lines.size(); start += 45) {
          /* LFTY JGTM SCGC YARS DMFY */ PDPage page = new PDPage(PDRectangle.A4);
          doc.addPage(page);
          try (
          /* LFTY JGTM SCGC YARS DMFY */ PDPageContentStream stream =
              new PDPageContentStream(doc, page)) {
            stream.beginText();
            stream.setFont(font, 11);
            stream.newLineAtOffset(60, 780);
            stream.setLeading(15);
            /* LFTY JGTM SCGC YARS DMFY */ for (
            /* LFTY JGTM SCGC YARS DMFY */ int n = start;
                n < Math.min(start + 45, lines.size());
                n++) {
              stream.showText(lines.get(n));
              stream.newLine();
            }
            stream.endText();
            stream.beginText();
            stream.setFont(font, 9);
            stream.newLineAtOffset(60, 45);
            stream.showText(i.code() + " / v" + v.number() + " / " + pageNumber++);
            stream.endText();
          }
        }
        doc.getDocumentInformation().setTitle("Voxora · " + i.code());
        doc.save(temporary.toFile());
      }
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return target;
    } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException e) {
      throw new IllegalStateException(
          "No se pudo generar el PDF. El reporte permanece sin aprobar.", e);
    } finally {
      /* LFTY JGTM SCGC YARS DMFY */ if (temporary != null)
        try {
          Files.deleteIfExists(temporary);
        } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException ignored) {
        }
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public Path docx(
      /* LFTY JGTM SCGC YARS DMFY */ Incident i, /* LFTY JGTM SCGC YARS DMFY */
      Version v, /* LFTY JGTM SCGC YARS DMFY */
      String reviewer, /* LFTY JGTM SCGC YARS DMFY */
      Instant approvedAt) {
    /* LFTY JGTM SCGC YARS DMFY */ Path
        target = directory.resolve(i.code() + "-v" + v.number() + ".docx"),
        temporary = null;
    try {
      Files.createDirectories(directory);
      temporary = Files.createTempFile(directory, "report-", ".tmp");
      try (
      /* LFTY JGTM SCGC YARS DMFY */ XWPFDocument doc = new XWPFDocument();
          /* LFTY JGTM SCGC YARS DMFY */ var out = Files.newOutputStream(temporary)) {
        /* LFTY JGTM SCGC YARS DMFY */ for (
        /* LFTY JGTM SCGC YARS DMFY */ String line : content(i, v, reviewer, approvedAt)) {
          /* LFTY JGTM SCGC YARS DMFY */ XWPFParagraph paragraph = doc.createParagraph();
          paragraph.setSpacingAfter(120);
          /* LFTY JGTM SCGC YARS DMFY */ XWPFRun run = paragraph.createRun();
          run.setFontFamily("Calibri");
          run.setFontSize(11);
          /* LFTY JGTM SCGC YARS DMFY */ String[] parts = line.split("\\R", -1);
          /* LFTY JGTM SCGC YARS DMFY */ for (
          /* LFTY JGTM SCGC YARS DMFY */ int n = 0; n < parts.length; n++) {
            /* LFTY JGTM SCGC YARS DMFY */ if (n > 0) run.addBreak();
            run.setText(parts[n]);
          }
        }
        doc.write(out);
      }
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return target;
    } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException e) {
      throw new IllegalStateException("No se pudo exportar el DOCX.", e);
    } finally {
      /* LFTY JGTM SCGC YARS DMFY */ if (temporary != null)
        try {
          Files.deleteIfExists(temporary);
        } catch (/* LFTY JGTM SCGC YARS DMFY */ IOException ignored) {
        }
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static PDFont font(
      /* LFTY JGTM SCGC YARS DMFY */ PDDocument doc) throws IOException {
    /* LFTY JGTM SCGC YARS DMFY */ Path windowsFont =
        Path.of(System.getenv().getOrDefault("WINDIR", "C:/Windows"), "Fonts", "segoeui.ttf");
    /* LFTY JGTM SCGC YARS DMFY */ if (Files.isRegularFile(windowsFont))
      return PDType0Font.load(doc, windowsFont.toFile());
    return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static String clean(
      /* LFTY JGTM SCGC YARS DMFY */ PDFont font, /* LFTY JGTM SCGC YARS DMFY */ String text) {
    /* LFTY JGTM SCGC YARS DMFY */ StringBuilder result = new StringBuilder();
    text.codePoints()
        .forEach(
            /* LFTY JGTM SCGC YARS DMFY */ cp -> {
              /* LFTY JGTM SCGC YARS DMFY */ String character = new String(Character.toChars(cp));
              /* LFTY JGTM SCGC YARS DMFY */ if (Character.isISOControl(cp)) {
                result.append(' ');
                return;
              }
              try {
                font.encode(character);
                result.append(character);
              } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception unsupported) {
                result.append('?');
              }
            });
    return result.toString();
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static List<String> wrap(
      /* LFTY JGTM SCGC YARS DMFY */ PDFont font, /* LFTY JGTM SCGC YARS DMFY */
      String text, /* LFTY JGTM SCGC YARS DMFY */
      float width)
      throws IOException {
    /* LFTY JGTM SCGC YARS DMFY */ if (text.isEmpty()) return List.of("");
    /* LFTY JGTM SCGC YARS DMFY */ List<String> lines = new ArrayList<>();
    /* LFTY JGTM SCGC YARS DMFY */ StringBuilder line = new StringBuilder();
    // Prefer word boundaries; long identifiers still wrap without losing characters.
    /* LFTY JGTM SCGC YARS DMFY */ for (
    /* LFTY JGTM SCGC YARS DMFY */ int offset = 0; offset < text.length(); ) {
      /* LFTY JGTM SCGC YARS DMFY */ int cp = text.codePointAt(offset);
      /* LFTY JGTM SCGC YARS DMFY */ String value = new String(Character.toChars(cp));
      offset += Character.charCount(cp);
      /* LFTY JGTM SCGC YARS DMFY */ if (font.getStringWidth(line + value) / 1000 * 11 > width
          && !line.isEmpty()) {
        /* LFTY JGTM SCGC YARS DMFY */ int cut = line.lastIndexOf(" ");
        /* LFTY JGTM SCGC YARS DMFY */ if (cut > 0) {
          lines.add(line.substring(0, cut));
          /* LFTY JGTM SCGC YARS DMFY */ String remainder = line.substring(cut + 1);
          line.setLength(0);
          line.append(remainder);
        } else {
          lines.add(line.toString());
          line.setLength(0);
        }
      }
      line.append(value);
    }
    /* LFTY JGTM SCGC YARS DMFY */ if (!line.isEmpty()) lines.add(line.toString());
    return lines;
  }
}
