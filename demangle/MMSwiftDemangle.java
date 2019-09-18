/*
 * Created by Yin Congxiao.
 */

package demangle;

import java.util.ArrayList;

public class MMSwiftDemangle {

    public static SwiftSymbol parsedMangledSwiftSymbol(String mangled) throws Exception {
        validateManglingPrefix(mangled);
        Demangler demangler = new Demangler(mangled);
        return demangler.demangleSymbol();
    }

    /*
     * This is likely to be the primary entry point to this file.
     * Pass a string containing a Swift mangled symbol
     * get a parsed SwiftSymbol class which can then be directly examined or printed.
     */
    public static String parseMangledSwiftSymbolToString(String mangled) {
        String firstRegex = "!";
        String[] titleAndBody = mangled.split(firstRegex);
        if (titleAndBody.length != 2) return mangled;
        mangled = titleAndBody[1];

        String regex = " ";
        String[] subStrings = mangled.split(regex);
        String res = "";
        for (String subStr:subStrings) {
            subStr = subStr.trim();
            try {
                validateManglingPrefix(subStr);
                SwiftSymbol symbol = parsedMangledSwiftSymbol(subStr);
                String mangledStr = symbol.print(SymbolPrinter.defaultOptions | SymbolPrinter.SYNTHESIZESUGARONTYPES);
                if (!mangledStr.isEmpty()) subStr = mangledStr;
            }catch (Exception ext) {
                System.out.println(ext.getStackTrace());
            }finally {
                res += subStr + regex;
            }
        }

        return titleAndBody[0] + "!" + res;
    }

    /*
     * validate mangled symbol.
     */
    private static void validateManglingPrefix(String mangled) throws Exception {
        /*
        Swift 4   "_T0"
        Swift 4.x "$S""_$S"
        Swift 5+  "$s", "_$s"
        */
        String[] prefixes = new String[]{"_T0", "$S", "_$S", "$s", "_$s"};
        boolean validate = false;
        for (String prefix : prefixes) {
            if (mangled.startsWith(prefix)) validate = true;
        }
        if (!validate) {
            throw new Exception("invalidate mangled string, must has prefix: $s, $S, _T!");
        }
    }
}
