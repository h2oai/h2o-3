package hex.genmodel;

import java.util.Arrays;
import java.util.Map;

/** This is a helper class to support Java generated models. */
public abstract class GenModel {
  public static enum ModelCategory {
    Unknown,
    Binomial,
    Multinomial,
    Regression,
    Clustering,
    AutoEncoder,
    DimReduction
  }

  /** Column names; last is response for supervised models */
  public final String[] _names; 

  /** Categorical/factor/enum mappings, per column.  Null for non-enum cols.
   *  Columns match the post-init cleanup columns.  The last column holds the
   *  response col enums for SupervisedModels.  */
  public final String _domains[][];

  public GenModel( String[] names, String domains[][] ) { _names = names; _domains = domains; }

  // Base methods are correct for unsupervised models.  All overridden in GenSupervisedModel
  public boolean isSupervised() { return false; }  // Overridden in GenSupervisedModel
  public int nfeatures() { return _names.length; } // Overridden in GenSupervisedModel
  public int nclasses() { return 0; }              // Overridden in GenSupervisedModel

  abstract public ModelCategory getModelCategory();

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

  /** Utility function to get a best prediction from an array of class
   *  prediction distribution.  It returns index of max value if predicted
   *  values are unique.  In the case of tie, the implementation solve it in
   *  pseudo-random way.
   *  @param preds an array of prediction distribution.  Length of arrays is equal to a number of classes+1.
   *  @return the best prediction (index of class, zero-based)
   */
  public static int getPrediction( double[] preds, double data[] ) {
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
    double res = preds[best];    // One of the tied best results
    long hash = 0;              // hash for tie-breaking
    if( data != null )
      for( double d : data ) hash ^= Double.doubleToRawLongBits(d) >> 6; // drop 6 least significants bits of mantisa (layout of long is: 1b sign, 11b exp, 52b mantisa)
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
  public static void GBM_rescale(double[] data, double[] preds) { 
    double sum = log_rescale(preds);
    for( int k=1; k<preds.length; k++ ) preds[k] /= sum;
    preds[0] = getPrediction(preds, data); 
  }
}
