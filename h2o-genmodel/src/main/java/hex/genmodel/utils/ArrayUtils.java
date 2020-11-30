package hex.genmodel.utils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

/**
 * Copied (partially) from water.util.ArrayUtils
 */
public class ArrayUtils {
  public static double[] nanArray(int len) {
    double[] arr = new double[len];
    for (int i = 0; i < len; i++) {
      arr[i] = Double.NaN;
    }
    return arr;
  }
  
  public static double l2norm(double[] x) {
    return Math.sqrt(l2norm2(x));
  }
  public static double l2norm2(double [] x){
    return l2norm2(x, false);
  }

  public static double l2norm2(double [] x, boolean skipLast){
    int last = x.length - (skipLast? 1 : 0);
    double sum = 0;
    for (int i = 0; i < last; ++i)
      sum += x[i]*x[i];
    return sum;
  }

  public static double[] flat(double[][] arr) {
    if (arr == null) return null;
    if (arr.length == 0) return null;
    int tlen = 0;
    for (double[] t : arr) tlen += (t != null) ? t.length : 0;
    double[] result = Arrays.copyOf(arr[0], tlen);
    int j = arr[0].length;
    for (int i = 1; i < arr.length; i++) {
      if (arr[i] == null)
        continue;
      System.arraycopy(arr[i], 0, result, j, arr[i].length);
      j += arr[i].length;
    }
    return result;
  }
  
  public static double[] eleDiff(final double[] from) {
    int arryLen = from.length-1;
    double[] cumsumR = new double[arryLen];
    for (int index = 0; index < arryLen; index++) {
      cumsumR[index] = from[index+1]-from[index];
    }
    return cumsumR;
  }

  /**
   * Check to see if a column is a boolean column.  A boolean column should contains only two
   * levels and the string describing the domains should be true/false
   * @param domains
   * @return
   */
  public static boolean isBoolColumn(String[] domains) {
    if (domains != null) {
      if (domains.length == 2) {  // check domain names to be true/false
        if (domains[0].equalsIgnoreCase("true") && domains[1].equalsIgnoreCase("false"))
          return true;
        else if (domains[1].equalsIgnoreCase("true") && domains[0].equalsIgnoreCase("false"))
          return true;
      } else if (domains.length == 1) {
        if (domains[0].equalsIgnoreCase("true") || domains[0].equalsIgnoreCase("false")) {
          return true;
        }
      }
    }
    return false;
  }

  public static int maxIndex(double[] from, Random rand) {
    assert rand != null;
    int result = 0;
    int maxCount = 0; // count of maximal element for a 1 item reservoir sample
    for( int i = 1; i < from.length; ++i ) {
      if( from[i] > from[result] ) {
        result = i;
        maxCount = 1;
      } else if( from[i] == from[result] ) {
        if( rand.nextInt(++maxCount) == 0 ) result = i;
      }
    }
    return result;
  }

  public static int maxIndex(double[] from) {
    int result = 0;
    for (int i = 1; i < from.length; ++i)
      if (from[i] > from[result]) result = i;
    return result;
  }

  /**
   * Sort an integer array of indices based on values
   * Updates indices in place, keeps values the same
   * @param idxs indices
   * @param values values
   */
  public static void sort(int[] idxs, double[] values) {
    sort(idxs, values, 500);
  }
  public static void sort(int[] idxs, final double[] values, int cutoff) {
    if (idxs.length < cutoff) {
      //hand-rolled insertion sort
      for (int i = 0; i < idxs.length; i++) {
        for (int j = i; j > 0 && values[idxs[j - 1]] > values[idxs[j]]; j--) {
          int tmp = idxs[j];
          idxs[j] = idxs[j - 1];
          idxs[j - 1] = tmp;
        }
      }
    } else {
      Integer[] d = new Integer[idxs.length];
      for (int i = 0; i < idxs.length; ++i) d[i] = idxs[i];
      Arrays.sort(d, new Comparator<Integer>() {
        @Override
        public int compare(Integer x, Integer y) {
        return values[x] < values[y] ? -1 : (values[x] > values[y] ? 1 : 0);
        }
      });
      for (int i = 0; i < idxs.length; ++i) idxs[i] = d[i];
    }
  }

  public static String[] append(String[] a, String... b) {
    if (a==null ) 
      return b;
    String[] tmp = Arrays.copyOf(a,a.length + b.length);
    System.arraycopy(b, 0, tmp, a.length, b.length);
    return tmp;
  }

  public static String[][] append(String[][] a, String[]... b) {
    if (a==null )
      return b;
    String[][] tmp = Arrays.copyOf(a,a.length + b.length);
    System.arraycopy(b, 0, tmp, a.length, b.length);
    return tmp;
  }

  public static double[] signum(double[] array) {
    double[] signArray = new double[array.length];
    for (int i = 0; i < array.length; i++) {
      if (array[i] > 0)
        signArray[i] = 1;
      else if (array[i] < 0)
        signArray[i] = -1;
      else
        signArray[i] = 0;
    }
    return signArray;
  }

  public static double[] difference(double[] array) {
    double[] difference = new double[array.length - 1];
    for (int i = 0; i < array.length - 1; i++) {
      difference[i] = array[i+1] - array[i];
    }
    return difference;
  }

}
