package demangle;

import java.util.ArrayList;
import java.util.List;

public class SafeArrayList <E> extends ArrayList<E> {

    class Enumerator {
        int offset;
        SwiftSymbol element;
        public Enumerator(int offset, SwiftSymbol ele) {
            this.element = ele;
            this.offset = offset;
        }
    }

    @Override
    public E get(int index) {
        if (index < 0 || index >= this.size()) {
            return null;
        }
        return super.get(index);
    }

    //first(where:);
    public Enumerator First(Utility.First condition) {
        for (int i = 0; i < this.size(); i ++) {
            SwiftSymbol first = (SwiftSymbol)this.get(i);
            if (condition.where(first)) {
                return new Enumerator(i, first);
            }
        }
        return null;
    }
}
