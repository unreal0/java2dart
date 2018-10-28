package src;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.dart.engine.ast.AstNode;
import com.google.dart.engine.ast.CompilationUnit;
import com.google.dart.engine.utilities.io.PrintStringWriter;
import com.google.dart.java2dart.Context;
import com.google.dart.java2dart.SyntaxTranslator;
import com.google.dart.java2dart.processor.RenameConstructorsSemanticProcessor;
import com.google.dart.java2dart.util.ToFormattedSourceVisitor;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;

import java.io.File;
import java.io.IOException;

import static org.fest.assertions.Assertions.assertThat;

public class Test1  {
    String filename = "";
    String s = "";
    String fileDir = "";
    protected File tmpFolder;
    protected File tmpFolder2;

    private String javaSource;
    private org.eclipse.jdt.core.dom.CompilationUnit javaUnit;
    private com.google.dart.engine.ast.CompilationUnit dartUnit;

    Test1(String fileDir, String filename, String s){
        this.fileDir = fileDir;
        this.filename = filename;
        this.s = s;
//        tmpFolder = Files.createTempDir();
//        tmpFolder = new File(this.getClass().getResource("/").getPath());
        tmpFolder =new File(System.getProperty("user.dir")+"/out/1");
//        tmpFolder2 =new File(System.getProperty("user.dir")+"/out/2");
//        tmpFolder2 = new File("2");
    }
    private final Context context = new Context();
    private CompilationUnit unit;

    public void test_Class() throws Exception {
        context.addSourceFolder(tmpFolder);
        context.addSourceFiles(tmpFolder);
        System.out.println(tmpFolder);
        translate();
        String s = getFormattedSource(unit);
        String[] ss = filename.split("\\.");
        System.out.print("s: "+s);

        setFileLines("/2/"+ss[0]+".dart", s);
//        context.addSourceFolder(tmpFolder2);
//        context.addSourceFiles(tmpFolder2);
//        System.out.println(tmpFolder2);

//        assertEquals(
//                toString(
//                        "class Test {",
//                        "  Test(int i);",
//                        "  bool foo() => false;",
//                        "  static main() {",
//                        "    Test v = new Test_main(42);",
//                        "  }",
//                        "}",
//                        "class Test_main extends Test {",
//                        "  Test_main(int arg0) : super(arg0);",
//                        "  bool foo() => true;",
//                        "}"),
//                getFormattedSource(unit));

    }

    public void testSyntax(String s) {
        parseJava(s);
        translateSyntax();
        String actual = context.getNodeAnnotations().toString();
        System.out.print("s: "+ actual);
    }

    public void tempfiledel() throws IOException {
        FileUtils.deleteDirectory(tmpFolder);

    }

    private void translateSyntax() {
        dartUnit = SyntaxTranslator.translate(context, javaUnit, javaSource);
    }
    private void translate() throws Exception {
        unit = context.translate();
        System.out.println("context: "+ context.getFileToMembers());

        System.out.println("unit: "+ unit);

        context.ensureUniqueClassMemberNames();
        context.applyLocalVariableSemanticChanges(unit);
        new RenameConstructorsSemanticProcessor(context).process(unit);
    }
    protected static boolean replaceSingleQuotes = false;

    /**
     * @return the formatted Dart source dump of the given {@link AstNode}.
     */
    protected static String getFormattedSource(AstNode node) {
        PrintStringWriter writer = new PrintStringWriter();
        node.accept(new ToFormattedSourceVisitor(writer));
        String result = writer.toString();
        return StringUtils.join(StringUtils.split(result, '\n'), "\n");
    }

    protected static void printFormattedSource(AstNode node) {
        String source = getFormattedSource(node);
        String[] lines = StringUtils.split(source, '\n');
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            line = StringUtils.replace(line, "\"", "\\\"");
            System.out.print("\"");
            System.out.print(line);
            if (i != lines.length - 1) {
                System.out.println("\",");
            } else {
                System.out.println("\"");
            }
        }
    }

    /**
     * @return the single {@link String} with "\n" separated lines.
     */
    protected static String toString(String... lines) {
        String result = Joiner.on("\n").join(lines);
        if (replaceSingleQuotes) {
            result = result.replace('\'', '"');
        }
        return result;
    }


    /**
     * Sets the content of the file with given path relative to {@link #tmpFolder}.
     */
    protected File setFileLines(String path, String content) throws Exception {
        File toFile = new File(tmpFolder, path);
        Files.createParentDirs(toFile);
        Files.write(content, toFile, Charsets.UTF_8);
        return toFile;
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
//        assertThat(javaUnit.getProblems()).isEmpty();
    }

}

