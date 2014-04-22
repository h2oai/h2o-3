package water.util;

/* Bulk Array Utilities */
public class ArrayUtils {

  // Sum elements of an array
  public static long sum(final long[] from) {
    long result = 0;
    for (long d: from) result += d;
    return result;
  }
  public static int sum(final int[] from) {
    int result = 0;
    for( int d : from ) result += d;
    return result;
  }
  public static float sum(final float[] from) {
    float result = 0;
    for (float d: from) result += d;
    return result;
  }
  public static double sum(final double[] from) {
    double result = 0;
    for (double d: from) result += d;
    return result;
  }

  // Add arrays, element-by-element
  public static byte[] add(byte[] a, byte[] b) {
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }
  public static int[] add(int[] a, int[] b) {
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }
  public static int[][] add(int[][] a, int[][] b) {
    for(int i = 0; i < a.length; i++ ) add(a[i],b[i]);
    return a;
  }
  public static long[] add(long[] a, long[] b) {
    if( b==null ) return a;
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }
  public static long[][] add(long[][] a, long[][] b) {
    for(int i = 0; i < a.length; i++ ) add(a[i],b[i]);
    return a;
  }
  public static float[] add(float[] a, float[] b) {
    if( b==null ) return a;
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }
  public static float[][] add(float[][] a, float[][] b) {
    for(int i = 0; i < a.length; i++ ) add(a[i],b[i]);
    return a;
  }
  public static double[] add(double[] a, double[] b) {
    if( a==null ) return b;
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }
  public static double[][] add(double[][] a, double[][] b) {
    for(int i = 0; i < a.length; i++ ) a[i] = add(a[i],b[i]);
    return a;
  }

  // Convert array of primitives to an array of Strings.
  public static String[] toString(long[] dom) {
    String[] result = new String[dom.length];
    for (int i=0; i<dom.length; i++) result[i] = String.valueOf(dom[i]);
    return result;
  }

  // Find an element with linear search & return it's index, or -1
  public static <T> int find(T[] ts, T elem) {
    for( int i=0; i<ts.length; i++ )
      if( elem==ts[i] || elem.equals(ts[i]) )
        return i;
    return -1;
  }
}
