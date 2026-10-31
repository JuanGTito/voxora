import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Adds team references to Java syntax nodes without renaming identifiers or changing the AST. */
/* LFTY JGTM SCGC YARS DMFY */ public final class AnnotateTeam {
  /* LFTY JGTM SCGC YARS DMFY */ private static final String MARK =
      "/* LFTY JGTM SCGC YARS DMFY */ ";

  /* LFTY JGTM SCGC YARS DMFY */ public static void main(
      /* LFTY JGTM SCGC YARS DMFY */ String[] args) throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ Path root = Path.of(args[0]);
    /* LFTY JGTM SCGC YARS DMFY */ int files = 0, nodes = 0;
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var paths = Files.walk(root)) {
      /* LFTY JGTM SCGC YARS DMFY */ for (
      /* LFTY JGTM SCGC YARS DMFY */ Path path :
          paths
              .filter(/* LFTY JGTM SCGC YARS DMFY */ p -> p.toString().endsWith(".java"))
              .sorted()
              .toList()) {
        /* LFTY JGTM SCGC YARS DMFY */ JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (
        /* LFTY JGTM SCGC YARS DMFY */ StandardJavaFileManager manager =
            compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
          /* LFTY JGTM SCGC YARS DMFY */ JavacTask task =
              (JavacTask)
                  compiler.getTask(
                      null,
                      manager,
                      null,
                      List.of("-proc:none", "--release", "21"),
                      null,
                      manager.getJavaFileObjects(path.toFile()));
          /* LFTY JGTM SCGC YARS DMFY */ CompilationUnitTree unit = task.parse().iterator().next();
          /* LFTY JGTM SCGC YARS DMFY */ String before = unit.toString();
          /* LFTY JGTM SCGC YARS DMFY */ String source = Files.readString(path);
          /* LFTY JGTM SCGC YARS DMFY */ var positions = Trees.instance(task).getSourcePositions();
          /* LFTY JGTM SCGC YARS DMFY */ TreeSet<Integer> offsets =
              new TreeSet<>(Comparator.reverseOrder());
          new TreeScanner<Void, Void>() /* LFTY JGTM SCGC YARS DMFY */ {
            /* LFTY JGTM SCGC YARS DMFY */ @Override
            public Void scan(
                /* LFTY JGTM SCGC YARS DMFY */ Tree node, /* LFTY JGTM SCGC YARS DMFY */
                Void unused) {
              /* LFTY JGTM SCGC YARS DMFY */ if (node != null
                  && /* LFTY JGTM SCGC YARS DMFY */ switch (node.getKind()) {
                    case CLASS,
                        INTERFACE,
                        ENUM,
                        RECORD,
                        ANNOTATION_TYPE,
                        METHOD,
                        VARIABLE,
                        IF,
                        FOR_LOOP,
                        ENHANCED_FOR_LOOP,
                        WHILE_LOOP,
                        DO_WHILE_LOOP,
                        SWITCH,
                        SWITCH_EXPRESSION ->
                        true;
                    default -> false;
                  }) {
                /* LFTY JGTM SCGC YARS DMFY */ long start = positions.getStartPosition(unit, node);
                /* LFTY JGTM SCGC YARS DMFY */ if (start >= 0
                    && !source
                        .substring(Math.max(0, (int) start - 250), (int) start)
                        .stripTrailing()
                        .endsWith(MARK.strip())) offsets.add((int) start);
              }
              return super.scan(node, unused);
            }
          }.scan(unit, null);
          /* LFTY JGTM SCGC YARS DMFY */ StringBuilder result = new StringBuilder(source);
          /* LFTY JGTM SCGC YARS DMFY */ for (/* LFTY JGTM SCGC YARS DMFY */ int offset : offsets)
            result.insert(offset, MARK);
          Files.writeString(path, result, StandardCharsets.UTF_8);
          /* LFTY JGTM SCGC YARS DMFY */ JavacTask check =
              (JavacTask)
                  compiler.getTask(
                      null,
                      manager,
                      null,
                      List.of("-proc:none", "--release", "21"),
                      null,
                      manager.getJavaFileObjects(path.toFile()));
          /* LFTY JGTM SCGC YARS DMFY */ if (!check
              .parse()
              .iterator()
              .next()
              .toString()
              .equals(before)) {
            Files.writeString(path, source);
            throw new IllegalStateException("El AST cambió: " + path);
          }
          files++;
          nodes += offsets.size();
        }
      }
    }
    System.out.println(
        "Archivos revisados: " + files + ". Referencias añadidas: " + nodes + ". AST conservado.");
  }
}
