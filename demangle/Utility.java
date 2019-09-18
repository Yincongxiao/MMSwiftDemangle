package demangle;

import java.util.ArrayList;
import java.util.List;

public class Utility {
    //快速构建 ArrList
    public static <T> SafeArrayList CreateLists(T... num) {
        SafeArrayList<T> list = new SafeArrayList<T>();
        if (num.length == 0) return list;
        for (int i = 0; i < num.length; i++) {
            if (num[i] != null) list.add(num[i]);
        }
        return list;
    }

    //翻转SafeArrayList,不改变源list
    public static <T> List<T> ReverseListWithoutChange(List<T> orList) {
        List resList = new SafeArrayList();
        for (int i = orList.size() - 1; i >= 0; --i) {
            resList.add(orList.get(i));
        }
        return resList;
    }

    // array safe get
    public static <T> T arrayAt(T[] array, int index) {
        try {
            return array[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    //first(where:);
    public static SwiftSymbol First(List<SwiftSymbol> list, First condition) {
        for (SwiftSymbol first : list) {
            if (condition.where(first)) {
                return first;
            }
        }
        return null;
    }

    public interface First {
        boolean where(SwiftSymbol swiftSymbol);
    }

    public static String validateString(String string) {
        if (string.isEmpty()) return "";
        return string;
    }

    public static <E> List<E> slice(List list, int from, int to) {
        if (from > to || from > list.size() - 1 || to < 0) {
            return new SafeArrayList<E>();
        } else {
            List subList = new SafeArrayList();
            int from_= Math.max(from, 0);
            int to_ = Math.min(to, list.size()-1);
            for (int i = from_; i <= to_; i++) {
                subList.add(list.get(i));
            }
            return subList;
        }
    }

    public static <E> List<E> dropFirst(List list) {
        if (list.size() < 1) return new ArrayList<>();
        return list.subList(1, list.size());
    }

    public static <E> List<E> dropLast(List list) {
        if (list.size() < 1) return new ArrayList<>();
        return list.subList(0, list.size() - 1);
    }

}
