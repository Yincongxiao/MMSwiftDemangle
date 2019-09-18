/*
 * Created by Yin Congxiao.
 */

package demangle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;

/*
 * 字符串匹配器  有$符号表示会改变游标位置.
 */
public class SymbolScanner {
    private char[] mangled;
    private int index;

    public SymbolScanner(String mangled) {
        this.mangled = mangled.toCharArray();
        reset();
    }

    /*
     * reset index
     */
    public void reset() {
        this.index = 0;
    }

    /*
     * 匹配mangled字符串是否是以string开头,如果是那么返回true,否则返回false,$
     */
    public boolean conditional(String string) {
        if (string.length() == 0) return false;
        int i = index;
        for (char cInString :
                string.toCharArray()) {
            if (i == mangled.length || mangled[i] != cInString) {
                return false;
            }
            i++;
        }
        index = i;
        return true;
    }

    public String readChars(int count) throws Exception {
        String result = "";
        int i = index;
        for (int j = 0; j < count; j++) {
            if (i == mangled.length) {
                throwException(MangledExceptionType.endedPrematurely);
            }
            result = result + mangled[i];
            i++;
        }
        index = i;
        return result;
    }

    public int readInt() throws Exception {
        int res = conditionalInt();
        if (res < 0) {
            throwException(MangledExceptionType.expectedInt);
        }
        return res;
    }

    /*
     * Throws if scalar at the current `index` is not in the range `"0"` to `"9"`.
     * Consume scalars `"0"` to `"9"` until a scalar outside that range is encountered.
     * Return the integer representation of the value scanned, interpreted as a base 10 integer.
     * `index` is advanced to the end of the number.
     */
    public int conditionalInt() {
        int result = 0;
        int i = index;
        while (i != (mangled.length - 1) && Character.isDigit(mangled[i])) {
            int digit = mangled[i] - '0';
            result = result * 10 + digit;
            i++;
        }
        if (i == index) {
            return -1000;
        }
        index = i;
        return result;
    }

    /*
     * 往前回溯count位,$
     */
    public void backtrack(int count) {
        if (count <= 0) count = 1;
        if (count > index) count = index;
        index -= count;
    }

    /*
     * 获取剩余String,$
     */
    public String remainder() {
        String remainder = mangled.toString().substring(index);
        index = mangled.length;
        return remainder;
    }

    /*
     * 查看下一个字符.
     */
    public char requirePeek() throws Exception {
        if (index == mangled.length) {
            throwException(MangledExceptionType.endedPrematurely);
        }
        return mangled[index];
    }

    public char peek(int skipCount) throws Exception {
        int i = index;
        int c = skipCount;
        while (c > 0 && i != mangled.length) {
            i++;
            c -= 1;
        }

        if (i == mangled.length) {
            return 0;
        }
        return mangled[i];
    }

    /*
     * 匹配mangled字符串是否是以string开头,如果匹配失败,抛出异常.
     */
    public void match(String string) throws Exception {
        if (string.length() + index > mangled.length) {
            throwException(MangledExceptionType.matchFailed);
        }
        String toStr = new String(mangled);
        String subStr = toStr.substring(index, index + string.length());
        if (subStr.equals(string)) {
            index += string.length();
        } else {
            throwException(MangledExceptionType.matchFailed);
        }
    }

    public void match(char scalar) throws Exception {
        if (index == mangled.length || mangled[index] != scalar) {
            throwException(MangledExceptionType.matchFailed);
        }
        index++;
    }

    public interface MatchTester {
        boolean matchChar(char c);
    }

    public void match(MatchTester test) throws Exception {
        if (index == mangled.length || !test.matchChar(mangled[index])) {
            throwException(MangledExceptionType.matchFailed);
        }
        index++;
    }

    public char read(MatchTester test) throws Exception {
        if (index == mangled.length || !test.matchChar(mangled[index])) {
            throwException(MangledExceptionType.matchFailed);
        }
        char c = mangled[index];
        index++;
        return c;
    }

    public String readUntil(char scalar) throws Exception {
        int i = index;
        skipUntil(scalar);
        String str = "";
        while (i != index) {
            str += mangled[i];
            i++;
        }
        return str;
    }

    public String readUntil(String string) throws Exception {
        int i = index;
        skipUntil(string);
        String str = "";
        while (i != index) {
            str += mangled[i];
            i++;
        }
        return str;
    }

    public String readUntil(Set set) throws Exception {
        int i = index;
        skipUntil(set);
        String str = "";
        while (i != index) {
            str += mangled[i];
            i++;
        }
        return str;
    }

    public void skipUntil(char scalar) throws Exception {
        int i = index;
        while (i != mangled.length && mangled[i] != scalar) {
            i++;
        }
        if (i == mangled.length) {
            throwException(MangledExceptionType.searchFailed);
        }
        index = i;
    }

    public void skipUntil(Set set) throws Exception {
        int i = index;
        while (i != mangled.length && !set.contains(mangled[i])) {
            i++;
        }
        if (i == mangled.length) {
            throwException(MangledExceptionType.searchFailed);
        }
        index = i;
    }

    public String readWhile(MatchTester test) {
        String str = "";
        while (index != mangled.length) {
            if (!test.matchChar(mangled[index])) {
                break;
            }
            str += mangled[index];
            index++;
        }
        return str;
    }

    public void skipWhile(MatchTester test) {
        while (index != mangled.length) {
            if (!test.matchChar(mangled[index])) {
                return;
            }
            index++;
        }
    }

    public void skipUntil(String string) throws Exception {
        char[] match = string.toCharArray();
        char first = match[0];
        if (first == 0) return;
        if (match.length == 1) {
            skipUntil(first);
            return;
        }

        int i = index;
        int j = index;
        char[] remainder = Arrays.copyOfRange(match, 1, match.length - 1);
        do {
            while (mangled[i] != first) {
                if (i == mangled.length) {
                    throwException(MangledExceptionType.searchFailed);
                }
                i++;
                j = i;
            }
            i++;
            boolean isBreak = false;
            for (char s : remainder) {
                if (i == mangled.length) {
                    throwException(MangledExceptionType.searchFailed);
                }
                if (mangled[i] != s) {
                    isBreak = true;
                    break;
                }
                i++;
            }
            if (!isBreak) {
                break;
            }
        } while (true);
        index = j;
    }

    /*
     * 查看下一个字符,$
     */
    public char readChar() throws Exception {
        if (index == mangled.length) {
            throw new Exception("<readChar()>: out of bounce.");
        }
        return mangled[index++];
    }

    public Boolean isAtEnd() {
        return index == mangled.length;
    }

    public enum MangledExceptionType {
        utf8ParseError, unexpected, matchFailed, expectedInt, endedPrematurely, searchFailed, integerOverflow
    }

    public void throwException(MangledExceptionType type) throws Exception {
        throw new Exception(type.toString());
    }
}
