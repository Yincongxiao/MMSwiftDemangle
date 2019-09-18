/*
 * Created by Yin Congxiao.
 */

package demangle;


public class Contents {

    public enum Type {
        NONE, INT, STRRING
    }

    public Type type;
    public int index;
    public String name;

    public Contents() {
        this.type = Type.NONE;
    }

    public Contents(String name) {
        this.type = Type.STRRING;
        this.name = name;
    }

    public Contents(int index) {
        this.type = Type.INT;
        this.index = index;
    }

    public int getIndex() {
        if (type == Type.INT) {
            return index;
        }
        return 0;
    }

    public String getName() {
        if (type == Type.STRRING) {
            return name;
        }
        return null;
    }
}

