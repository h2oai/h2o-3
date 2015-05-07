package water.util;

import java.util.*;

/**
 * Shared static code to support modeling, prediction, and scoring.
 *
 * <p>Used by interpreted models as well as by generated model code.</p>
 *
 * <p><strong>WARNING:</strong> The class should have no other H2O dependencies
 * since it is provided for generated code as h2o-model.jar which contains
 * only a few files.</p>
 *
 */
public class ModelUtils {

  /**
   * Sample out-of-bag rows with given rate with help of given sampler.
   * It returns array of sampled rows. The first element of array contains a number
   * of sampled rows. The returned array can be larger than number of returned sampled
   * elements.
   *
   * @param nrows number of rows to sample from.
   * @param rate sampling rate
   * @param sampler random "dice"
   * @return an array contains numbers of sampled rows. The first element holds a number of sampled rows. The array length
   * can be greater than number of sampled rows.
   */
  public static int[] sampleOOBRows(int nrows, float rate, Random sampler) {
    return sampleOOBRows(nrows, rate, sampler, new int[2+Math.round((1f-rate)*nrows*1.2f+0.5f)]);
  }
  /**
   * In-situ version of {@link #sampleOOBRows(int, float, Random)}.
   *
   * @param oob an initial array to hold sampled rows. Can be internally reallocated.
   * @return an array containing sampled rows.
   *
   * @see #sampleOOBRows(int, float, Random)
   */
  public static int[] sampleOOBRows(int nrows, float rate, Random sampler, int[] oob) {
    int oobcnt = 0; // Number of oob rows
    Arrays.fill(oob, 0);
    for(int row = 0; row < nrows; row++) {
      if (sampler.nextFloat() >= rate) { // it is out-of-bag row
        oob[1+oobcnt++] = row;
        if (1+oobcnt>=oob.length) oob = Arrays.copyOf(oob, Math.round(1.2f*nrows+0.5f)+2);
      }
    }
    oob[0] = oobcnt;
    return oob;
  }
}
