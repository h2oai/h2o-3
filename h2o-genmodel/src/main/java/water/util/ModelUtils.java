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

  public static int getPrediction(double[] preds) {
    int best=1, tieCnt=0;   // Best class; count of ties
    for( int c=2; c<preds.length; c++) {
      if( preds[best] < preds[c] ) {
        best = c;               // take the max index
        tieCnt=0;               // No ties
      } else if (preds[best] == preds[c]) {
        tieCnt++;               // Ties
      }
    }
    if( tieCnt==0 ) return best-1; // Return zero-based best class
    // Tie-breaking logic
    double res = preds[best];   // One of the tied best results
    int idx = (int)Double.doubleToRawLongBits(res)%(tieCnt+1);   // Which of the ties we'd like to keep
    for( best=1; best<preds.length; best++)
      if( res == preds[best] && --idx < 0 )
        return best-1;          // Return best
    throw new RuntimeException("Unreachable code reached!");
  }

  /**
   * Correct a given list of class probabilities produced as a prediction by a model back to prior class distribution
   *
   * <p>The implementation is based on Eq. (27) in  <a href="http://gking.harvard.edu/files/0s.pdf">the paper</a>.
   *
   * @param scored list of class probabilities beginning at index 1
   * @param priorClassDist original class distribution
   * @param modelClassDist class distribution used for model building (e.g., data was oversampled)
   * @return corrected list of probabilities
   */
  public static double[] correctProbabilities(double[] scored, double[] priorClassDist, double[] modelClassDist) {
    if( priorClassDist == modelClassDist ) return scored; // Shortcut if not correcting for probs
    double probsum=0;
    for( int c=1; c<scored.length; c++ ) {
      final double original_fraction = priorClassDist[c-1];
      final double oversampled_fraction = modelClassDist[c-1];
      assert(!Double.isNaN(scored[c]));
      if (original_fraction != 0 && oversampled_fraction != 0) scored[c] *= original_fraction / oversampled_fraction;
      probsum += scored[c];
    }
    if (probsum>0) for (int i=1;i<scored.length;++i) scored[i] /= probsum;
    return scored;
  }


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
