package water.util;

import water.DKV;
import water.Futures;
import water.Key;
import water.MemoryManager;
import water.fvec.AppendableVec;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;

import static water.util.RandomUtils.getRNG;

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
  public static float[] reduceMin(float[] a, float[] b) {
    for (int i=0; i<a.length; ++i)
      a[i] = Math.min(a[i], b[i]);
    return a;
  }
  public static float[] reduceMax(float[] a, float[] b) {
    for (int i=0; i<a.length; ++i)
      a[i] = Math.max(a[i], b[i]);
    return a;
  }
  public static double innerProduct(double [] x, double [] y){
    double result = 0;
    for (int i = 0; i < x.length; i++)
      result += x[i] * y[i];
    return result;
  }
  public static double[][] outerProduct(double[] x, double[] y){
    double[][] result = new double[x.length][y.length];
    for(int i = 0; i < x.length; i++) {
      for(int j = 0; j < y.length; j++)
        result[i][j] = x[i] * y[j];
    }
    return result;
  }

  public static double l2norm2(double [] x){ return l2norm2(x, false); }

  public static double l2norm2(double [][] xs, boolean skipLast){
    double res = 0;
    for(double [] x:xs)
      res += l2norm2(x,skipLast);
    return res;
  }
  public static double l2norm2(double [] x, boolean skipLast){
    double sum = 0;
    int last = x.length - (skipLast?1:0);
    for(int i = 0; i < last; ++i)
      sum += x[i]*x[i];
    return sum;
  }
  public static double l2norm2(double[] x, double[] y) {  // Computes \sum_{i=1}^n (x_i - y_i)^2
    assert x.length == y.length;
    double sse = 0;
    for(int i = 0; i < x.length; i++) {
      double diff = x[i] - y[i];
      sse += diff * diff;
    }
    return sse;
  }
  public static double l2norm2(double[][] x, double[][] y) {
    assert x.length == y.length && x[0].length == y[0].length;
    double sse = 0;
    for(int i = 0; i < x.length; i++)
      sse += l2norm2(x[i], y[i]);
    return sse;
  }

  public static double l1norm(double [] x){ return l1norm(x, false); }
  public static double l1norm(double [] x, boolean skipLast){
    double sum = 0;
    int last = x.length -(skipLast?1:0);
    for(int i = 0; i < last; ++i)
      sum += x[i] >= 0?x[i]:-x[i];
    return sum;
  }
  public static double linfnorm(double [] x, boolean skipLast){
    double res = Double.NEGATIVE_INFINITY;
    int last = x.length -(skipLast?1:0);
    for(int i = 0; i < last; ++i) {
      if(x[i] > res) res = x[i];
      if(-x[i] > res) res = -x[i];
    }
    return res;
  }
  public static double l2norm(double[] x) { return Math.sqrt(l2norm2(x)); }
  public static double l2norm(double [] x, boolean skipLast){
    return Math.sqrt(l2norm2(x, skipLast));
  }
  public static double l2norm(double[] x, double[] y) { return Math.sqrt(l2norm2(x,y)); }
  public static double l2norm(double[][] x, double[][] y) { return Math.sqrt(l2norm2(x,y)); }

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
  public static long[][][] add(long[][][] a, long[][][] b) {
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
  public static boolean[] or(boolean[] a, boolean[] b) {
    if (b==null)return a;
    for (int i = 0; i < a.length; i++) a[i] |= b[i];
    return a;
  }

  public static double[][] deepClone(double [][] ary){
    double [][] res = ary.clone();
    for(int i = 0 ; i < res.length; ++i)
      res[i] = ary[i].clone();
    return res;
  }

  public static double[] add(double[] a, double[] b) {
    if( a==null ) return b;
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }

  public static double[] wadd(double[] a, double[] b, double w) {
    if( a==null ) return b;
    for(int i = 0; i < a.length; i++ )
      a[i] += w*b[i];
    return a;
  }

  // a <- b + c
  public static double[] add(double[] a, double[] b, double [] c) {
    for(int i = 0; i < a.length; i++ )
      a[i] = b[i] + c[i];
    return a;
  }
  public static double[][] add(double[][] a, double[][] b) {
    for(int i = 0; i < a.length; i++ ) a[i] = add(a[i], b[i]);
    return a;
  }
  public static double[][][] add(double[][][] a, double[][][] b) {
    for(int i = 0; i < a.length; i++ ) a[i] = add(a[i],b[i]);
    return a;
  }

  public static double avg(double[] nums) {
    double sum = 0;
    for(double n: nums) sum+=n;
    return sum/nums.length;
  }
  public static double avg(long[] nums) {
    long sum = 0;
    for(long n: nums) sum+=n;
    return sum/nums.length;
  }
  public static long[] add(long[] nums, long a) {
    for (int i=0;i<nums.length;i++) nums[i] += a;
    return nums;
  }
  public static float[] div(float[] nums, int n) {
    for (int i=0; i<nums.length; i++) nums[i] /= n;
    return nums;
  }
  public static float[] div(float[] nums, float n) {
    assert !Float.isInfinite(n) : "Trying to divide " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] /= n;
    return nums;
  }
  public static double[] div(double[] nums, double n) {
    assert !Double.isInfinite(n) : "Trying to divide " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] /= n;
    return nums;
  }
  public static double[][] div(double[][] ds, long[] n) {
    for (int i=0; i<ds.length; i++) div(ds[i],n[i]);
    return ds;
  }
  public static double[] div(double[] ds, long[] n) {
    for (int i=0; i<ds.length; i++) ds[i]/=n[i];
    return ds;
  }
  public static float[] mult(float[] nums, float n) {
//    assert !Float.isInfinite(n) : "Trying to multiply " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] *= n;
    return nums;
  }
  public static double[] mult(double[] nums, double n) {
//    assert !Double.isInfinite(n) : "Trying to multiply " + Arrays.toString(nums) + " by  " + n; // Almost surely not what you want
    for (int i=0; i<nums.length; i++) nums[i] *= n;
    return nums;
  }
  public static double[][] mult(double[][] ary, double n) {
    if(ary == null) return null;
    for (int i=0; i<ary.length; i++) mult(ary[i], n);
    return ary;
  }
  public static double[] invert(double[] ary) {
    if(ary == null) return null;
    for(int i=0;i<ary.length;i++) ary[i] = 1. / ary[i];
    return ary;
  }

  public static double[] multArrVec(double[][] ary, double[] nums) {
    if(ary == null || nums == null) return null;
    assert ary[0].length == nums.length : "Inner dimensions must match: Got " + ary[0].length + " != " + nums.length;
    double[] res = new double[ary.length];
    for(int i = 0; i < ary.length; i++)
      res[i] = innerProduct(ary[i], nums);
    return res;
  }

  public static double[] multVecArr(double[] nums, double[][] ary) {
    if(ary == null || nums == null) return null;
    assert nums.length == ary.length : "Inner dimensions must match: Got " + nums.length + " != " + ary.length;
    double[] res = new double[ary[0].length];
    for(int j = 0; j < ary[0].length; j++) {
      res[j] = 0;
      for(int i = 0; i < ary.length; i++)
        res[j] += nums[i] * ary[i][j];
    }
    return res;
  }

  public static double[][] multArrArr(double[][] ary1, double[][] ary2) {
    if(ary1 == null || ary2 == null) return null;
    assert ary1[0].length == ary2.length : "Inner dimensions must match: Got " + ary1[0].length + " != " + ary2.length;   // Inner dimensions must match
    double[][] res = new double[ary1.length][ary2[0].length];

    for(int i = 0; i < ary1.length; i++) {
      for(int j = 0; j < ary2[0].length; j++) {
        double tmp = 0;
        for(int k = 0; k < ary1[0].length; k++)
          tmp += ary1[i][k] * ary2[k][j];
        res[i][j] = tmp;
      }
    }
    return res;
  }

  public static double[][] transpose(double[][] ary) {
    if(ary == null) return null;
    double[][] res = new double[ary[0].length][ary.length];
    for(int i = 0; i < res.length; i++) {
      for(int j = 0; j < res[0].length; j++)
        res[i][j] = ary[j][i];
    }
    return res;
  }

  /**
   * Given a n by k matrix X, form its Gram matrix
   * @param x Matrix of real numbers
   * @param transpose If true, compute n by n Gram of rows = XX'
   *                  If false, compute k by k Gram of cols = X'X
   * @return A symmetric positive semi-definite Gram matrix
   */
  public static double[][] formGram(double[][] x, boolean transpose) {
    if (x == null) return null;
    int dim_in = transpose ? x[0].length : x.length;
    int dim_out = transpose ? x.length : x[0].length;
    double[][] xgram = new double[dim_out][dim_out];

    // Compute all entries on and above diagonal
    if(transpose) {
      for (int i = 0; i < dim_in; i++) {
        // Outer product = x[i] * x[i]', where x[i] is col i
        for (int j = 0; j < dim_out; j++) {
          for (int k = j; k < dim_out; k++)
            xgram[j][k] += x[j][i] * x[k][i];
        }
      }
    } else {
      for (int i = 0; i < dim_in; i++) {
        // Outer product = x[i]' * x[i], where x[i] is row i
        for (int j = 0; j < dim_out; j++) {
          for (int k = j; k < dim_out; k++)
            xgram[j][k] += x[i][j] * x[i][k];
        }
      }
    }

    // Fill in entries below diagonal since Gram is symmetric
    for (int i = 0; i < dim_in; i++) {
      for (int j = 0; j < dim_out; j++) {
        for (int k = 0; k < j; k++)
          xgram[j][k] = xgram[k][j];
      }
    }
    return xgram;
  }
  public static double[][] formGram(double[][] x) { return formGram(x, false); }

  public static double[] permute(double[] vec, int[] idx) {
    if(vec == null) return null;
    assert vec.length == idx.length : "Length of vector must match permutation vector length: Got " + vec.length + " != " + idx.length;
    double[] res = new double[vec.length];

    for(int i = 0; i < vec.length; i++)
      res[i] = vec[idx[i]];
    return res;
  }
  public static double[][] permuteCols(double[][] ary, int[] idx) {
    if(ary == null) return null;
    assert ary[0].length == idx.length : "Number of columns must match permutation vector length: Got " + ary[0].length + " != " + idx.length;
    double[][] res = new double[ary.length][ary[0].length];

    for(int j = 0; j < ary[0].length; j++) {
      for(int i = 0; i < ary.length; i++)
        res[i][j] = ary[i][idx[j]];
    }
    return res;
  }
  public static double[][] permuteRows(double[][] ary, int[] idx) {
    if(ary == null) return null;
    assert ary.length == idx.length : "Number of rows must match permutation vector length: Got " + ary.length + " != " + idx.length;
    double[][] res = new double[ary.length][ary[0].length];
    for(int i = 0; i < ary.length; i++)
      res[i] = permute(ary[i], idx);
    return res;
  }

  public static double [][] generateLineSearchVecs(double [] srcVec, double [] gradient, int n, final double step) {
    double [][] res = new double[n][];
    double x = step;
    for(int i = 0; i < res.length; ++i) {
      res[i] = MemoryManager.malloc8d(srcVec.length);
      for(int j = 0; j < res[i].length; ++j)
        res[i][j] = srcVec[j] + gradient[j] * x;
      x *= step;
    }
    return res;
  }

  public static String arrayToString(int[] ary) {
    if (ary == null || ary.length==0 ) return "";
    int m = ary.length - 1;

    StringBuilder sb = new StringBuilder();
    for (int i = 0; ; i++) {
      sb.append(ary[i]);
      if (i == m) return sb.toString();
      sb.append(", ");
    }
  }

  // Convert array of primitives to an array of Strings.
  public static String[] toString(long[] dom) {
    String[] result = new String[dom.length];
    for (int i=0; i<dom.length; i++) result[i] = String.valueOf(dom[i]);
    return result;
  }
  public static String[] toString(int[] dom) {
    String[] result = new String[dom.length];
    for (int i=0; i<dom.length; i++) result[i] = String.valueOf(dom[i]);
    return result;
  }
  public static String[] toString(Object[] ary) {
    String[] result = new String[ary.length];
    for (int i=0; i<ary.length; i++) {
      Object o = ary[i];
      if (o != null && o.getClass().isArray()) {
        Class klazz = ary[i].getClass();
        result[i] = byte[].class.equals(klazz) ? Arrays.toString((byte[]) o) :
                    short[].class.equals(klazz) ? Arrays.toString((short[]) o) :
                    int[].class.equals(klazz) ? Arrays.toString((int[]) o) :
                    long[].class.equals(klazz) ? Arrays.toString((long[]) o) :
                    boolean[].class.equals(klazz) ? Arrays.toString((boolean[]) o) :
                    float[].class.equals(klazz) ? Arrays.toString((float[]) o) :
                    double[].class.equals(klazz) ? Arrays.toString((double[]) o) : Arrays.toString((Object[]) o);

      } else {
        result[i] = String.valueOf(o);
      }
    }
    return result;
  }

  public static boolean contains(String[] names, String name) {
    if (null == names) return false;
    for (String n : names) if (n.equals(name)) return true;
    return false;
  }

  static public boolean contains(int[] a, int d) {
    for (int anA : a) if (anA == d) return true;
    return false;
  }

  public static <T> T[] subarray(T[] a, int off, int len) {
    return Arrays.copyOfRange(a,off,off+len);
  }

  /** Returns the index of the largest value in the array.
   * In case of a tie, an the index is selected randomly.
   */
  public static int maxIndex(int[] from, Random rand) {
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

  public static int maxIndex(float[] from, Random rand) {
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

  public static int maxIndex(int[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]>from[result]) result = i;
    return result;
  }
  public static int maxIndex(long[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]>from[result]) result = i;
    return result;
  }
  public static int maxIndex(double[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]>from[result]) result = i;
    return result;
  }
  public static int minIndex(int[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]<from[result]) result = i;
    return result;
  }
  public static int minIndex(float[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]<from[result]) result = i;
    return result;
  }
  public static int minIndex(double[] from) {
    int result = 0;
    for (int i = 1; i<from.length; ++i)
      if (from[i]<from[result]) result = i;
    return result;
  }
  public static double maxValue(double[] ary) {
    return maxValue(ary,0,ary.length);
  }
  public static double maxValue(double[] ary, int from, int to) {
    double result = ary[from];
    for (int i = from+1; i<to; ++i)
      if (ary[i]>result) result = ary[i];
    return result;
  }
  public static float maxValue(float[] ary) {
    return maxValue(ary,0,ary.length);
  }
  public static float maxValue(float[] ary, int from, int to) {
    float result = ary[from];
    for (int i = from+1; i<to; ++i)
      if (ary[i]>result) result = ary[i];
    return result;
  }
  public static float minValue(float[] from) {
    float result = from[0];
    for (int i = 1; i<from.length; ++i)
      if (from[i]<result) result = from[i];
    return result;
  }
  public static double minValue(double[] from) {
    double result = from[0];
    for (int i = 1; i<from.length; ++i)
      if (from[i]<result) result = from[i];
    return result;
  }
  public static long maxValue(long[] from) {
    long result = from[0];
    for (int i = 1; i<from.length; ++i)
      if (from[i]>result) result = from[i];
    return result;
  }
  public static long minValue(long[] from) {
    long result = from[0];
    for (int i = 1; i<from.length; ++i)
      if (from[i]<result) result = from[i];
    return result;
  }
  public static long minValue(int[] from) {
    int result = from[0];
    for (int i = 1; i<from.length; ++i)
      if (from[i]<result) result = from[i];
    return result;
  }

  // Find an element with linear search & return it's index, or -1
  public static <T> int find(T[] ts, T elem) {return find(ts,elem,0);}

  // Find an element with linear search & return it's index, or -1
  public static <T> int find(T[] ts, T elem, int off) {
    for (int i = off; i < ts.length; i++)
      if (elem == ts[i] || elem.equals(ts[i]))
        return i;
    return -1;
  }
  public static int find(long[] ls, long elem) {
    for(int i=0; i<ls.length; ++i )
      if( elem==ls[i] ) return i;
    return -1;
  }

  private static final DecimalFormat default_dformat = new DecimalFormat("0.#####");
  public static String pprint(double[][] arr){
    return pprint(arr, default_dformat);
  }
  // pretty print Matrix(2D array of doubles)
  public static String pprint(double[][] arr,DecimalFormat dformat) {
    int colDim = 0;
    for( double[] line : arr )
      colDim = Math.max(colDim, line.length);
    StringBuilder sb = new StringBuilder();
    int max_width = 0;
    int[] ilengths = new int[colDim];
    Arrays.fill(ilengths, -1);
    for( double[] line : arr ) {
      for( int c = 0; c < line.length; ++c ) {
        double d = line[c];
        String dStr = dformat.format(d);
        if( dStr.indexOf('.') == -1 ) dStr += ".0";
        ilengths[c] = Math.max(ilengths[c], dStr.indexOf('.'));
        int prefix = (d >= 0 ? 1 : 2);
        max_width = Math.max(dStr.length() + prefix, max_width);
      }
    }
    for( double[] line : arr ) {
      for( int c = 0; c < line.length; ++c ) {
        double d = line[c];
        String dStr = dformat.format(d);
        if( dStr.indexOf('.') == -1 ) dStr += ".0";
        for( int x = dStr.indexOf('.'); x < ilengths[c] + 1; ++x )
          sb.append(' ');
        sb.append(dStr);
        if( dStr.indexOf('.') == -1 ) sb.append('.');
        for( int i = dStr.length() - Math.max(0, dStr.indexOf('.')); i <= 5; ++i )
          sb.append('0');
      }
      sb.append("\n");
    }
    return sb.toString();
  }
  public static int[] unpackInts(long... longs) {
    int len      = 2*longs.length;
    int result[] = new int[len];
    int i = 0;
    for (long l : longs) {
      result[i++] = (int) (l & 0xffffffffL);
      result[i++] = (int) (l>>32);
    }
    return result;
  }

  private static void swap(long[] a, int i, int change) {
    long helper = a[i];
    a[i] = a[change];
    a[change] = helper;
  }
  private static void swap(int[] a, int i, int change) {
    int helper = a[i];
    a[i] = a[change];
    a[change] = helper;
  }

  /**
   * Extract a shuffled array of integers
   * @param a input array
   * @param n number of elements to extract
   * @param result array to store the results into (will be of size n)
   * @param seed random number seed
   * @param startIndex offset into a
   * @return result
   */
  public static int[] shuffleArray(int[] a, int n, int result[], long seed, int startIndex) {
    if (n<=0) return result;
    Random random = getRNG(seed);
    if (result == null || result.length != n)
      result = new int[n];
    result[0] = a[startIndex];
    for (int i = 1; i < n; i++) {
      int j = random.nextInt(i+1);
      if (j!=i) result[i] = result[j];
      result[j] = a[startIndex+i];
    }
    for (int i = 0; i < n; ++i)
      assert(ArrayUtils.contains(result, a[startIndex+i]));
    return result;
  }

  public static void shuffleArray(int[] a, Random rng) {
    int n = a.length;
    for (int i = 0; i < n; i++) {
      int change = i + rng.nextInt(n - i);
      swap(a, i, change);
    }
  }

  // Generate a n by m array of random numbers drawn from the standard normal distribution
  public static double[][] gaussianArray(int n, int m) { return gaussianArray(n, m, System.currentTimeMillis()); }
  public static double[][] gaussianArray(int n, int m, long seed) {
    if(n <= 0 || m <= 0) return null;
    double[][] result = new double[n][m];
    Random random = getRNG(seed);

    for(int i = 0; i < n; i++) {
      for(int j = 0; j < m; j++)
        result[i][j] = random.nextGaussian();
    }
    return result;
  }
  public static double[] gaussianVector(int n) { return gaussianVector(n, System.currentTimeMillis()); }
  public static double[] gaussianVector(int n, long seed) {
    if(n <= 0) return null;
    double[] result = new double[n];
    Random random = getRNG(seed);

    for(int i = 0; i < n; i++)
      result[i] = random.nextGaussian();
    return result;
  }

  /** Returns number of strings which represents a number. */
  public static int numInts(String... a) {
    int cnt = 0;
    for(String s : a) if (isInt(s)) cnt++;
    return cnt;
  }

  public static boolean isInt(String s) {
    int i = s.charAt(0)=='-' ? 1 : 0;
    for(; i<s.length();i++) if (!Character.isDigit(s.charAt(i))) return false;
    return true;
  }

  public static int[] toInt(String[] a, int off, int len) {
    int[] res = new int[len];
    for(int i=0; i<len; i++) res[i] = Integer.valueOf(a[off + i]);
    return res;
  }

  /** Clever union of String arrays.
   *
   * For union of numeric arrays (strings represent integers) it is expecting numeric ordering.
   * For pure string domains it is expecting lexicographical ordering.
   * For mixed domains it always expects lexicographical ordering since such a domain were produce
   * by a parser which sort string with Array.sort().
   *
   * PRECONDITION - string domain was sorted by Array.sort(String[]), integer domain by Array.sort(int[]) and switched to Strings !!!
   *
   * @param a a set of strings
   * @param b a set of strings
   * @return union of arrays
   */
  public static String[] domainUnion(String[] a, String[] b) {
    int cIinA = numInts(a);
    int cIinB = numInts(b);
    // Trivial case - all strings or ints, sorted
    if (cIinA==0 && cIinB==0   // only strings
            || cIinA==a.length && cIinB==b.length ) // only integers
      return union(a, b, cIinA==0);
    // Be little bit clever here: sort string representing numbers first and append
    // a,b were sorted by Array.sort() but can contain some numbers.
    // So sort numbers in numeric way, and then string in lexicographical order
    int[] ai = toInt(a, 0, cIinA); Arrays.sort(ai); // extract int part but sort it in numeric order
    int[] bi = toInt(b, 0, cIinB); Arrays.sort(bi);
    String[] ri = toString(union(ai,bi)); // integer part
    String[] si = union(a, b, cIinA, a.length - cIinA, cIinB, b.length - cIinB, true);
    return join(ri, si);
  }

  /** Union of given String arrays.
   *
   * The method expects ordering of domains in given order (lexicographical, numeric)
   *
   * @param a first array
   * @param b second array
   * @param lexo - true if domains are sorted in lexicographical order or false for numeric domains
   * @return union of values in given arrays.
   *
   * precondition lexo ? a,b are lexicographically sorted : a,b are sorted numerically
   * precondition a!=null &amp;&amp; b!=null
   */
  public static String[] union(String[] a, String[] b, boolean lexo) {
    assert a!=null && b!=null : "Union expect non-null input!";
    return union(a, b, 0, a.length, 0, b.length, lexo);
  }
  public static String[] union(String[] a, String[] b, int aoff, int alen, int boff, int blen, boolean lexo) {
    assert a!=null && b!=null : "Union expect non-null input!";
    String[] r = new String[alen+blen];
    int ia = aoff, ib = boff, i = 0;
    while (ia < aoff+alen && ib < boff+blen) {
      int c = lexo ? a[ia].compareTo(b[ib]) : Integer.valueOf(a[ia]).compareTo(Integer.valueOf(b[ib]));
      if ( c < 0) r[i++] = a[ia++];
      else if (c == 0) { r[i++] = a[ia++]; ib++; }
      else r[i++] = b[ib++];
    }
    if (ia < aoff+alen) while (ia<aoff+alen) r[i++] = a[ia++];
    if (ib < boff+blen) while (ib<boff+blen) r[i++] = b[ib++];
    return Arrays.copyOf(r, i);
  }
  /** Returns a union of given sorted arrays. */
  public static int[] union(int[] a, int[] b) {
    assert a!=null && b!=null : "Union expect non-null input!";
    int[] r = new int[a.length+b.length];
    int ia = 0, ib = 0, i = 0;
    while (ia < a.length && ib < b.length) {
      int c = a[ia]-b[ib];
      if ( c < 0) r[i++] = a[ia++];
      else if (c == 0) { r[i++] = a[ia++]; ib++; }
      else r[i++] = b[ib++];
    }
    if (ia < a.length) while (ia<a.length) r[i++] = a[ia++];
    if (ib < b.length) while (ib<b.length) r[i++] = b[ib++];
    return Arrays.copyOf(r, i);
  }

  public static long[] join(long[] a, long[] b) {
    long[] res = Arrays.copyOf(a, a.length+b.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }
  public static float [] join(float[] a, float[] b) {
    float[] res = Arrays.copyOf(a, a.length+b.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }
  public static <T> T[] join(T[] a, T[] b) {
    T[] res = Arrays.copyOf(a, a.length+b.length);
    System.arraycopy(b, 0, res, a.length, b.length);
    return res;
  }

  public static final boolean hasNaNsOrInfs(double [] ary){
    for(double d:ary)
      if(Double.isNaN(d) || Double.isInfinite(d))
        return true;
    return false;
  }
  public static final boolean hasNaNs(double [] ary){
    for(double d:ary)
      if(Double.isNaN(d))
        return true;
    return false;
  }

  public static final boolean hasNaNsOrInfs(float [] ary){
    for(float d:ary)
      if(Double.isNaN(d) || Double.isInfinite(d))
        return true;
    return false;
  }
  /** Generates sequence (start, stop) of integers: (start, start+1, ...., stop-1) */
  static public int[] seq(int start, int stop) {
    assert start<stop;
    int len = stop-start;
    int[] res = new int[len];
    for(int i=start; i<stop;i++) res[i-start] = i;
    return res;
  }

  // warning: Non-Symmetric! Returns all elements in a that are not in b (but NOT the other way around)
  static public int[] difference(int a[], int b[]) {
    if (a == null) return new int[]{};
    if (b == null) return a.clone();
    int[] r = new int[a.length];
    int cnt = 0;
    for (int i=0; i<a.length; i++) {
      if (!contains(b, a[i])) r[cnt++] = a[i];
    }
    return Arrays.copyOf(r, cnt);
  }

  // warning: Non-Symmetric! Returns all elements in a that are not in b (but NOT the other way around)
  static public String[] difference(String a[], String b[]) {
    if (a == null) return new String[]{};
    if (b == null) return a.clone();
    String[] r = new String[a.length];
    int cnt = 0;
    for (int i=0; i<a.length; i++) {
      if (!contains(b, a[i])) r[cnt++] = a[i];
    }
    return Arrays.copyOf(r, cnt);
  }

  static public double[][] append( double[][] a, double[][] b ) {
    if( a==null ) return b;
    if( b==null ) return a;
    if( a.length==0 ) return b;
    if( b.length==0 ) return a;
    assert a[0].length==b[0].length;
    double[][] c = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,c,a.length,b.length);
    return c;
  }

  static public double[] append( double[] a, double[] b ) {
    if( a==null ) return b;
    if( b==null ) return a;
    if( a.length==0 ) return b;
    if( b.length==0 ) return a;
    double[] c = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,c,a.length,b.length);
    return c;
  }
  static public String[] append( String[] a, String[] b ) {
    if( a==null ) return b;
    if( b==null ) return a;
    if( a.length==0 ) return b;
    if( b.length==0 ) return a;
    String[] c = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,c,a.length,b.length);
    return c;
  }

  public static <T> T[] append(T[] a, T... b) {
    if( a==null ) return b;
    T[] tmp = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,tmp,a.length,b.length);
    return tmp;
  }

  static public String[] prepend(String[] ary, String s) {
    if (ary==null) return new String[] { s };
    String[] nary = new String[ary.length+1];
    nary[0] = s;
    System.arraycopy(ary,0,nary,1,ary.length);
    return nary;
  }
  static public <T> T[] copyAndFillOf(T[] original, int newLength, T padding) {
    if(newLength < 0) throw new NegativeArraySizeException("The array size is negative.");
    T[] newArray = Arrays.copyOf(original, newLength);
    if(original.length < newLength) {
      System.arraycopy(original, 0, newArray, 0, original.length);
      Arrays.fill(newArray, original.length, newArray.length, padding);
    } else
      System.arraycopy(original, 0, newArray, 0, newLength);
    return newArray;

  }

  static public double[] copyAndFillOf(double[] original, int newLength, double padding) {
    if(newLength < 0) throw new NegativeArraySizeException("The array size is negative.");
    double[] newArray = new double[newLength];
    if(original.length < newLength) {
      System.arraycopy(original, 0, newArray, 0, original.length);
      Arrays.fill(newArray, original.length, newArray.length, padding);
    } else
      System.arraycopy(original, 0, newArray, 0, newLength);
    return newArray;
  }
  static public long[] copyAndFillOf(long[] original, int newLength, long padding) {
    if(newLength < 0) throw new NegativeArraySizeException("The array size is negative.");
    long[] newArray = new long[newLength];
    if(original.length < newLength) {
      System.arraycopy(original, 0, newArray, 0, original.length);
      Arrays.fill(newArray, original.length, newArray.length, padding);
    } else
      System.arraycopy(original, 0, newArray, 0, newLength);
    return newArray;
  }

  static public double[] copyFromIntArray(int[] a) {
    double[] da = new double[a.length];
    for(int i=0;i<a.length;++i) da[i] = a[i];
    return da;
  }

  // sparse sortedMerge (ids and vals)
  public static void sortedMerge(int[] aIds, double [] aVals, int[] bIds, double [] bVals, int [] resIds, double [] resVals) {
    int i = 0, j = 0;
    for(int k = 0; k < resIds.length; ++k){
      if(i == aIds.length){
        System.arraycopy(bIds,j,resIds,k,resIds.length-k);
        System.arraycopy(bVals,j,resVals,k,resVals.length-k);
        j = bIds.length;
        break;
      }
      if(j == bIds.length) {
        System.arraycopy(aIds,i,resIds,k,resIds.length-k);
        System.arraycopy(aVals,i,resVals,k,resVals.length-k);
        i = aIds.length;
        break;
      }
      if(aIds[i] > bIds[j]) {
        resIds[k] = bIds[j];
        resVals[k] = bVals[j];
        ++j;
      } else {
        resIds[k] = aIds[i];
        resVals[k] = aVals[i];
        ++i;
      }
    }
    assert i == aIds.length && j == bIds.length;
  }

  public static String[] select(String[] ary, int[] idxs) {
    String [] res  = new String[idxs.length];
    for(int i = 0; i < res.length; ++i)
      res[i] = ary[idxs[i]];
    return res;
  }

  /**
   * Sort an integer array of indices based on values
   * Updates indices in place, keeps values the same
   * @param idxs indices
   * @param values values
   */
  public static void sort(final int[] idxs, final double[] values) {
    sort(idxs, values, 500);
  }
  public static void sort(final int[] idxs, final double[] values, int cutoff) {
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
//      Arrays.parallelSort(d, new Comparator<Integer>() {
      Arrays.sort(d, new Comparator<Integer>() {
        @Override
        public int compare(Integer x, Integer y) {
          return values[x] < values[y] ? -1 : (values[x] > values[y] ? 1 : 0);
        }
      });
      for (int i = 0; i < idxs.length; ++i) idxs[i] = d[i];
    }
  }

  public static double [] subtract (double [] a, double [] b) {
    double [] c = MemoryManager.malloc8d(a.length);
    subtract(a,b,c);
    return c;
  }
  public static void subtract (double [] a, double [] b, double [] c) {
    for(int i = 0; i < a.length; ++i)
      c[i] = a[i] - b[i];
  }

  /** Flatenize given array.
   *
   * Example: [[1,2], [3,null], [4]] -> [1,2,3,null,4]
   * @param arr array of arrays
   * @param <T> any type
   * @return flattened array, if input was null return null, if input was empty return null
   */
  public static <T> T[] flat(T[][] arr) {
    if (arr == null) return null;
    if (arr.length == 0) return null;
    int tlen = 0;
    for (T[] t : arr) tlen += t.length;
    T[] result = Arrays.copyOf(arr[0], tlen);
    int j = arr[0].length;
    for (int i = 1; i < arr.length; i++) {
      System.arraycopy(arr[i], 0, result, j, arr[i].length);
      j += arr[i].length;
    }
    return result;
  }

  public static double[] flat(double[][] arr) {
    if (arr == null) return null;
    if (arr.length == 0) return null;
    int tlen = 0;
    for (double[] t : arr) tlen += t.length;
    double[] result = Arrays.copyOf(arr[0], tlen);
    int j = arr[0].length;
    for (int i = 1; i < arr.length; i++) {
      System.arraycopy(arr[i], 0, result, j, arr[i].length);
      j += arr[i].length;
    }
    return result;
  }

  public static Object[][] zip(Object[] a, Object[] b) {
    if (a.length != b.length) throw new IllegalArgumentException("Cannot zip arrays of different lenghts!");
    Object[][] result = new Object[a.length][2];
    for (int i = 0; i < a.length; i++) {
      result[i][0] = a[i];
      result[i][1] = b[i];
    }

    return result;
  }

  public static <K, V> int crossProductSize(Map<K, V[]> hyperSpace) {
    int size = 1;
    for (Map.Entry<K,V[]> entry : hyperSpace.entrySet()) {
      V[] value = entry.getValue();
      size *= value != null ? value.length : 1;
    }
    return size;
  }

  public static Integer[] interval(Integer start, Integer end) {
    return interval(start, end, 1);
  }
  public static Integer[] interval(Integer start, Integer end, Integer step) {
    int len = 1 + (end - start) / step; // Include both ends of interval
    Integer[] result = new Integer[len];
    for(int i = 0, value = start; i < len; i++, value += step) {
      result[i] = value;
    }
    return result;
  }

  public static Float[] interval(Float start, Float end, Float step) {
    int len = 1 + (int)((end - start) / step); // Include both ends of interval
    Float[] result = new Float[len];
    Float value = start;
    for(int i = 0; i < len; i++, value = start + i*step) {
      result[i] = value;
    }
    return result;
  }

  public static String [] remove(String [] ary, String s) {
    if(s == null)return ary;
    int cnt = 0;
    int idx = find(ary,s);
    while(idx > 0) {
      ++cnt;
      idx = find(ary,s,++idx);
    }
    if(cnt == 0)return ary;
    String [] res = new String[ary.length-cnt];
    int j = 0;
    for(String x:ary)
      if(!x.equals(s))
        res[j++] = x;
    return res;
  }

  /** Create a new frame based on given row data.
   *  @param key   Key for the frame
   *  @param names names of frame columns
   *  @param rows  data given in the form of rows
   *  @return new frame which contains columns named according given names and including given data */
  public static Frame frame(Key key, String[] names, double[]... rows) {
    assert names == null || names.length == rows[0].length;
    Futures fs = new Futures();
    Vec[] vecs = new Vec[rows[0].length];
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(vecs.length);
    int rowLayout = -1;
    for( int c = 0; c < vecs.length; c++ ) {
      AppendableVec vec = new AppendableVec(keys[c], Vec.T_NUM);
      NewChunk chunk = new NewChunk(vec, 0);
      for (double[] row : rows) chunk.addNum(row[c]);
      chunk.close(0, fs);
      if( rowLayout== -1) rowLayout = vec.compute_rowLayout();
      vecs[c] = vec.close(rowLayout,fs);
    }
    fs.blockForPending();
    Frame fr = new Frame(key, names, vecs);
    if( key != null ) DKV.put(key, fr);
    return fr;
  }
  public static Frame frame(double[]... rows) { return frame(null, rows); }
  public static Frame frame(String[] names, double[]... rows) { return frame(Key.make(), names, rows); }
  public static Frame frame(String name, Vec vec) { Frame f = new Frame(); f.add(name, vec); return f; }

}
