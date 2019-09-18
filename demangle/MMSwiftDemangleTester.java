package demangle;

import java.io.*;
import java.util.ArrayList;

public class MMSwiftDemangleTester {

    class Mangling {
        private String input;
        private String output;

        // The "manglings.txt" file contains some prefixes and metadata that are not
        // handled by the actual parser and printer so they're stripped, here.
        public Mangling(String input, String output) {
            if (input.startsWith("__")) {
                this.input = input.substring(1);
            } else {
                this.input = input;
            }
            if (output.startsWith("{")) {
                int endBrace = output.indexOf('}');
                if (endBrace > 0) {
                    int space = endBrace + 2 < output.length() ? endBrace + 2 : -1;
                    if (space > 0) {
                        this.output = output.substring(space);
                        return;
                    }
                }
            }
            this.output = output;
        }
    }

    public ArrayList<Mangling> readManglingFile() {
        try {
            ArrayList manging = new ArrayList();
            String pathname = "/Users/yincongxiao/Downloads/CwlDemangle-master/CwlDemangle/manglings.txt";
            File filename = new File(pathname);
            InputStreamReader reader = new InputStreamReader(new FileInputStream(filename));
            BufferedReader br = new BufferedReader(reader);
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith("_T0") || line.startsWith("_$S") || line.startsWith("_$s")) {
                    String[] components = line.split(" ---> ");
                    if (components.length == 2) {
                        Mangling mgl = new Mangling(components[0], components[1]);
                        manging.add(mgl);
                    }
                }
                line = br.readLine();
            }
            return manging;
        } catch (Exception ext) {
            System.out.println("fail reading manglings.txt file!");
            return null;
        }
    }

    /*
     *This function shows suggested usage of the mangled symbol parser and printer
     */
    public void demangle(String input, String expectedOutput) {
        try {
            SwiftSymbol swiftSymbol = MMSwiftDemangle.parsedMangledSwiftSymbol(input);
            String result = swiftSymbol.print(SymbolPrinter.defaultOptions | SymbolPrinter.SYNTHESIZESUGARONTYPES);
            if (!result.equals(expectedOutput)) {
                System.out.printf("\nFailed to demangle:\n   %s\nGot:\n  %s\nexpected:\n  %s\n", input, result, expectedOutput);
            }else  {
                System.out.printf("\nSuccessfully parsed and printed:\n  %s\nto:\n  %s\n", input, expectedOutput);
            }
        }catch (Exception ext) {
            System.out.printf("\nfail parsed, exception throwed!\n  %s\nexpected:\n   ",input, expectedOutput);
        }
    }

    public static void doTest() {
        MMSwiftDemangleTester tester = new MMSwiftDemangleTester();
        ArrayList <Mangling> manglings = tester.readManglingFile();
        for (Mangling mangling: manglings) {
            tester.demangle(mangling.input, mangling.output);
        }
    }

    public static void doTest(String mangledString, String expectedStr) {
        MMSwiftDemangleTester tester = new MMSwiftDemangleTester();
        tester.demangle(mangledString, expectedStr);
    }
}
