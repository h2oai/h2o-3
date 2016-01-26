package hex.genmodel;

import hex.ModelCategory;
import water.genmodel.IGeneratedModel;

import java.io.Serializable;
import java.util.*;

/** This is a helper class to support Java generated models. */
public abstract class GenModel implements IGenModel, IGeneratedModel, Serializable {

  /** Column names; last is response for supervised models */
  public final String[] _names;

  /** Categorical/factor/enum mappings, per column.  Null for non-enum cols.
   *  Columns match the post-init cleanup columns.  The last column holds the
   *  response col enums for SupervisedModels.  */
  public final String[][] _domains;


  public GenModel( String[] names, String[][] domains ) { _names = names; _domains = domains; }

  @Override public boolean isSupervised() {
    // FIXME: can be derived directly from model category?
    return false;
  }
  @Override public int nfeatures() {
    return _names.length;
  }
  @Override public int nclasses() {
    return 0;
  }
  @Override public int getNumCols() {
    return nfeatures();
  }
  @Override public int getResponseIdx() {
    if (!isSupervised())
      throw new UnsupportedOperationException("Cannot provide response index for unsupervised models.");
    return _domains.length - 1;
  }
  @Override public String getResponseName() {
    throw new UnsupportedOperationException("getResponseName is not supported in h2o-dev!");
  }
  @Override public int getNumResponseClasses() {
    if (isClassifier())
      return nclasses();
    else
      throw new UnsupportedOperationException("Cannot provide number of response classes for non-classifiers.");
  }
  @Override public String[] getNames() {
    return _names;
  }
  @Override public int getColIdx(String name) {
    String[] names = getNames();
    for (int i=0; i<names.length; i++) if (names[i].equals(name)) return i;
    return -1;
  }
  @Override public int getNumClasses(int colIdx) {
    String[] domval = getDomainValues(colIdx);
    return domval!=null?domval.length:-1;
  }
  @Override public String[] getDomainValues(String name) {
    int colIdx = getColIdx(name);
    return colIdx != -1 ? getDomainValues(colIdx) : null;
  }
  @Override public String[] getDomainValues(int i) {
    return getDomainValues()[i];
  }
  @Override public int mapEnum(int colIdx, String enumValue) {
    String[] domain = getDomainValues(colIdx);
    if (domain==null || domain.length==0) return -1;
    for (int i=0; i<domain.length;i++) if (enumValue.equals(domain[i])) return i;
    return -1;
  }
  @Override
  public String[][] getDomainValues() {
    return _domains;
  }

  @Override public boolean isClassifier() {
    ModelCategory cat = getModelCategory();
    return cat == ModelCategory.Binomial || cat == ModelCategory.Multinomial;
  }

  @Override public boolean isAutoEncoder() {
    ModelCategory cat = getModelCategory();
    return cat == ModelCategory.AutoEncoder;
  }

  @Override public int getPredsSize() {
    return isClassifier() ? 1 + getNumResponseClasses() : 2;
  }

  /** ??? */
  public String getHeader() { return null; }

  /** Takes a HashMap mapping column names to doubles.
   *  <p>
   *  Looks up the column names needed by the model, and places the doubles into
   *  the data array in the order needed by the model. Missing columns use NaN.
   *  </p>
   */
  public double[] map( Map<String, Double> row, double data[] ) {
    String[] colNames = _names;
    for( int i=0; i<nfeatures(); i++ ) {
      Double d = row.get(colNames[i]);
      data[i] = d==null ? Double.NaN : d;
    }
    return data;
  }

  @Override
  public float[] predict(double[] data, float[] preds) {
    return predict(data, preds, 0);
  }

  @Override
  public float[] predict(double[] data, float[] preds, int maxIters) {
    throw new UnsupportedOperationException("Unsupported operation - use score0 method!");
  }

  /** Subclasses implement the scoring logic.  The data is pre-loaded into a
   *  re-used temp array, in the order the model expects.  The predictions are
   *  loaded into the re-used temp array, which is also returned.  This call
   *  exactly matches the hex.Model.score0, but uses the light-weight
   *  GenModel class. */
  abstract public double[] score0( double[] data, double[] preds );

  // Does the mapping lookup for every row, no allocation.
  // data and preds arrays are pre-allocated and can be re-used for every row.
  public double[] score0( Map<String, Double> row, double data[], double preds[] ) {
    return score0(map(row,data),preds);
  }

  // Does the mapping lookup for every row.
  // preds array is pre-allocated and can be re-used for every row.
  // Allocates a double[] for every row.
  public double[] score0( Map<String, Double> row, double preds[] ) {
    return score0(map(row,new double[nfeatures()]),preds);
  }

  // Does the mapping lookup for every row.
  // Allocates a double[] and a float[] for every row.
  public double[] score0( Map<String, Double> row ) {
    return score0(map(row,new double[nfeatures()]),new double[nclasses()+1]);
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
    double probsum=0;
    for( int c=1; c<scored.length; c++ ) {
      final double original_fraction = priorClassDist[c-1];
      final double oversampled_fraction = modelClassDist[c-1];
      assert(!Double.isNaN(scored[c])) : "Predicted NaN class probability";
      if (original_fraction != 0 && oversampled_fraction != 0) scored[c] *= original_fraction / oversampled_fraction;
      probsum += scored[c];
    }
    if (probsum>0) for (int i=1;i<scored.length;++i) scored[i] /= probsum;
    return scored;
  }

  /** Utility function to get a best prediction from an array of class
   *  prediction distribution.  It returns the index of the max. probability (if that exists).
   *  In the case of ties, it samples from the tied classes with the likelihood given by the prior probabilities.
   *  @param preds an array of prediction distribution.  Length of arrays is equal to a number of classes+1.
   *  @param priorClassDist prior class probabilities (used to break ties)
   *  @param data Test data
   *  @param threshold threshold for binary classifier
   * @return the best prediction (index of class, zero-based)
   */
  public static int getPrediction(double[] preds, double[] priorClassDist, double data[], double threshold) {
    if (preds.length == 3) {
      return (preds[2] >= threshold) ? 1 : 0; //no tie-breaking
    }
    List<Integer> ties = new ArrayList<>();
    ties.add(0);
    int best=1, tieCnt=0;   // Best class; count of ties
    for( int c=2; c<preds.length; c++) {
      if( preds[best] < preds[c] ) {
        best = c;               // take the max index
        tieCnt=0;               // No ties
      } else if (preds[best] == preds[c]) {
        tieCnt++;               // Ties
        ties.add(c-1);
      }
    }
    if( tieCnt==0 ) return best-1; // Return zero-based best class

    long hash = 0;              // hash for tie-breaking
    if( data != null )
      for( double d : data ) hash ^= Double.doubleToRawLongBits(d) >> 6; // drop 6 least significants bits of mantissa (layout of long is: 1b sign, 11b exp, 52b mantisa)

    if (priorClassDist!=null) {
      // Tie-breaking based on prior probabilities
      // Example: probabilities are 0.4, 0.2, 0.4 for a 3-class problem with priors 0.7, 0.1, 0.2
      // Probability of predicting class 1 should be higher than for class 3 based on the priors
      double sum = 0;
      for (Integer i : ties) { //ties = [0, 2]
        sum += priorClassDist[i]; //0.7 + 0.2
      }
      // sum is now 0.9
      Random rng = new Random(hash);
      double tie = rng.nextDouble(); //for example 0.4135 -> should pick the first of the ties, since it occupies 0.7777 = 0.7/0.9 of the 0...1 range, and 0.4135 < 0.7777
      double partialSum = 0;
      for (Integer i : ties) {
        partialSum += priorClassDist[i] / sum; //0.7777 at first iteration, 1.0000 at second iteration
        if (tie <= partialSum)
          return i;
      }
    }

    // Tie-breaking logic (should really never be triggered anymore)
    double res = preds[best];    // One of the tied best results
    int idx = (int)hash%(tieCnt+1);  // Which of the ties we'd like to keep
    for( best=1; best<preds.length; best++)
      if( res == preds[best] && --idx < 0 )
        return best-1;          // Return best
    throw new RuntimeException("Should Not Reach Here");
  }

  // Utility to do bitset lookup
  public static boolean bitSetContains(byte[] bits, int bitoff, int num ) {
    if (Integer.MIN_VALUE == num) { //Missing value got cast'ed to Integer.MIN_VALUE via (int)-Float.MAX_VALUE in GenModel.*_fclean
      num = 0; // all missing values are treated the same as the first enum level //FIXME
    }
    assert num >= 0;
    num -= bitoff;
    return (num >= 0) && (num < (bits.length<<3)) &&
      (bits[num >> 3] & ((byte)1 << (num & 7))) != 0;
  }

  // --------------------------------------------------------------------------
  // KMeans utilities
  // For KMeansModel scoring; just the closest cluster center
  public static int KMeans_closest(double[][] centers, double[] point, String[][] domains, double[] means, double[] mults) {
    int min = -1;
    double minSqr = Double.MAX_VALUE;
    for( int cluster = 0; cluster < centers.length; cluster++ ) {
      double sqr = KMeans_distance(centers[cluster],point,domains,means,mults);
      if( sqr < minSqr ) {      // Record nearest cluster center
        min = cluster;
        minSqr = sqr;
      }
    }
    return min;
  }

  // only used for GLRM initialization - inverse of distance to each cluster center normalized to sum to one
  public static double[] KMeans_simplex(double[][] centers, double[] point, String[][] domains, double[] means, double[] mults) {
    double[] dist = new double[centers.length];
    double sum = 0, inv_sum = 0;
    for( int cluster = 0; cluster < centers.length; cluster++ ) {
      dist[cluster] = KMeans_distance(centers[cluster],point,domains,means,mults);
      sum += dist[cluster];
      inv_sum += 1.0 / dist[cluster];
    }

    double[] ratios = new double[centers.length];
    if (sum == 0) {   // In degenerate case where all cluster centers identical to point, pick one at random
      Random rng = new Random();
      int idx = rng.nextInt(centers.length);
      ratios[idx] = 1;
    } else {
      // Is the point identical to an existing cluster center?
      int idx = -1;
      for (int cluster = 0; cluster < centers.length; cluster++) {
        if(dist[cluster] == 0) {
          idx = cluster;
          break;
        }
      }

      if(idx == -1) {  // If not, take ratios as inverse of cluster distance normalized to sum to one
        for (int cluster = 0; cluster < centers.length; cluster++)
          ratios[cluster] = 1.0 / (dist[cluster] * inv_sum);
      } else   // Otherwise, just assign directly to closest cluster
        ratios[idx] = 1;
    }
    return ratios;
  }

  // only used for metric builder - uses float[] and fills up colSum & colSumSq arrays, otherwise the same as method below.
  // WARNING - if changing this code - also change the code below
  public static double KMeans_distance(double[] center, float[] point, String[][] domains, double[] means, double[] mults,
                                       double[] colSum, double[] colSumSq) {
    double sqr = 0;             // Sum of dimensional distances
    int pts = point.length;     // Count of valid points

    for(int column = 0; column < center.length; column++) {
      float d = point[column];
      if( Float.isNaN(d) ) { pts--; continue; }
      if( domains[column] != null ) { // Categorical?
        if( d != center[column] ) {
          sqr += 1.0;           // Manhattan distance
        }
      } else {                  // Euclidean distance
        if( mults != null ) {   // Standardize if requested
          d -= means[column];
          d *= mults[column];
        }
        double delta = d - center[column];
        sqr += delta * delta;
      }
      colSum[column] += d;
      colSumSq[column] += d*d;
    }
    // Scale distance by ratio of valid dimensions to all dimensions - since
    // we did not add any error term for the missing point, the sum of errors
    // is small - ratio up "as if" the missing error term is equal to the
    // average of other error terms.  Same math another way:
    //   double avg_dist = sqr / pts; // average distance per feature/column/dimension
    //   sqr = sqr * point.length;    // Total dist is average*#dimensions
    if( 0 < pts && pts < point.length ) {
      double scale = point.length / pts;
      sqr *= scale;
//      for (int i=0; i<colSum.length; ++i) {
//        colSum[i] *= Math.sqrt(scale);
//        colSumSq[i] *= scale;
//      }
    }
    return sqr;
  }

  // WARNING - if changing this code - also change the code above
  public static double KMeans_distance(double[] center, double[] point, String[][] domains, double[] means, double[] mults) {
    double sqr = 0;             // Sum of dimensional distances
    int pts = point.length;     // Count of valid points

    for(int column = 0; column < center.length; column++) {
      double d = point[column];
      if( Double.isNaN(d) ) { pts--; continue; }
      if( domains[column] != null ) { // Categorical?
        if( d != center[column] )
          sqr += 1.0;           // Manhattan distance
      } else {                  // Euclidean distance
        if( mults != null ) {   // Standardize if requested
          d -= means[column];
          d *= mults[column];
        }
        double delta = d - center[column];
        sqr += delta * delta;
      }
    }
    // Scale distance by ratio of valid dimensions to all dimensions - since
    // we did not add any error term for the missing point, the sum of errors
    // is small - ratio up "as if" the missing error term is equal to the
    // average of other error terms.  Same math another way:
    //   double avg_dist = sqr / pts; // average distance per feature/column/dimension
    //   sqr = sqr * point.length;    // Total dist is average*#dimensions
    if( 0 < pts && pts < point.length )
      sqr *= point.length / pts;
    return sqr;
  }

  // --------------------------------------------------------------------------
  // SharedTree utilities

  // Tree scoring; NaNs always "go left": count as -Float.MAX_VALUE
  public static double[] SharedTree_clean( double[] data ) {
    double[] fs = new double[data.length];
    for( int i=0; i<data.length; i++ )
      fs[i] = Double.isNaN(data[i]) ? -Double.MAX_VALUE : data[i];
    return fs;
  }

  // Build a class distribution from a log scale.
  // Because we call Math.exp, we have to be numerically stable or else we get
  // Infinities, and then shortly NaN's.  Rescale the data so the largest value
  // is +/-1 and the other values are smaller.  See notes here:
  // http://www.hongliangjie.com/2011/01/07/logsum/
  public static double log_rescale(double[] preds) {
    // Find a max
    double maxval=Double.NEGATIVE_INFINITY;
    for( int k=1; k<preds.length; k++) maxval = Math.max(maxval,preds[k]);
    assert !Double.isInfinite(maxval) : "Something is wrong with GBM trees since returned prediction is " + Arrays.toString(preds);
    // exponentiate the scaled predictions; keep a rolling sum
    double dsum=0;
    for( int k=1; k<preds.length; k++ )
      dsum += (preds[k]=Math.exp(preds[k]-maxval));
    return dsum;                // Return rolling sum; predictions are log-scaled
  }

  // Build a class distribution from a log scale; find the top prediction
  public static void GBM_rescale(double[] preds) {
    double sum = log_rescale(preds);
    for( int k=1; k<preds.length; k++ ) preds[k] /= sum;
  }

  // --------------------------------------------------------------------------
  // GLM utilities
  public static double GLM_identityInv( double x ) { return x; }
  public static double GLM_logitInv( double x ) { return 1.0 / (Math.exp(-x) + 1.0); }
  public static double GLM_logInv( double x ) { return Math.exp(x); }
  public static double GLM_inverseInv( double x ) {  double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x); return 1.0 / xx; }
  public static double GLM_tweedieInv( double x, double tweedie_link_power ) {
    return tweedie_link_power == 0?Math.max(2e-16,Math.exp(x)):Math.pow(x, 1.0/ tweedie_link_power);
  }
}