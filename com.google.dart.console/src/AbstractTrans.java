import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.dart.engine.ast.AstNode;
import com.google.dart.engine.utilities.io.PrintStringWriter;
import com.google.dart.java2dart.util.ToFormattedSourceVisitor;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
public class AbstractTrans {
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

    protected File tmpFolder;

    /**
     * Sets the content of the file with given path relative to {@link #tmpFolder}.
     */
    protected File setFileLines(String path, String content) throws Exception {
        File toFile = new File(tmpFolder, path);
        Files.createParentDirs(toFile);
        Files.write(content, toFile, Charsets.UTF_8);
        return toFile;
    }

}
