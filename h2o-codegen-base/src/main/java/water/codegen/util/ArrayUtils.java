package water.codegen.util;

import java.util.Arrays;

/**
 * Created by michal on 5/17/16.
 */
public class ArrayUtils {
  public static <T> T[] append(T[] a, T... b) {
    if( a==null ) return b;
    T[] tmp = Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b,0,tmp,a.length,b.length);
    return tmp;
  }
}
