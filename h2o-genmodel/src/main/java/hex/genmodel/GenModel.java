package hex.genmodel;

import hex.ModelCategory;
import water.genmodel.IGeneratedModel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.List;

/**
 * This is a helper class to support Java generated models.
 */
public abstract class GenModel implements IGenModel, IGeneratedModel, Serializable {

  /** Column names; last is response for supervised models */
  public final String[] _names;

  /** Categorical (factor/enum) mappings, per column.  Null for non-enum cols.
   *  Columns match the post-init cleanup columns.  The last column holds the
   *  response col enums for SupervisedModels.  */
  public final String[][] _domains;

  /** Name of the response column used for training (only for supervised models). */
  public final String _responseColumn;

  /** Name of the column with offsets (used for certain types of models). */
  public String _offsetColumn;


  public GenModel(String[] names, String[][] domains, String responseColumn) {
    _names = names;
    _domains = domains;
    _responseColumn = responseColumn;
  }

  /**
   * @deprecated This constructor is deprecated and will be removed in a future version.
   *             use {@link #GenModel(String[] names, String[][] domains, String responseColumn)()} instead.
   */
  @Deprecated
  public GenModel(String[] names, String[][] domains) {
    this(names, domains, null);
  }

  //--------------------------------------------------------------------------------------------------------------------
  // IGenModel interface
  //--------------------------------------------------------------------------------------------------------------------

  /** Returns true for supervised models. */
  @Override public boolean isSupervised() {
    return false;
  }

  /** Returns number of input features. */
  @Override public int nfeatures() {
    return _names.length;
  }

  /** Returns number of output classes for classifiers, 1 for regression models, and 0 for unsupervised models. */
  @Override public int nclasses() {
    return 0;
  }

  /** Returns this model category. */
  @Override public abstract ModelCategory getModelCategory();

  /** Override this for models that may produce results in different categories. */
  @Override public EnumSet<ModelCategory> getModelCategories() {
    return EnumSet.of(getModelCategory());
  }


  //--------------------------------------------------------------------------------------------------------------------
  // IGeneratedModel interface
  //--------------------------------------------------------------------------------------------------------------------

  @Override public abstract String getUUID();

  /** Returns number of columns used as input for training (i.e., exclude response and offset columns). */
  @Override public int getNumCols() {
    return nfeatures();
  }

  /** The names of all columns used, including response and offset columns. */
  @Override public String[] getNames() {
    return _names;
  }

  /** The name of the response column. */
  @Override public String getResponseName() {
    // Note: _responseColumn is not set when deprecated constructor GenModel(String[] names, String[][] domains) is used
    int r = getResponseIdx();
    return r < _names.length ? _names[r] : _responseColumn;
  }

  /** Returns the index of the response column inside getDomains(). */
  @Override public int getResponseIdx() {
    if (!isSupervised())
      throw new UnsupportedOperationException("Cannot provide response index for unsupervised models.");
    return _domains.length - 1;
  }

  /** Get number of classes in the given column.
   * Return number greater than zero if the column is categorical or -1 if the column is numeric. */
  @Override public int getNumClasses(int colIdx) {
    String[] domval = getDomainValues(colIdx);
    return domval != null? domval.length : -1;
  }

  /** Return a number of classes in response column. */
  @Override public int getNumResponseClasses() {
    if (!isClassifier())
      throw new UnsupportedOperationException("Cannot provide number of response classes for non-classifiers.");
    return nclasses();
  }

  /** Returns true if this model represents a classifier, else it is used for regression. */
  @Override public boolean isClassifier() {
    ModelCategory cat = getModelCategory();
    return cat == ModelCategory.Binomial || cat == ModelCategory.Multinomial || cat == ModelCategory.Ordinal;
  }

  /** Returns true if this model represents an AutoEncoder. */
  @Override public boolean isAutoEncoder() {
    return getModelCategory() == ModelCategory.AutoEncoder;
  }

  /** Gets domain of the given column. */
  @Override public String[] getDomainValues(String name) {
    int colIdx = getColIdx(name);
    return colIdx != -1 ? getDomainValues(colIdx) : null;
  }

  /** Returns domain values for the i-th column. */
  @Override public String[] getDomainValues(int i) {
    return getDomainValues()[i];
  }

  /** Returns domain values for all columns, including the response column. */
  @Override public String[][] getDomainValues() {
    return _domains;
  }

  /** Returns index of a column with given name, or -1 if the column is not found. */
  @Override public int getColIdx(String name) {
    String[] names = getNames();
    for (int i = 0; i < names.length; i++) if (names[i].equals(name)) return i;
    return -1;
  }

  /** Maps given column's categorical to the integer used by this model (returns -1 if mapping not found). */
  @Override public int mapEnum(int colIdx, String enumValue) {
    String[] domain = getDomainValues(colIdx);
    if (domain != null)
      for (int i = 0; i < domain.length; i++)
        if (enumValue.equals(domain[i]))
          return i;
    return -1;
  }

  /** Returns the expected size of preds array which is passed to `predict(double[], double[])` function. */
  @Override public int getPredsSize() {
    return isClassifier()? 1 + getNumResponseClasses() : 2;
  }

  public int getPredsSize(ModelCategory mc) {
    return (mc == ModelCategory.DimReduction)? nclasses() :getPredsSize();
  }

  public static String createAuxKey(String k) {
    return k + ".aux";
  }

  /*
  @Override
  public float[] predict(double[] data, float[] preds) {
    return predict(data, preds, 0);
  }

  @Override
  public float[] predict(double[] data, float[] preds, int maxIters) {
    throw new UnsupportedOperationException("Unsupported operation - use score0 method!");
  }
  */


  //--------------------------------------------------------------------------------------------------------------------

  /** Takes a HashMap mapping column names to doubles.
   *  <p>
   *  Looks up the column names needed by the model, and places the doubles into
   *  the data array in the order needed by the model. Missing columns use NaN.
   *  </p>
   */
  /*
  public double[] map(Map<String, Double> row, double data[]) {
    for (int i = 0; i < nfeatures(); i++) {
      Double d = row.get(_names[i]);
      data[i] = d==null ? Double.NaN : d;
    }
    return data;
  }
  */

  /** Subclasses implement the scoring logic.  The data is pre-loaded into a
   *  re-used temp array, in the order the model expects.  The predictions are
   *  loaded into the re-used temp array, which is also returned.  This call
   *  exactly matches the hex.Model.score0, but uses the light-weight
   *  GenModel class. */
  public abstract double[] score0(double[] row, double[] preds);

  public double[] score0(double[] row, double offset, double[] preds) {
    throw new UnsupportedOperationException("`offset` column is not supported");
  }

  /** Subclasses implement calibration of class probabilities. The input is array of
   *  predictions returned by the scoring function (score0). Supports classification
   *  models that were trained with calibration enabled. Original probabilities
   *  in the predictions array are overwritten by their corresponding calibrated
   *  counterparts. Return false if model doesn't support calibration.
   */
  public boolean calibrateClassProbabilities(double preds[]) {
    return false;
  }

  /*
  // Does the mapping lookup for every row, no allocation.
  // data and preds arrays are pre-allocated and can be re-used for every row.
  public double[] score0(Map<String, Double> row, double[] data, double[] preds) {
    Double offset = _offsetColumn == null? null : row.get(_offsetColumn);
    return score0(map(row, data), offset == null? 0.0 : offset, preds);
  }

  // Does the mapping lookup for every row.
  // preds array is pre-allocated and can be re-used for every row.
  // Allocates a double[] for every row.
  public double[] score0(Map<String, Double> row, double[] preds) {
    return score0(row, new double[nfeatures()], preds);
  }

  // Does the mapping lookup for every row.
  // Allocates a double[] and a float[] for every row.
  public double[] score0(Map<String, Double> row) {
    return score0(row, new double[nfeatures()], new double[nclasses()+1]);
  }
  */

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
  public static int getPrediction(double[] preds, double[] priorClassDist, double[] data, double threshold) {
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
      assert(preds.length==priorClassDist.length+1);
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

  // Utility to do bitset lookup from a POJO
  public static boolean bitSetContains(byte[] bits, int nbits, int bitoff, double dnum) {
    assert(!Double.isNaN(dnum));
    int idx = (int)dnum;
    idx -= bitoff;
    assert (idx >= 0 && idx < nbits): "Must have "+bitoff+" <= idx <= " + (bitoff+nbits-1) + ": " + idx;
    return (bits[idx >> 3] & ((byte)1 << (idx & 7))) != 0;
  }

  public static boolean bitSetIsInRange(int nbits, int bitoff, double dnum) {
    assert(!Double.isNaN(dnum));
    int idx = (int)dnum;
    idx -= bitoff;
    return (idx >= 0 && idx < nbits);
  }

  // Todo: Done for K-means but we should really unify for all models.
  public static void Kmeans_preprocessData(double [] data, double [] means, double [] mults, int[] modes){
    for(int i = 0; i < data.length; i++) {
      data[i] = Kmeans_preprocessData(data[i], i, means, mults, modes);
    }
  }

  public static double Kmeans_preprocessData(double d, int i, double [] means, double [] mults, int[] modes){
    if(modes[i] == -1) {    // Mode = -1 for non-categorical cols
      if( Double.isNaN(d) )
        d = means[i];
      if( mults != null ) {
        d -= means[i];
        d *= mults[i];
      }
    } else {
      if( Double.isNaN(d) )
        d = modes[i];
    }
    return d;
  }

  // --------------------------------------------------------------------------
  // KMeans utilities
  // For KMeansModel scoring; just the closest cluster center
  public static int KMeans_closest(double[][] centers, double[] point, String[][] domains) {
    int min = -1;
    double minSqr = Double.MAX_VALUE;
    for( int cluster = 0; cluster < centers.length; cluster++ ) {
      double sqr = KMeans_distance(centers[cluster],point,domains);
      if( sqr < minSqr ) {      // Record nearest cluster center
        min = cluster;
        minSqr = sqr;
      }
    }
    return min;
  }

  // Outputs distances from a given point to all cluster centers, returns index of the closest cluster center
  public static int KMeans_distances(double[][] centers, double[] point, String[][] domains, double[] distances) {
    int min = -1;
    double minSqr = Double.MAX_VALUE;
    for (int cluster = 0; cluster < centers.length; cluster++) {
      distances[cluster] = KMeans_distance(centers[cluster], point, domains);
      if (distances[cluster] < minSqr) {      // Record nearest cluster center
        min = cluster;
        minSqr = distances[cluster];
      }
    }
    return min;
  }

  // only used for GLRM initialization - inverse of distance to each cluster center normalized to sum to one
  public static double[] KMeans_simplex(double[][] centers, double[] point, String[][] domains) {
    double[] dist = new double[centers.length];
    double sum = 0, inv_sum = 0;
    for( int cluster = 0; cluster < centers.length; cluster++ ) {
      dist[cluster] = KMeans_distance(centers[cluster],point,domains);
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
  public static double KMeans_distance(double[] center, float[] point, int [] modes,
                                       double[] colSum, double[] colSumSq) {
    double sqr = 0;             // Sum of dimensional distances
    int pts = point.length;     // Count of valid points

    for(int column = 0; column < center.length; column++) {
      float d = point[column];
      if( Float.isNaN(d) ) { pts--; continue; }
      if( modes[column] != -1 ) { // Categorical?
        if( d != center[column] ) {
          sqr += 1.0;           // Manhattan distance
        }
        if(d != modes[column]) {
          colSum[column] += 1;
        }
      } else {                  // Euclidean distance
        double delta = d - center[column];
        sqr += delta * delta;
        colSum[column] += d;
        colSumSq[column] += d*d;
      }
    }
    // Scale distance by ratio of valid dimensions to all dimensions - since
    // we did not add any error term for the missing point, the sum of errors
    // is small - ratio up "as if" the missing error term is equal to the
    // average of other error terms.  Same math another way:
    //   double avg_dist = sqr / pts; // average distance per feature/column/dimension
    //   sqr = sqr * point.length;    // Total dist is average*#dimensions
    if( 0 < pts && pts < point.length ) {
      double scale = ((double) point.length) / pts;
      sqr *= scale;
//      for (int i=0; i<colSum.length; ++i) {
//        colSum[i] *= Math.sqrt(scale);
//        colSumSq[i] *= scale;
//      }
    }
    return sqr;
  }

  // WARNING - if changing this code - also change the code above
  public static double KMeans_distance(double[] center, double[] point,String[][] domains) {
    double sqr = 0;             // Sum of dimensional distances
    int pts = point.length;     // Count of valid points

    for(int column = 0; column < center.length; column++) {
      double d = point[column];
      if( Double.isNaN(d) ) { pts--; continue; }
      if( domains[column] != null ) { // Categorical?
        if( d != center[column] )
          sqr += 1.0;           // Manhattan distance
      } else {                  // Euclidean distance
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
      sqr *= ((double) point.length) / pts;
    return sqr;
  }

  // --------------------------------------------------------------------------
  // SharedTree utilities

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
    for (int k = 1; k < preds.length; k++)
      preds[k] /= sum;
  }

  // --------------------------------------------------------------------------
  // GLM utilities
  public static double GLM_identityInv( double x ) { return x; }
  public static double GLM_logitInv( double x ) { return 1.0 / (Math.exp(-x) + 1.0); }
  public static double GLM_logInv( double x ) { return Math.exp(x); }
  public static double GLM_inverseInv( double x ) {  double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x); return 1.0 / xx; }
  public static double GLM_ologitInv(double x) {
    return GLM_logitInv(x);
  }
  public static double GLM_tweedieInv( double x, double tweedie_link_power ) {
    return tweedie_link_power == 0?Math.max(2e-16,Math.exp(x)):Math.pow(x, 1.0/ tweedie_link_power);
  }

  /** ??? */
  public String getHeader() { return null; }

  // Helper for DeepWater and XGBoost Native (models that require explicit one-hot encoding on the fly)
  static public void setInput(final double[] from, float[] to, int _nums, int _cats, int[] _catOffsets, double[] _normMul, double[] _normSub, boolean useAllFactorLevels, boolean replaceMissingWithZero) {
    double[] nums = new double[_nums]; // a bit wasteful - reallocated each time
    int[] cats = new int[_cats]; // a bit wasteful - reallocated each time

    setCats(from, nums, cats, _cats, _catOffsets, _normMul, _normSub, useAllFactorLevels);

    assert(to.length == _nums + _catOffsets[_cats]);
    Arrays.fill(to, 0f);
    for (int i = 0; i < _cats; ++i)
      if (cats[i] >= 0)
        to[cats[i]] = 1f; // one-hot encode categoricals
    for (int i = 0; i < _nums; ++i)
      to[_catOffsets[_cats] + i] = Double.isNaN(nums[i]) ? (replaceMissingWithZero ? 0 : Float.NaN) : (float)nums[i];
  }

  // Helper for Deeplearning, note: we assume nums and cats are allocated already and being re-used
  static public void setInput(final double[] from, double[] to, double[] nums, int[] cats, int _nums, int _cats,
                              int[] _catOffsets, double[] _normMul, double[] _normSub, boolean useAllFactorLevels, boolean replaceMissingWithZero) {
    setCats(from, nums, cats, _cats, _catOffsets, _normMul, _normSub, useAllFactorLevels);

    assert(to.length == _nums + _catOffsets[_cats]);
    Arrays.fill(to, 0d);
    for (int i = 0; i < _cats; ++i)
      if (cats[i] >= 0)
        to[cats[i]] = 1d; // one-hot encode categoricals
    for (int i = 0; i < _nums; ++i)
      to[_catOffsets[_cats] + i] = Double.isNaN(nums[i]) ? (replaceMissingWithZero ? 0 : Double.NaN) : nums[i];
  }

  // Helper for XGBoost Java
  static public void setCats(final double[] from, double[] nums, int[] cats, int _cats, int[] _catOffsets,
                             double[] _normMul, double[] _normSub, boolean useAllFactorLevels) {

    setCats(from, cats, _cats, _catOffsets, useAllFactorLevels);

    for (int i = _cats; i < from.length; ++i) {
      double d = from[i];

      if ((_normMul != null) && (_normMul.length >0)) {
        d = (d - _normSub[i - _cats]) * _normMul[i - _cats];
      }
      nums[i - _cats] = d; //can be NaN for missing numerical data
    }
  }

  static public void setCats(final double[] from, int[] to, int cats, int[] catOffsets, boolean useAllFactorLevels) {
    for (int i = 0; i < cats; ++i) {
      if (Double.isNaN(from[i])) {
        to[i] = (catOffsets[i + 1] - 1); //use the extra level for NAs made during training
      } else {
        int c = (int) from[i];
        if (useAllFactorLevels)
          to[i] = c + catOffsets[i];
        else {
          if (c != 0)
            to[i] = c - 1 + catOffsets[i];
          else
            to[i] = -1;
        }
        if (to[i] >= catOffsets[i + 1])
          to[i] = (catOffsets[i + 1] - 1);
      }
    }
  }

  public static float[] convertDouble2Float(double[] input) {
    int arraySize = input.length;
    float[] output = new float[arraySize];
    for (int index=0; index<arraySize; index++)
      output[index] = (float) input[index];
    return output;
  }

   public static void img2pixels(BufferedImage img, int w, int h, int channels, float[] pixels, int start, float[] mean) throws IOException {
    // resize the image
    BufferedImage scaledImg = new BufferedImage(w, h, img.getType());
    Graphics2D g2d = scaledImg.createGraphics();
    g2d.drawImage(img, 0, 0, w, h, null);
    g2d.dispose();

    int r_idx = start;
    int g_idx = r_idx + w * h;
    int b_idx = g_idx + w * h;

    for (int i = 0; i < h; i++) {
      for (int j = 0; j < w; j++) {
        Color mycolor = new Color(scaledImg.getRGB(j, i));
        int red = mycolor.getRed();
        int green = mycolor.getGreen();
        int blue = mycolor.getBlue();
        if (channels==1) {
          pixels[r_idx] = (red+green+blue)/3;
          if (mean!=null) {
            pixels[r_idx] -= mean[r_idx];
          }
        } else {
          pixels[r_idx] = red;
          pixels[g_idx] = green;
          pixels[b_idx] = blue;
          if (mean!=null) {
            pixels[r_idx] -= mean[r_idx-start];
            pixels[g_idx] -= mean[g_idx-start];
            pixels[b_idx] -= mean[b_idx-start];
          }
        }
        r_idx++;
        g_idx++;
        b_idx++;
      }
    }
  }

  // Helpers for deeplearning mojo


}
