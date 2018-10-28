import com.google.dart.engine.ast.CompilationUnit;
import com.google.dart.java2dart.Context;
import com.google.dart.java2dart.processor.PropertySemanticProcessor;
import com.google.dart.java2dart.processor.RenameConstructorsSemanticProcessor;

import java.io.File;

    public class Main {
        public static void main(String[] args) throws Exception {

//            SwingUtilities.invokeLater(new Runnable() {
//                public void run() {
//                    DragonConsoleFrame dcf = new DragonConsoleFrame();
//                    dcf.getConsole().setCommandProcessor(new DemoProcessor());
//                    dcf.getConsole().append("&ob>> ");
//                    dcf.setVisible(true);
//                }
//            });
            Trans trans = new Trans();
            trans.test();

        }
    }
