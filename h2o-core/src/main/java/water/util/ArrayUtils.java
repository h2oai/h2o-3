package water.util;

import water.*;
import water.fvec.*;

import java.text.DecimalFormat;
import java.util.*;

import static java.lang.StrictMath.sqrt;
import static water.util.RandomUtils.getRNG;

/* Bulk Array Utilities */
public class ArrayUtils {
  private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};

  public static int[] cumsum(final int[] from) {
    int arryLen = from.length;
    int[] cumsumR = new int[arryLen];
    int result = 0;
    for (int index = 0; index < arryLen; index++) {
      result += result+from[index];
      cumsumR[index] = result;
    }
    return cumsumR;
  }
  
  // Sum elements of an array
  public static long sum(final long[] from) {
    long result = 0;
    for (long d: from) result += d;
    return result;
  }
  public static long sum(final long[] from, int startIdx, int endIdx) {
    long result = 0;
    for (int i = startIdx; i < endIdx; i++) result += from[i];
    return result;
  }
  public static int sum(final int[] from) {
    int result = 0;
    for( int d : from ) result += d;
    return result;
  }
  public static long suml(final int[] from) {
    long result = 0;
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

  public static double innerProductPartial(double [] x, int[] x_index, double [] y){
    double result = 0;
    for (int i = 0; i < y.length; i++)
      result += x[x_index[i]] * y[i];
    return result;
  }

  public static double [] mmul(double [][] M, double [] V) {
    double [] res = new double[M.length];
    for(int i = 0; i < M.length; ++i) {
      double d = 0;
      for (int j = 0; j < V.length; ++j) {
        d += M[i][j] * V[j];
      }
      res[i] = d;
    }
    return res;
  }

  public static double[][] outerProduct(double[] x, double[] y){
    double[][] result = new double[x.length][y.length];
    for(int i = 0; i < x.length; i++) {
      for(int j = 0; j < y.length; j++)
        result[i][j] = x[i] * y[j];
    }
    return result;
  }

  /***
   * Find the index of an element val in the sorted array arr.
   * 
   * @param arr: sorted array
   * @param val: value of element we are interested in
   * @param <T>: data type
   * @return index of element val or -1 if not found
   */
  public static<T extends Comparable<T>> int indexOf(T[] arr, T val) {
    int highIndex = arr.length-1;
    int compare0 = val.compareTo(arr[0]); // small shortcut
    if (compare0 == 0)
      return 0;
    int compareLast = val.compareTo(arr[highIndex]);
    if (compareLast==0)
      return highIndex;
    if (val.compareTo(arr[0])<0 || val.compareTo(arr[highIndex])>0) // end shortcut
      return -1;
    
    int count = 0;
    int numBins = arr.length;
    int lowIndex = 0;

    while (count < numBins) {
      int tryBin = (int) Math.floor((highIndex+lowIndex)*0.5);
      double compareVal = val.compareTo(arr[tryBin]);
      if (compareVal==0)
        return tryBin;
      else if (compareVal>0)
        lowIndex = tryBin;
      else
        highIndex = tryBin;

      count++;
    }
    return -1;
  }
  
  // return the sqrt of each element of the array.  Will overwrite the original array in this case
  public static double[] sqrtArr(double [] x){
    assert (x != null);
    int len = x.length;

    for (int index = 0; index < len; index++) {
      assert (x[index]>=0.0);
      x[index] = sqrt(x[index]);
    }

    return x;
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

  /**
   * Like the R norm for matrices, this function will calculate the maximum absolute col sum if type='o' or
   * return the maximum absolute row sum otherwise
   * @param arr
   * @param type
   * @return
   */
  public static double rNorm(double[][] arr, char type) {
    double rnorm = Double.NEGATIVE_INFINITY;
    int numArr = arr.length;
    for (int rind = 0; rind < numArr; rind++) {
      double tempSum = 0.0;
      for (int cind = 0; cind < numArr; cind++) {
        tempSum += type == 'o' ? Math.abs(arr[rind][cind]) : Math.abs(arr[cind][rind]);
      }
      if (tempSum > rnorm)
        rnorm = tempSum;
    }
    return rnorm;
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
  public static float[] add(float ca, float[] a, float cb, float[] b) {
    for(int i = 0; i < a.length; i++ ) a[i] = (ca * a[i]) + (cb * b[i]);
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

  public static <T extends Iced> T[][] deepClone(T [][] ary){
    T [][] res = ary.clone();
    for(int i = 0 ; i < res.length; ++i)
      res[i] = deepClone(res[i]);
    return res;
  }
  public static <T extends Iced> T[] deepClone(T [] ary){
    T [] res = ary.clone();
    for(int j = 0; j < res.length; ++j)
      if(res[j] != null)
        res[j] = (T)res[j].clone();
    return res;
  }

  public static double[] add(double[] a, double[] b) {
    if( a==null ) return b;
    for(int i = 0; i < a.length; i++ ) a[i] += b[i];
    return a;
  }

  public static double[] add(double[] a, double b) {
    for(int i = 0; i < a.length; i++ ) a[i] += b;
    return a;
  }
  public static int[] add(int[] a, int b) {
    for(int i = 0; i < a.length; i++ ) a[i] += b;
    return a;
  }
  public static double[] wadd(double[] a, double[] b, double w) {
    if( a==null ) return b;
    for(int i = 0; i < a.length; i++ )
      a[i] += w*b[i];
    return a;
  }

  public static double[] wadd(double[] a, double[] b, double [] c, double w) {
    if( a==null ) return b;
    for(int i = 0; i < a.length; i++ )
      c[i] = a[i] + w*b[i];
    return c;
  }

  // a <- b + c
  public static double[] add(double[] a, double[] b, double [] c) {
    for(int i = 0; i < a.length; i++ )
      a[i] = b[i] + c[i];
    return a;
  }
  public static double[][] add(double[][] a, double[][] b) {
    if (a == null) return b;
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

  public static double[][] div(double[][] ds, double[] n) {
    for (int i=0; i<ds.length; i++) div(ds[i],n[i]);
    return ds;
  }

  public static double[] div(double[] ds, long[] n) {
    for (int i=0; i<ds.length; i++) ds[i]/=n[i];
    return ds;
  }
  public static double[] div(double[] ds, double[] n) {
    for (int i=0; i<ds.length; i++) ds[i]/=n[i];
    return ds;
  }

  public static double[][] mult(double[][] ds, double[] n) {
    for (int i=0; i<ds.length; i++) mult(ds[i],n[i]);
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
    for (double[] row : ary) mult(row, n);
    return ary;
  }

  public static double[] mult(double[] nums, double[] nums2) {
    for (int i=0; i<nums.length; i++) nums[i] *= nums2[i];
    return nums;
  }

  /**
   *
   * @param data vector (1 x n)
   * @param p vector (1 x n)
   * @param n vector (1 x n)
   * @return Result of matrix operation (data - p) * n
   */
  public static double subAndMul(double[] data, double[] p, double[] n) {
    double res = 0;
    for (int col=0; col<data.length; col++)
      res += (data[col] - p[col]) * n[col];
    return res;
  }

  public static double[] invert(double[] ary) {
    if(ary == null) return null;
    for(int i=0;i<ary.length;i++) ary[i] = 1. / ary[i];
    return ary;
  }

  public static double[] multArrVec(double[][] ary, double[] nums) {
    if(ary == null) return null;
    double[] res = new double[ary.length];
    return multArrVec(ary, nums, res);
  }

  public static double[] multArrVecPartial(double[][] ary, double[] nums, int[] numColInd) {
    if(ary == null) return null;
    double[] res = new double[ary.length];
    for (int ind = 0; ind < ary.length; ind++) {
      res[ind] = innerProductPartial(nums, numColInd, ary[ind]);
    }
    return res;
  }
  public static double[] diagArray(double[][] ary) {
    if(ary == null) return null;
    int arraylen = ary.length;
    double[] res = new double[ary.length];
    for (int index=0; index < arraylen; index++)
      res[index] = ary[index][index];
    return res;
  }

  /***
   * Return the index of an element val that is less than tol array from an element of the array arr.
   * Note that arr does not need to be sorted.
   * 
   * @param arr: double array possibly containing an element of interest.
   * @param val: val to be found in array arr
   * @param tol: maximum difference between value of interest val and an element of array
   * @return the index of element that is within tol away from val or -1 if not found
   */
  public static int locate(double[] arr, double val, double tol) {
    int arrLen = arr.length;
    for (int index = 0; index < arrLen; index++) {
      if (Math.abs(arr[index]-val) < tol) 
        return index;
    }
    return -1;
  }
  
  public static double[] multArrVec(double[][] ary, double[] nums, double[] res) {
    if(ary == null || nums == null) return null;
    assert ary[0].length == nums.length : "Inner dimensions must match: Got " + ary[0].length + " != " + nums.length;
    for(int i = 0; i < ary.length; i++)
      res[i] = innerProduct(ary[i], nums);
    return res;
  }

  public static double[] multVecArr(double[] nums, double[][] ary) {
    if(ary == null || nums == null) return null;
    assert nums.length == ary.length : "Inner dimensions must match: Got " + nums.length + " != " + ary.length;
    double[] res = new double[ary[0].length]; // number of columns
    for(int j = 0; j < ary[0].length; j++) {  // go through each column
      res[j] = 0;
      for(int i = 0; i < ary.length; i++) // inner product of nums with each column of ary
        res[j] += nums[i] * ary[i][j];
    }
    return res;
  }

  /*
  with no memory allocation for results.  We assume the memory is already allocated.
   */
  public static double[][] multArrArr(double[][] ary1, double[][] ary2, double[][] res) {
    if(ary1 == null || ary2 == null) return null;
    // Inner dimensions must match
    assert ary1[0].length == ary2.length : "Inner dimensions must match: Got " + ary1[0].length + " != " + ary2.length;

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

  /*
  with memory allocation for results
   */
  public static double[][] multArrArr(double[][] ary1, double[][] ary2) {
    if(ary1 == null || ary2 == null) return null;
    double[][] res = new double[ary1.length][ary2[0].length];

    return multArrArr(ary1, ary2, res);
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

  public static double[][] expandArray(double[][] ary, int newColNum) {
    if(ary == null) return null;
    assert ary.length < newColNum : "new array should be greater than original array in second dimension.";
    int oldMatRow = ary.length;
    double[][] res = new double[newColNum][newColNum];
    for(int i = 0; i < oldMatRow; i++) {
      System.arraycopy(ary[i], 0, res[i], 0, oldMatRow);
    }
    return res;
  }

  /***
   * This function will perform transpose of triangular matrices only.  If the original matrix is lower triangular,
   * the return matrix will be upper triangular and vice versa.
   * 
   * @param ary
   * @return
   */
  public static double[][] transposeTriangular(double[][] ary, boolean upperTriangular) {
    if(ary == null) return null;
    int rowNums = ary.length;
    double[][] res = new double[ary.length][]; // allocate as many rows as original matrix
    for (int rowIndex=0; rowIndex < rowNums; rowIndex++) {
      int colNum = upperTriangular?(rowIndex+1):(rowNums-rowIndex);
      res[rowIndex] = new double[colNum];
      for (int colIndex=0; colIndex < colNum; colIndex++)
        res[rowIndex][colIndex] = ary[colIndex+rowIndex][rowIndex];
    }
    return res;
  }

  public static <T> T[] cloneOrNull(T[] ary){return ary == null?null:ary.clone();}

  public static <T> T[][] transpose(T[][] ary) {
    if(ary == null|| ary.length == 0) return ary;
    T [][] res  = Arrays.copyOf(ary,ary[0].length);
    for(int i = 0; i < res.length; ++i)
      res[i] = Arrays.copyOf(ary[0],ary.length);
    for(int i = 0; i < res.length; i++) {
      for(int j = 0; j < res[0].length; j++)
        res[i][j] = ary[j][i];
    }
    return res;
  }

  /**
   * Provide array from start to end in steps of 1
   * @param start beginning value (inclusive)
   * @param end   ending value (inclusive)
   * @return specified range of integers
   */
  public static int[] range(int start, int end) {
    int[] r = new int[end-start+1];
    for(int i=0;i<r.length;i++)
      r[i] = i+start;
    return r;
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
  
  public static String toStringQuotedElements(Object[] a) {
    return toStringQuotedElements(a, -1);
  }
  
  public static String toStringQuotedElements(Object[] a, int maxItems) {
    if (a == null)
      return "null";

    if (a.length == 0)
      return "[]";
    
    int max = a.length;
    int ellipsisIdx = max+1;
    if (maxItems > 0 && maxItems < a.length)  {
      max = maxItems + 1;
      ellipsisIdx = max / 2;
    }

    StringBuilder b = new StringBuilder();
    b.append('[');
    for (int i = 0; i < max; i++) {
      int idx = i == ellipsisIdx ? -1 
              : i < ellipsisIdx ? i 
              : a.length - max + i;
      if (idx >= 0)
        b.append('"').append(a[idx]).append('"');
      else
        b.append("...").append(a.length - maxItems).append(" not listed...");
      if (i < max-1) b.append(", ");
    }
    return b.append(']').toString();
  }

  public static <T> boolean contains(T[] arr, T target) {
    if (null == arr) return false;
    for (T t : arr) {
      if (t == target) return true;
      if (t != null && t.equals(target))  return true;
    }
    return false;
  }

  static public boolean contains(byte[] a, byte d) {
    for (byte anA : a) if (anA == d) return true;
    return false;
  }

  static public boolean contains(int[] a, int d) {
    for (int anA : a) if (anA == d) return true;
    return false;
  }

  public static byte[] subarray(byte[] a, int off, int len) {
    return Arrays.copyOfRange(a,off,off+len);
  }
  
  public static <T> T[] subarray(T[] a, int off, int len) {
    return Arrays.copyOfRange(a,off,off+len);
  }

  public static <T> T[][] subarray2DLazy(T[][] a, int columnOffset, int len) {
    return Arrays.copyOfRange(a, columnOffset, columnOffset + len);
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
  public static int maxIndex(long[] from, int off) {
    int result = off;
    for (int i = off+1; i<from.length; ++i)
      if (from[i]>from[result]) result = i;
    return result;
  }
  public static int maxIndex(float[] from) {
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
  public static double minValue(double[] ary, int from, int to) {
    double result = ary[from];
    for (int i = from+1; i<to; ++i)
      if (ary[i]<result) result = ary[i];
    return result;
  }
  public static double minValue(double[] from) {
    return Arrays.stream(from).min().getAsDouble();
  }

  /**
   * Find minimum and maximum in array in the same time
   *
   * @return Array with 2 fields. First field is minimum and second field is maximum.
   */
  public static double[] minMaxValue(double[] from) {
    double min = Double.MAX_VALUE;
    double max = Double.MIN_VALUE;
    for (int i = 0; i < from.length; ++i) {
      if (from[i] < min) min = from[i];
      if (from[i] > max) max = from[i];
    }
    return new double[]{min, max};
  }

  public static long maxValue(long[] from) {
    return Arrays.stream(from).max().getAsLong();
  }

  public static int maxValue(Integer[] from) {
    return Arrays.stream(from).max(Integer::compare).get();
  }
  
  public static int maxValue(int[] from) {
    return Arrays.stream(from).max().getAsInt();
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


  /**
   * Find an element with prefix with linear search & return it's index if find exactly, 
   *  -index-2 if find first occurrence with prefix or -1
   */
  public static int findWithPrefix(String[] array, String prefix) {
    return findWithPrefix(array, prefix, 0);
  }

  /**
   * Find an element with prefix with linear search & return it's index if find exactly, 
   *  -index-2 if find first occurrence with prefix or -1
   */
  public static int findWithPrefix(String[] array, String prefix, int off) {
    for (int i = off; i < array.length; i++) {
      if(array[i].equals(prefix)){
        return i;
      }
      if (array[i].startsWith(prefix)) {
        return -i - 2;
      }
    }
    return -1;
  }
  
  public static int find(long[] ls, long elem) {
    for(int i=0; i<ls.length; ++i )
      if( elem==ls[i] ) return i;
    return -1;
  }
  public static int find(int[] ls, int elem) {
    for(int i=0; i<ls.length; ++i )
      if( elem==ls[i] ) return i;
    return -1;
  }
  // behaves like Arrays.binarySearch, but is slower -> Just good for tiny arrays (length<20)
  public static int linearSearch(double[] vals, double v) {
    final int N=vals.length;
    for (int i=0; i<N; ++i) {
      if (vals[i]==v) return i;
      if (vals[i]>v) return -i-1;
    }
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
  public static double[] gaussianVector(int n, long seed) { return gaussianVector(n, getRNG(seed)); }
  /**
   * Make a new array initialized to random Gaussian N(0,1) values with the given seed.
   *
   * @param n length of generated vector
   * @return array with gaussian values. Randomly selected {@code zeroNum} item values are zeros.
   */
  public static double[] gaussianVector(int n, Random random) {
    if(n <= 0) return null;
    double[] result = new double[n];  // ToDo: Get rid of this new action.

    for(int i = 0; i < n; i++)
      result[i] = random.nextGaussian();
    return result;
  }  

  /** Remove the array allocation in this one */
  public static double[] gaussianVector(long seed, double[] vseed) {
    if (vseed == null)
      return null;

    Random random = getRNG(seed);
    int arraySize = vseed.length;
    for (int i=0; i < arraySize; i++) {
      vseed[i] = random.nextGaussian();
    }
    return vseed;
  }

  /** Returns number of strings which represents a number. */
  public static int numInts(String... a) {
    int cnt = 0;
    for(String s : a) if (isInt(s)) cnt++;
    return cnt;
  }

  public static boolean isInt(String... ary) {
    for(String s:ary) {
      if (s == null || s.isEmpty()) return false;
      int i = s.charAt(0) == '-' ? 1 : 0;
      for (; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
    }
    return true;
  }

  public static int[] toInt(String[] a, int off, int len) {
    int[] res = new int[len];
    for(int i=0; i<len; i++) res[i] = Integer.valueOf(a[off + i]);
    return res;
  }

  public static Integer[] toIntegers(int[] a, int off, int len) {
    Integer [] res = new Integer[len];
    for(int i = 0; i < len; ++i)
      res[i] = a[off+i];
    return res;
  }

  public static int[] toInt(Integer[] a, int off, int len) {
    int [] res = new int[len];
    for(int i = 0; i < len; ++i)
      res[i] = a[off+i];
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
   * // TODO: add tests
   */
  public static String[] domainUnion(String[] a, String[] b) {
    if (a == null) return b;
    if (b == null) return a;
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
    if (a == null) return b;
    if (b == null) return a;
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

  public static boolean hasNaNsOrInfs(double [] ary){
    for(double d:ary)
      if(Double.isNaN(d) || Double.isInfinite(d))
        return true;
    return false;
  }
  public static boolean hasNaNs(double [] ary){
    for(double d:ary)
      if(Double.isNaN(d))
        return true;
    return false;
  }

  public static boolean hasNaNsOrInfs(float [] ary){
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
    for (int x : a) {
      if (!contains(b, x)) r[cnt++] = x;
    }
    return Arrays.copyOf(r, cnt);
  }

  // warning: Non-Symmetric! Returns all elements in a that are not in b (but NOT the other way around)
  static public String[] difference(String a[], String b[]) {
    if (a == null) return new String[]{};
    if (b == null) return a.clone();
    String[] r = new String[a.length];
    int cnt = 0;
    for (String s : a) {
      if (!contains(b, s)) r[cnt++] = s;
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

  static public byte[] append( byte[] a, byte... b ) {
    if( a==null ) return b;
    if( b==null ) return a;
    if( a.length==0 ) return b;
    if( b.length==0 ) return a;
    byte[] c = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,c,a.length,b.length);
    return c;
  }

  static public int[] append( int[] a, int[] b ) {
    if( a==null ) return b;
    if( b==null ) return a;
    if( a.length==0 ) return b;
    if( b.length==0 ) return a;
    int[] c = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,c,a.length,b.length);
    return c;
  }

  static public long[] append( long[] a, long[] b ) {
    if( a==null ) return b;
    if( b==null ) return a;
    if( a.length==0 ) return b;
    if( b.length==0 ) return a;
    long[] c = Arrays.copyOf(a,a.length+b.length);
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

  // Java7+  @SafeVarargs
  public static <T> T[] append(T[] a, T... b) {
    if( a==null ) return b;
    T[] tmp = Arrays.copyOf(a,a.length+b.length);
    System.arraycopy(b,0,tmp,a.length,b.length);
    return tmp;
  }
  
  public static int[] append(int[] a, int b) {
    if( a==null || a.length == 0) return  new int[]{b};
    int[] tmp = Arrays.copyOf(a,a.length+1);
    tmp[a.length] = b;
    return tmp;
  }
  
  public static double[] append(double[] a, double b) {
    if( a==null || a.length == 0) return  new double[]{b};
    double[] tmp = Arrays.copyOf(a,a.length+1);
    tmp[a.length] = b;
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
  static public int[] copyAndFillOf(int[] original, int newLength, int padding) {
    if(newLength < 0) throw new NegativeArraySizeException("The array size is negative.");
    int[] newArray = new int[newLength];
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

  public static int [] sortedMerge(int[] a, int [] b) {
    int [] c = MemoryManager.malloc4(a.length + b.length);
    int i = 0, j = 0;
    for(int k = 0; k < c.length; ++k){
      if(i == a.length) c[k] = b[j++];
      else if(j == b.length)c[k] = a[i++];
      else if(b[j] < a[i]) c[k] = b[j++];
      else c[k] = a[i++];
    }
    return c;
  }
  public static double [] sortedMerge(double[] a, double [] b) {
    double [] c = MemoryManager.malloc8d(a.length + b.length);
    int i = 0, j = 0;
    for(int k = 0; k < c.length; ++k){
      if(i == a.length) c[k] = b[j++];
      else if(j == b.length)c[k] = a[i++];
      else if(b[j] < a[i]) c[k] = b[j++];
      else c[k] = a[i++];
    }
    return c;
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
  public static String[] select(String[] ary, byte[] idxs) {
    String [] res  = new String[idxs.length];
    for(int i = 0; i < res.length; ++i)
      res[i] = ary[idxs[i]];
    return res;
  }

  public static double[] select(double[] ary, int[] idxs) {
    double [] res = MemoryManager.malloc8d(idxs.length);
    for(int i = 0; i < res.length; ++i)
      res[i] = ary[idxs[i]];
    return res;
  }

  public static int[] select(int[] ary, int[] idxs) {
    int [] res = MemoryManager.malloc4(idxs.length);
    for(int i = 0; i < res.length; ++i)
      res[i] = ary[idxs[i]];
    return res;
  }

  public static byte[] select(byte[] array, int[] idxs) {
    byte[] res = MemoryManager.malloc1(idxs.length);
    for(int i = 0; i < res.length; ++i)
      res[i] = array[idxs[i]];
    return res;
  }

  public static double [] expandAndScatter(double [] ary, int N, int [] ids) {
    assert ary.length == ids.length:"ary.length = " + ary.length + " != " + ids.length + " = ids.length";
    double [] res = MemoryManager.malloc8d(N);
    for(int i = 0; i < ids.length; ++i) res[ids[i]] = ary[i];
    return res;
  }


  /**
   * Sort an integer array of indices based on values
   * Updates indices in place, keeps values the same
   * @param idxs indices
   * @param values values
   */
  public static void sort(final int[] idxs, final double[] values) {
    sort(idxs, values, 500, 1);
  }
  
  public static void sort(final int[] idxs, final double[] values, int cutoff) {
    sort(idxs, values, cutoff, 1);
  }
  // set increasing to 1 for ascending sort and -1 for descending sort
  public static void sort(final int[] idxs, final double[] values, int cutoff, int increasing) {
    if (idxs.length < cutoff) {
      //hand-rolled insertion sort
      for (int i = 0; i < idxs.length; i++) {
        for (int j = i; j > 0 && values[idxs[j - 1]]*increasing > values[idxs[j]]*increasing; j--) {
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
          return values[x]*increasing < values[y]*increasing ? -1 :
                  (values[x]*increasing > values[y]*increasing ? 1 : 0);
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
  
  public static double [][] subtract (double [][] a, double [][] b) {
    double [][] c = MemoryManager.malloc8d(a.length, a[0].length);
    for (int rowIndex = 0; rowIndex < c.length; rowIndex++) {
      c[rowIndex] = subtract(a[rowIndex], b[rowIndex], c[rowIndex]);
    }
    return c;
  }

  public static int [] subtract (int [] a, int [] b) {
    int [] c = MemoryManager.malloc4 (a.length);
    for (int i = 0; i < a.length; i++)
      c[i] = a[i]-b[i];
    return c;
  }

  public static double[] subtract (double [] a, double [] b, double [] c) {
    for(int i = 0; i < a.length; ++i)
      c[i] = a[i] - b[i];
    return c;
  }

  /** Flatenize given array (skips null arrays)
   *
   * Example: [[1,2], null, [3,null], [4]] -> [1,2,3,null,4]
   * @param arr array of arrays
   * @param <T> any type
   * @return flattened array, if input was null return null, if input was empty return null
   */
  public static <T> T[] flat(T[][] arr) {
    if (arr == null) return null;
    if (arr.length == 0) return null;
    int tlen = 0;
    for (T[] t : arr) tlen += (t != null) ? t.length : 0;
    T[] result = Arrays.copyOf(arr[0], tlen);
    int j = arr[0].length;
    for (int i = 1; i < arr.length; i++) {
      if (arr[i] == null)
        continue;
      System.arraycopy(arr[i], 0, result, j, arr[i].length);
      j += arr[i].length;
    }
    return result;
  }

  public static double [][] convertTo2DMatrix(double [] x, int N) {
    assert x.length % N == 0: "number of coefficient should be divisible by number of coefficients per class ";
    int len = x.length/N; // N is number of coefficients per class
    double [][] res = new double[len][];
    for(int i = 0; i < len; ++i) { // go through each class
      res[i] = MemoryManager.malloc8d(N);
      System.arraycopy(x,i*N,res[i],0,N);
    }
    return res;
  }

  public static Object[][] zip(Object[] a, Object[] b) {
    if (a.length != b.length) throw new IllegalArgumentException("Cannot zip arrays of different lengths!");
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

  public static Double[] interval(Double start, Double end, Double step) {
    int len = 1 + (int)((end - start) / step); // Include both ends of interval
    Double[] result = new Double[len];
    Double value = start;
    for(int i = 0; i < len; i++, value = start + i*step) {
      result[i] = value;
    }
    return result;
  }

  public static String [] remove(String [] ary, String s) {
    if(s == null)return ary;
    int cnt = 0;
    int idx = find(ary,s);
    while(idx >= 0) {
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

  public static int[] sorted_set_diff(int[] x, int[] y) {
    assert isSorted(x);
    assert isSorted(y);
    int [] res = new int[x.length];
    int j = 0, k = 0;
    for(int i = 0; i < x.length; i++){
      while(j < y.length && y[j] < x[i])j++;
      if(j == y.length || y[j] != x[i])
        res[k++] = x[i];
    }
    return Arrays.copyOf(res,k);
  }
  /*
      This class is written to copy the contents of a frame to a 2-D double array.
   */
  public static class FrameToArray extends MRTask<FrameToArray> {
    int _startColIndex;   // first column index to extract
    int _endColIndex;     // last column index to extract
    int _rowNum;          // number of columns in
    public double[][] _frameContent;

    public FrameToArray(int startCol, int endCol, long rowNum, double[][] frameContent) {
      assert ((startCol >= 0) && (endCol >= startCol) && (rowNum > 0));
      _startColIndex = startCol;
      _endColIndex = endCol;
      _rowNum = (int) rowNum;
      int colNum = endCol-startCol+1;

      if (frameContent == null) { // allocate memory here if user has not provided one
        _frameContent = MemoryManager.malloc8d(_rowNum, colNum);
      } else {  // make sure we are passed the correct size 2-D double array
        assert (_rowNum == frameContent.length && frameContent[0].length == colNum);
        for (int index = 0; index < colNum; index++) { // zero fill use array
          Arrays.fill(frameContent[index], 0.0);
        }
        _frameContent = frameContent;
      }
    }

    @Override public void map(Chunk[] c) {
      assert _endColIndex < c.length;
      int endCol = _endColIndex+1;
      int rowOffset = (int) c[0].start();   // real row index
      int chkRows = c[0]._len;

      for (int rowIndex = 0; rowIndex < chkRows; rowIndex++) {
        for (int colIndex = _startColIndex; colIndex < endCol; colIndex++) {
          _frameContent[rowIndex+rowOffset][colIndex-_startColIndex] = c[colIndex].atd(rowIndex);
        }
      }
    }

    @Override public void reduce(FrameToArray other) {
      ArrayUtils.add(_frameContent, other._frameContent);
    }

    public double[][] getArray() {
      return _frameContent;
    }
  }


  /*
				This class is written to a 2-D array to the frame instead of allocating new memory every time.
	*/
  public static class CopyArrayToFrame extends MRTask<CopyArrayToFrame> {
    int _startColIndex;   // first column index to extract
    int _endColIndex;     // last column index to extract
    int _rowNum;          // number of columns in
    public double[][] _frameContent;

    public CopyArrayToFrame(int startCol, int endCol, long rowNum, double[][] frameContent) {
      assert ((startCol >= 0) && (endCol >= startCol) && (rowNum > 0));

      _startColIndex = startCol;
      _endColIndex = endCol;
      _rowNum = (int) rowNum;

      int colNum = endCol-startCol+1;
      assert (_rowNum == frameContent.length && frameContent[0].length == colNum);

      _frameContent = frameContent;
    }

    @Override public void map(Chunk[] c) {
      assert _endColIndex < c.length;
      int endCol = _endColIndex+1;
      int rowOffset = (int) c[0].start();   // real row index
      int chkRows = c[0]._len;

      for (int rowIndex = 0; rowIndex < chkRows; rowIndex++) {
        for (int colIndex = _startColIndex; colIndex < endCol; colIndex++) {
          c[colIndex].set(rowIndex, _frameContent[rowIndex+rowOffset][colIndex-_startColIndex]);
        }
      }
    }
  }

  /** Create a new frame based on given row data.
   *  @param key   Key for the frame
   *  @param names names of frame columns
   *  @param rows  data given in the form of rows
   *  @return new frame which contains columns named according given names and including given data */
  public static Frame frame(Key<Frame> key, String[] names, double[]... rows) {
    assert names == null || names.length == rows[0].length;
    Futures fs = new Futures();
    Vec[] vecs = new Vec[rows[0].length];
    Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(vecs.length);
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
  public static Frame frame(String[] names, double[]... rows) { return frame(Key.<Frame>make(), names, rows); }
  public static Frame frame(String name, Vec vec) { Frame f = new Frame(); f.add(name, vec); return f; }

  /**
   * Remove b from a, both a,b are assumed to be sorted.
   */
  public static int[] removeSorted(int [] a, int [] b) {
    int [] indeces = new int[b.length];
    indeces[0] = Arrays.binarySearch(a,0,a.length,b[0]);
    if(indeces[0] < 0)
      throw new NoSuchElementException("value " + b[0] + " not found in the first array.");
    for(int i = 1; i < b.length; ++i) {
      indeces[i] = Arrays.binarySearch(a,indeces[i-1],a.length,b[i]);
      if(indeces[i] < 0)
        throw new NoSuchElementException("value " + b[i] + " not found in the first array.");
    }
    return removeIds(a,indeces);
  }

  public static int[] removeIds(int[] x, int[] ids) {
    int [] res = new int[x.length-ids.length];
    int j = 0;
    for(int i = 0; i < x.length; ++i)
      if(j == ids.length || i != ids[j]) res[i-j] = x[i]; else ++j;
    return res;
  }
  public static double[] removeIds(double[] x, int[] ids) {
    double [] res = new double[x.length-ids.length];
    int j = 0;
    for(int i = 0; i < x.length; ++i)
      if(j == ids.length || i != ids[j]) res[i-j] = x[i]; else ++j;
    return res;
  }

  public static boolean hasNzs(double[] x) {
    if(x == null)
      return false;
    for(double d:x)
      if(d != 0) return true;
    return false;
  }

  public static int countNonzeros(double[] beta) {
    int res = 0;
    for(double d:beta)
      if(d != 0)++res;
    return res;
  }

  public static long[] subtract(long n, long[] nums) {
    for (int i=0; i<nums.length; i++) nums[i] = n - nums[i];
    return nums;
  }

  public static <T> T[] remove( T[] ary, int id) {
    if(id < 0 || id >= ary.length) return Arrays.copyOf(ary,ary.length);
    if(id == ary.length-1) return Arrays.copyOf(ary,id);
    if(id == 0) return Arrays.copyOfRange(ary,1,ary.length);
    return append(Arrays.copyOf(ary,id), Arrays.copyOfRange(ary,id+1,ary.length));
  }

  public static byte[] remove(byte[] ary, int id) {
    if(id < 0 || id >= ary.length) return Arrays.copyOf(ary,ary.length);
    if(id == ary.length-1) return Arrays.copyOf(ary,id);
    if(id == 0) return Arrays.copyOfRange(ary,1,ary.length);
    return append(Arrays.copyOf(ary,id), Arrays.copyOfRange(ary,id+1,ary.length));
  }

  public static int[] remove(int[] ary, int id) {
    if(id < 0 || id >= ary.length) return Arrays.copyOf(ary,ary.length);
    if(id == ary.length-1) return Arrays.copyOf(ary,id);
    if(id == 0) return Arrays.copyOfRange(ary,1,ary.length);
    return append(Arrays.copyOf(ary,id), Arrays.copyOfRange(ary,id+1,ary.length));
  }

  public static long[] remove(long[] ary, int id) {
    if(id < 0 || id >= ary.length) return Arrays.copyOf(ary,ary.length);
    if(id == ary.length-1) return Arrays.copyOf(ary,id);
    if(id == 0) return Arrays.copyOfRange(ary,1,ary.length);
    return append(Arrays.copyOf(ary,id), Arrays.copyOfRange(ary,id+1,ary.length));
  }

  public static double[] padUniformly(double[] origPoints, int newLength) {
    int origLength = origPoints.length;
    if (newLength <= origLength || origLength<=1) return origPoints;
    int extraPoints = newLength - origLength;
    int extraPointsPerBin = extraPoints/(origLength-1);
    double[] res = new double[newLength];

    int pos=0;
    int rem = extraPoints - extraPointsPerBin*(origLength-1);
    for (int i=0;i<origLength-1;++i) {
      double startPos = origPoints[i];
      double delta = origPoints[i+1]-startPos;
      int ext = extraPointsPerBin + (i<rem ? 1 : 0);
      res[pos++] = startPos;
      for (int j=0;j<ext;++j)
        res[pos++] = startPos + (j+0.5) / ext * delta;
    }
    res[pos] = origPoints[origLength-1];
    return res;
  }

  // See HistogramTest JUnit for tests
  public static double[] makeUniqueAndLimitToRange(double[] splitPoints, double min, double maxEx) {
    double last= splitPoints[0];
    double[] uniqueValidPoints = new double[splitPoints.length+2];
    int count=0;
    // keep all unique points that are minimally overlapping with min..maxEx
    for (int i = 0; i< splitPoints.length; ++i) {
      double pos = splitPoints[i];
      // first one
      if (pos >= min && count==0) {
        uniqueValidPoints[count++]= min;
        if (pos> min) uniqueValidPoints[count++]=pos;
        last=pos;
      }
      //last one
      else if (pos > maxEx) {
        break;
      }
      // regular case: add to uniques
      else if (pos > min && pos < maxEx && (i==0 || pos != last)) {
        uniqueValidPoints[count++] = pos;
        last = pos;
      }
    }
    if (count==0) {
      return new double[]{min};
    }
    return Arrays.copyOfRange(uniqueValidPoints,0,count);
  }

  // See HistogramTest JUnit for tests
  public static double[] limitToRange(double[] sortedSplitPoints, double min, double maxEx) {
    int start=Arrays.binarySearch(sortedSplitPoints, min);
    if (start<0) start=-start-1;
    // go back one more to return at least one value
    if (start==sortedSplitPoints.length) start--;
    // go back one more to include the min (inclusive)
    if (sortedSplitPoints[start] > min && start>0) start--;
    assert(start>=0);
    assert(sortedSplitPoints[start] <= min);

    int end=Arrays.binarySearch(sortedSplitPoints, maxEx);
    if (end<0) end=-end-1;
    assert(end>0 && end<= sortedSplitPoints.length): "End index ("+end+") should be > 0 and <= split points size ("+sortedSplitPoints.length+"). "+collectArrayInfo(sortedSplitPoints);
    assert(end>=start): "End index ("+end+") should be >= start index ("+start+"). " + collectArrayInfo(sortedSplitPoints);
    assert(sortedSplitPoints[end-1] < maxEx): "Split valued at index end-1 ("+sortedSplitPoints[end-1]+") should be < maxEx value ("+maxEx+"). "+collectArrayInfo(sortedSplitPoints);

    return Arrays.copyOfRange(sortedSplitPoints,start,end);
  }
  
  private static String collectArrayInfo(double[] array){
    StringBuilder info = new StringBuilder("Array info - length: "+array.length + " values: ");
    for(double value: array){
      info.append(value+" ");
    }
    return info.toString();
  }

  public static double[] extractCol(int i, double[][] ary) {
    double [] res = new double[ary.length];
    for(int j = 0; j < ary.length; ++j)
      res[j] = ary[j][i];
    return res;
  }

  public static long encodeAsLong(byte[] b) {
    return encodeAsLong(b, 0, b.length);
  }
  public static long encodeAsLong(byte[] b, int off, int len) {
    assert len <= 8 : "Cannot encode more then 8 bytes into long: len = " + len;
    long r = 0;
    int shift = 0;
    for(int i = 0; i < len; i++) {
      r |= (b[i + off] & 0xFFL) << shift;
      shift += 8;
    }
    return r;
  }

  public static int encodeAsInt(byte[] b) {
    assert b.length == 4 : "Cannot encode more than 4 bytes into int: len = " + b.length;
    return (b[0]&0xFF)+((b[1]&0xFF)<<8)+((b[2]&0xFF)<<16)+((b[3]&0xFF)<<24);
  }

  public static int encodeAsInt(byte[] bs, int at) {
    if (at + 4 > bs.length) throw new IndexOutOfBoundsException("Cannot encode more than 4 bytes into int: len = " + bs.length + ", pos=" + at);
    return (bs[at]&0xFF)+((bs[at+1]&0xFF)<<8)+((bs[at+2]&0xFF)<<16)+((bs[at+3]&0xFF)<<24);
  }

  public static byte[] decodeAsInt(int what, byte[] bs, int at) {
    if (bs.length < at + 4) throw new IndexOutOfBoundsException("Wrong position " + at + ", array length is " + bs.length);
    for (int i = at; i < at+4 && i < bs.length; i++) {
      bs[i] = (byte)(what&0xFF);
      what >>= 8;
    }
    return bs;
  }

  /** Transform given long numbers into byte array.
   * Highest 8-bits of the first long will stored in the first field of returned byte array.
   *
   * Example:
   * 0xff18000000000000L -> new byte[] { 0xff, 0x18, 0, 0, 0, 0, 0, 0}
   */
  public static byte[] toByteArray(long ...nums) {
    if (nums == null || nums.length == 0) return EMPTY_BYTE_ARRAY;
    byte[] result = new byte[8*nums.length];
    int c = 0;
    for (long n : nums) {
      for (int i = 0; i < 8; i++) {
        result[c*8 + i] = (byte) ((n >>> (56 - 8 * i)) & 0xFF);
      }
      c++;
    }
    return result;
  }

  public static byte[] toByteArray(int[] ary) {
    byte[] r = new byte[ary.length];
    for (int i = 0; i < ary.length; i++) {
      r[i] = (byte) (ary[i] & 0xff);
    }
    return r;
  }

  public static boolean equalsAny(long value, long...lhs) {
    if (lhs == null || lhs.length == 0) return false;
    for (long lhValue : lhs) {
      if (value == lhValue) return true;
    }
    return false;
  }

  /**
   * Convert an array of primitive types into an array of corresponding boxed types. Due to quirks of Java language
   * this cannot be done in any generic way -- there should be a separate function for each use case...
   * @param arr input array of `char`s
   * @return output array of `Character`s
   */
  public static Character[] box(char[] arr) {
    Character[] res = new Character[arr.length];
    for (int i = 0; i < arr.length; i++)
      res[i] = arr[i];
    return res;
  }

  /**
   * Convert an ArrayList of Integers to a primitive int[] array.
   */
  public static int[] toPrimitive(ArrayList<Integer> arr) {
    int[] res = new int[arr.size()];
    for (int i = 0; i < res.length; i++)
      res[i] = arr.get(i);
    return res;
  }

  public static boolean isSorted(int[] vals) {
    for (int i = 1; i < vals.length; ++i)
      if (vals[i - 1] > vals[i]) return false;
    return true;
  }
  public static boolean isSorted(double[] vals) {
    for (int i = 1; i < vals.length; ++i)
      if (vals[i - 1] > vals[i]) return false;
    return true;
  }

  public static byte[] constAry(int len, byte b) {
    byte[] ary = new byte[len];
    Arrays.fill(ary, b);
    return ary;
  }

  public static double[] constAry(int len, double c) {
    double[] ary = new double[len];
    Arrays.fill(ary, c);
    return ary;
  }

  public static double[] toDouble(float[] floats) {
    if (floats == null)
      return null;
    double[] ary = new double[floats.length];
    for (int i = 0; i < floats.length; i++)
      ary[i] = floats[i];
    return ary;
  }

  public static double[] toDouble(int[] ints) {
    if (ints == null)
      return null;
    double[] ary = new double[ints.length];
    for (int i = 0; i < ints.length; i++)
      ary[i] = ints[i];
    return ary;
  }

  public static boolean isInstance(Object object, Class[] comparedClasses) {
    for (Class c : comparedClasses) {
      if (c.isInstance(object)) return true;
    }
    return false;
  }
  

  /**
   * Count number of occurrences of element in given array.
   *
   * @param array   array in which number of occurrences should be counted.
   * @param element element whose occurrences should be counted.
   *
   * @return  number of occurrences of element in given array.
   */
  public static int occurrenceCount(byte[] array, byte element) {
    int cnt = 0;

    for (byte b : array)
      if (b == element)
        cnt++;

    return cnt;
  }

  public static String findLongestCommonPrefix(String inputArray[]) {
    String referenceWord = inputArray[0];
    String result = "";
    for (int j = 1; j <= referenceWord.length(); j++) {
      String prefix = referenceWord.substring(0, j);
      if (isPresentInAllWords(prefix, inputArray) && result.length() < prefix.length()) {
        result = prefix;
      }
    }
    return result;
  }

  private static boolean isPresentInAllWords(String prefix, String[] words) {
    int n = words.length, k;
    for (k = 1; k < n; k++) {
      if (!words[k].startsWith(prefix)) {
        return false;
      }
    }
    return true;
  }

  /**
   *
   * @return Array dimension of array.length with values from uniform distribution with bounds taken from array.
   *         For example first value of the result is from Unif(First column min value, First column max value)
   */
  public static double[] uniformDistFromArray(double[][] array, long seed) {
    double[] p = new double[array.length];
    Random random = RandomUtils.getRNG(seed);

    for (int col = 0; col < array.length; col++) {
      double[] minMax = ArrayUtils.minMaxValue(array[col]);
      double min = minMax[0];
      double max = minMax[1];
      p[col] = min + random.nextDouble() * (max - min);
    }

    return p;
  }

  /*
   * Linear interpolation values in the array with Double.NaN values.
   * The interpolation always starts from 0. 
   * The last element of array cannot be Double.NaN.
   *
   * @param array input array with Double.NaN values
   */
  public static void interpolateLinear(double[] array){
    assert array.length > 0 && !Double.isNaN(array[array.length-1]);
    if(array.length == 1){
      return;
    }
    List<Integer> nonNullIdx = new ArrayList<>();
    List<Integer> steps = new ArrayList<>();
    int tmpStep = 0;
    for (int i = 0; i < array.length; i++) {
      if (!Double.isNaN(array[i])) {
        nonNullIdx.add(i);
        if (tmpStep != 0) {
          steps.add(tmpStep);
        }
        tmpStep = 0;
      }
      else {
        tmpStep++;
      }
    }

    double start = Double.NaN, end = Double.NaN, step = Double.NaN, mean = Double.NaN;
    for (int i=0; i<array.length; i++) {
      // begin always with 0
      if(i == 0 && Double.isNaN(array[i])) {
        start = 0;
        end = array[nonNullIdx.get(0)];
        step = 1.0 / (double)(steps.get(0) + 1);
        mean = step;
        array[i] = start * (1 - mean) + end * mean;
        mean += step;
      } else if (!Double.isNaN(array[i]) && nonNullIdx.size() > 1 && steps.size() > 0) {
        start = array[nonNullIdx.get(0)];
        end = array[nonNullIdx.get(1)];
        step = 1.0 / (double)(steps.get(0) + 1);
        mean = step;
        nonNullIdx.remove(0);
        steps.remove(0);
      } else if (Double.isNaN(array[i])) {
        array[i] = start * (1 - mean) + end * mean;
        mean += step;
      }
    }
  }
}
