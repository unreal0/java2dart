import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.dart.engine.ast.AstNode;
import com.google.dart.engine.ast.CompilationUnit;
import com.google.dart.engine.utilities.io.PrintStringWriter;
import com.google.dart.java2dart.Context;
import com.google.dart.java2dart.SyntaxTranslator;
import com.google.dart.java2dart.processor.PropertySemanticProcessor;
import com.google.dart.java2dart.processor.RenameConstructorsSemanticProcessor;
import com.google.dart.java2dart.util.ToFormattedSourceVisitor;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;

import java.io.Console;
import java.io.File;

import static org.fest.assertions.Assertions.assertThat;

public class Trans extends AbstractTrans {
    private final Context context = new Context();
    private CompilationUnit unit;
    private String javaSource;
    private org.eclipse.jdt.core.dom.CompilationUnit javaUnit;
    private com.google.dart.engine.ast.CompilationUnit dartUnit;

    public void test() throws Exception {
//        translate();
        setFileLines(
                "test/Test1.java",
                toString(
                        "// filler filler filler filler filler filler filler filler filler filler",
                        "package test;",
                        "public class Test {",
                        "  public Test(int i) {",
                        "  }",
                        "  public boolean foo() {",
                        "    return false;",
                        "  }",
                        "  public static main() {",
                        "    Test v = new Test(42) {",
                        "      public boolean foo() {",
                        "        return true;",
                        "      }",
                        "    };",
                        "  }",
                        "}"));
//        context.addSourceFolder(tmpFolder);
//        context.addSourceFiles(tmpFolder);
        translate();
        setFileLines("test/t1.java",getFormattedSource(unit));
        context.addSourceFolder(tmpFolder);
        context.addSourceFiles(tmpFolder);

//        getFormattedSource(unit);
        printFormattedSource(unit);

    }

    /**
     * We can generate <code>List</code> for var-args declaration, but we don't know about them at
     * invocation point during syntax translation, only at semantic step.
     */
    public void test_varArgs() throws Exception {
        parseJava(
                "// filler filler filler filler filler filler filler filler filler filler",
                "public class A {",
                "  void test(int errorCode, Object ...args) {",
                "  }",
                "  void main() {",
                "    test(-1);",
                "    test(-1, 2, 3.0);",
                "  }",
                "}");
        assertDartSource(//
                "class A {",
                "  void test(int errorCode, List<Object> args) {",
                "  }",
                "  void main() {",
                "    test(-1);",
                "    test(-1, 2, 3.0);",
                "  }",
                "}");
    }

    void printFormattedSource() {
//        translate();
        String source = toFormattedSource(dartUnit);
        String[] lines = StringUtils.split(source, '\n');
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            System.out.print("\"");
            line = StringUtils.replace(line, "\"", "\\\"");
            System.out.print(line);
            if (i != lines.length - 1) {
                System.out.println("\",");
            } else {
                System.out.println("\"");
            }
        }
    }

    /**
     * Translates {@link #javaUnit} into {@link #dartUnit} and check that it produces given Dart
     * source.
     */
    private void assertDartSource(String... lines) {
//        translate();
        String actualDartSource = toFormattedSource(dartUnit);
        String expectedDartSource = Joiner.on("\n").join(lines);
//        assertEquals(expectedDartSource, actualDartSource);
    }

    /**
     * Parse Java source lines into {@link #javaUnit}.
     */
    private void parseJava(String... lines) {
        javaSource = Joiner.on("\n").join(lines);
        ASTParser parser = ASTParser.newParser(AST.JLS4);
        parser.setCompilerOptions(ImmutableMap.of(
                JavaCore.COMPILER_SOURCE,
                JavaCore.VERSION_1_5,
                JavaCore.COMPILER_DOC_COMMENT_SUPPORT,
                JavaCore.ENABLED));
        parser.setSource(javaSource.toCharArray());
        javaUnit = (org.eclipse.jdt.core.dom.CompilationUnit) parser.createAST(null);
        assertThat(javaUnit.getProblems()).isEmpty();
    }

//    private void translate() {
//        dartUnit = SyntaxTranslator.translate(context, javaUnit, javaSource);
//    }

    private void translate() throws Exception {
        unit = context.translate();
        context.ensureUniqueClassMemberNames();
        context.applyLocalVariableSemanticChanges(unit);
        new RenameConstructorsSemanticProcessor(context).process(unit);
    }
    /**
     * @return the formatted Dart source dump of the given {@link AstNode}.
     */
    private static String toFormattedSource(AstNode node) {
        PrintStringWriter writer = new PrintStringWriter();
        node.accept(new ToFormattedSourceVisitor(writer));
        String result = writer.toString();
        return StringUtils.join(StringUtils.split(result, '\n'), "\n");
    }
}
