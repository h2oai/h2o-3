package water.util;

/**
 * The following code replaces Java 7 Objects class, while Java 7
 * is not always available.
 * 
 * Created by vpatryshev on 3/1/17.
 */
public class Java7 {
  public static final class Objects {

    public static boolean equals(Object x, Object y) {
      return x == y || (x != null && x.equals(y));
    }

    public static int hashCode(Object a) {
      return a == null ? 0 : a.hashCode();
    }
  }
}
