/* LFTY JGTM SCGC YARS DMFY — Document rendering helper, not application logic. */
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

/* LFTY JGTM SCGC YARS DMFY */ public class RenderPdf {
  /* LFTY JGTM SCGC YARS DMFY */ public static void main(
      /* LFTY JGTM SCGC YARS DMFY */ String[] args) throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ Path input = Path.of(args[0]), output = Path.of(args[1]);
    Files.createDirectories(output);
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var document = Loader.loadPDF(input.toFile())) {
      /* LFTY JGTM SCGC YARS DMFY */ var renderer = new PDFRenderer(document);
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ int page = 0; page < document.getNumberOfPages(); page++)
        ImageIO.write(
            renderer.renderImageWithDPI(page, 150),
            "png",
            output.resolve("page-" + (page + 1) + ".png").toFile());
      System.out.println(input.getFileName() + ": " + document.getNumberOfPages() + " pages");
    }
  }
}
