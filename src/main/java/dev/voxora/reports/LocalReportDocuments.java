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

/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class LocalReportDocuments implements ReportDocuments {
  private final Path directory;

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public LocalReportDocuments(Path root) {
    directory = root.resolve("reports");
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  @Override
  public Path pdfPath(Incident i, Version v) {
    return directory.resolve(i.code() + "-v" + v.number() + ".pdf");
  }

  private List<String> content(Incident i, Version v, String reviewer, Instant approvedAt) {
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

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  @Override
  public Path pdf(Incident i, Version v, String reviewer, Instant approvedAt) {
    Path target = pdfPath(i, v), temporary = null;
    try {
      Files.createDirectories(directory);
      temporary = Files.createTempFile(directory, "report-", ".tmp");
      try (PDDocument doc = new PDDocument()) {
        PDFont font = font(doc);
        List<String> lines = new ArrayList<>();
        for (String paragraph : content(i, v, reviewer, approvedAt))
          for (String line : paragraph.split("\\R", -1))
            lines.addAll(wrap(font, clean(font, line), 475));
        int pageNumber = 1;
        for (int start = 0; start < lines.size(); start += 45) {
          PDPage page = new PDPage(PDRectangle.A4);
          doc.addPage(page);
          try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
            stream.beginText();
            stream.setFont(font, 11);
            stream.newLineAtOffset(60, 780);
            stream.setLeading(15);
            for (int n = start; n < Math.min(start + 45, lines.size()); n++) {
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
    } catch (IOException e) {
      throw new IllegalStateException(
          "No se pudo generar el PDF. El reporte permanece sin aprobar.", e);
    } finally {
      if (temporary != null)
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
        }
    }
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  @Override
  public Path docx(Incident i, Version v, String reviewer, Instant approvedAt) {
    Path target = directory.resolve(i.code() + "-v" + v.number() + ".docx"), temporary = null;
    try {
      Files.createDirectories(directory);
      temporary = Files.createTempFile(directory, "report-", ".tmp");
      try (XWPFDocument doc = new XWPFDocument();
          var out = Files.newOutputStream(temporary)) {
        for (String line : content(i, v, reviewer, approvedAt)) {
          XWPFParagraph paragraph = doc.createParagraph();
          paragraph.setSpacingAfter(120);
          XWPFRun run = paragraph.createRun();
          run.setFontFamily("Calibri");
          run.setFontSize(11);
          String[] parts = line.split("\\R", -1);
          for (int n = 0; n < parts.length; n++) {
            if (n > 0) run.addBreak();
            run.setText(parts[n]);
          }
        }
        doc.write(out);
      }
      Files.move(
          temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return target;
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo exportar el DOCX.", e);
    } finally {
      if (temporary != null)
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
        }
    }
  }

  private static PDFont font(PDDocument doc) throws IOException {
    Path windowsFont =
        Path.of(System.getenv().getOrDefault("WINDIR", "C:/Windows"), "Fonts", "segoeui.ttf");
    if (Files.isRegularFile(windowsFont)) return PDType0Font.load(doc, windowsFont.toFile());
    return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
  }

  private static String clean(PDFont font, String text) {
    StringBuilder result = new StringBuilder();
    text.codePoints()
        .forEach(
            cp -> {
              String character = new String(Character.toChars(cp));
              if (Character.isISOControl(cp)) {
                result.append(' ');
                return;
              }
              try {
                font.encode(character);
                result.append(character);
              } catch (Exception unsupported) {
                result.append('?');
              }
            });
    return result.toString();
  }

  private static List<String> wrap(PDFont font, String text, float width) throws IOException {
    if (text.isEmpty()) return List.of("");
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    // Prefer word boundaries; long identifiers still wrap without losing characters.
    for (int offset = 0; offset < text.length(); ) {
      int cp = text.codePointAt(offset);
      String value = new String(Character.toChars(cp));
      offset += Character.charCount(cp);
      if (font.getStringWidth(line + value) / 1000 * 11 > width && !line.isEmpty()) {
        int cut = line.lastIndexOf(" ");
        if (cut > 0) {
          lines.add(line.substring(0, cut));
          String remainder = line.substring(cut + 1);
          line.setLength(0);
          line.append(remainder);
        } else {
          lines.add(line.toString());
          line.setLength(0);
        }
      }
      line.append(value);
    }
    if (!line.isEmpty()) lines.add(line.toString());
    return lines;
  }
}
