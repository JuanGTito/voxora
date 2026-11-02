import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.lang.model.element.Modifier;
import javax.tools.*;

/** Coloca solo las iniciales junto a declaraciones public, conservando el arbol sintactico. */
/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class AnnotateTeam {
  private static final String INITIALS = "LFTY · JGTM · SCGC · YARS · DMFY";
  private static final String MARK = "/" + "* " + INITIALS + " *" + "/";

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public static void main(String[] args) throws Exception {
    int files = 0, references = 0;
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    try (var paths = Files.walk(Path.of(args[0]));
        var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        String original = Files.readString(path);
        String before = parse(compiler, manager, path).parse().iterator().next().toString();
        String source =
            original
                .replace("/" + "* LFTY JGTM SCGC YARS DMFY *" + "/", "")
                .replace(MARK, "")
                .replaceAll("(?m)^\\h*// (?:Equipo: )?" + INITIALS + "\\h*\\R", "")
                .replace(" — " + INITIALS, "");
        Files.writeString(path, source, StandardCharsets.UTF_8);
        try {
          JavacTask task = parse(compiler, manager, path);
          CompilationUnitTree unit = task.parse().iterator().next();
          var positions = Trees.instance(task).getSourcePositions();
          TreeSet<Integer> offsets = new TreeSet<>(Comparator.reverseOrder());
          new TreeScanner<Void, Void>() {
            private void mark(Tree node, ModifiersTree modifiers) {
              if (modifiers.getFlags().contains(Modifier.PUBLIC)) {
                long start = positions.getStartPosition(unit, node);
                if (start >= 0) offsets.add((int) start);
              }
            }

            /* LFTY · JGTM · SCGC · YARS · DMFY */
            @Override
            public Void visitClass(ClassTree node, Void unused) {
              mark(node, node.getModifiers());
              return super.visitClass(node, unused);
            }

            /* LFTY · JGTM · SCGC · YARS · DMFY */
            @Override
            public Void visitMethod(MethodTree node, Void unused) {
              mark(node, node.getModifiers());
              return super.visitMethod(node, unused);
            }

            /* LFTY · JGTM · SCGC · YARS · DMFY */
            @Override
            public Void visitVariable(VariableTree node, Void unused) {
              mark(node, node.getModifiers());
              return super.visitVariable(node, unused);
            }
          }.scan(unit, null);
          StringBuilder result = new StringBuilder(source);
          for (int offset : offsets) {
            int lineStart = source.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
            String indent = source.substring(lineStart, offset);
            result.insert(offset, MARK + (indent.isBlank() ? "\n" + indent : " "));
          }
          Files.writeString(path, result, StandardCharsets.UTF_8);
          if (!parse(compiler, manager, path).parse().iterator().next().toString().equals(before)) {
            throw new IllegalStateException("El arbol sintactico cambio: " + path);
          }
          files++;
          references += offsets.size();
        } catch (Exception failure) {
          Files.writeString(path, original, StandardCharsets.UTF_8);
          throw failure;
        }
      }
    }
    System.out.println(
        "Archivos: "
            + files
            + ". Declaraciones public: "
            + references
            + ". Arbol sintactico conservado.");
  }

  private static JavacTask parse(
      JavaCompiler compiler, StandardJavaFileManager manager, Path path) {
    return (JavacTask)
        compiler.getTask(
            null,
            manager,
            null,
            List.of("-proc:none", "--release", "21"),
            null,
            manager.getJavaFileObjects(path.toFile()));
  }
}
