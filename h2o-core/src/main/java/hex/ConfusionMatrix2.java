package hex;

import java.util.Arrays;
import water.util.ArrayUtils;
import water.Iced;
import water.fvec.Vec;
import water.fvec.Frame;

public class ConfusionMatrix2 extends Iced {
  public long[][] _arr; // [actual][predicted]
  public final double[] _classErr;
  public double _predErr;

  public enum ErrMetric {
    MAXC, SUMC, TOTAL;

    public double computeErr(ConfusionMatrix2 cm) {
      switch( this ) {
      case MAXC : return ArrayUtils.maxValue(cm.classErr());
      case SUMC : return ArrayUtils.sum(cm.classErr());
      case TOTAL: return cm.err();
      default   : throw water.H2O.unimpl();
      }
    }
  }

  public ConfusionMatrix2(int n) {
    _arr = new long[n][n];
    _classErr = classErr();
    _predErr = err();
  }

  public ConfusionMatrix2(long[][] value) {
    _arr = value;
    _classErr = classErr();
    _predErr = err();
  }

  public ConfusionMatrix2(long[][] value, int dim) {
    _arr = new long[dim][dim];
    for (int i=0; i<dim; ++i)
      System.arraycopy(value[i], 0, _arr[i], 0, dim);
    _classErr = classErr();
    _predErr = err();
  }

  /** Build the CM data from the actuals and predictions, using the default
   *  threshold.  Print to Log.info if the number of classes is below
   *  the print_threshold.
   */
  public ConfusionMatrix2(Vec actuals, Frame predictions, int print_threshold) {
    throw water.H2O.unimpl();
  }


  public void add(int i, int j) {
    _arr[i][j]++;
  }

  public double[] classErr() {
    double[] res = new double[_arr.length];
    for( int i = 0; i < res.length; ++i )
      res[i] = classErr(i);
    return res;
  }

  public final int size() {
    return _arr.length;
  }

  public void reComputeErrors(){
    for(int i = 0; i < _arr.length; ++i)
      _classErr[i] = classErr(i);
    _predErr = err();
  }
  public final long classErrCount(int c) {
    long s = 0;
    for( long x : _arr[c] )
      s += x;
    return s - _arr[c][c];
  }
  public final double classErr(int c) {
    long s = 0;
    for( long x : _arr[c] )
      s += x;
    if( s == 0 )
      return 0.0;    // Either 0 or NaN, but 0 is nicer
    return (double) (s - _arr[c][c]) / s;
  }
  public long totalRows() {
    long n = 0;
    for (long[] a_arr : _arr)
      for (int p = 0; p < a_arr.length; ++p)
        n += a_arr[p];
    return n;
  }

  public void add(ConfusionMatrix2 other) {
    ArrayUtils.add(_arr, other._arr);
  }

  /**
   * @return overall classification error
   */
  public double err() {
    long n = totalRows();
    long err = n;
    for( int d = 0; d < _arr.length; ++d )
      err -= _arr[d][d];
    return (double) err / n;
  }
  public long errCount() {
    long n = totalRows();
    long err = n;
    for( int d = 0; d < _arr.length; ++d )
      err -= _arr[d][d];
    return err;
  }
  /**
   * The percentage of predictions that are correct.
   */
  public double accuracy() { return 1-err(); }
  /**
   * The percentage of negative labeled instances that were predicted as negative.
   * @return TNR / Specificity
   */
  public double specificity() {
    if(!isBinary())throw new UnsupportedOperationException("specificity is only implemented for 2 class problems.");
    double tn = _arr[0][0];
    double fp = _arr[0][1];
    return tn / (tn + fp);
  }
  /**
   * The percentage of positive labeled instances that were predicted as positive.
   * @return Recall / TPR / Sensitivity
   */
  public double recall() {
    if(!isBinary())throw new UnsupportedOperationException("recall is only implemented for 2 class problems.");
    double tp = _arr[1][1];
    double fn = _arr[1][0];
    return tp / (tp + fn);
  }
  /**
   * The percentage of positive predictions that are correct.
   * @return Precision
   */
  public double precision() {
    if(!isBinary())throw new UnsupportedOperationException("precision is only implemented for 2 class problems.");
    double tp = _arr[1][1];
    double fp = _arr[0][1];
    return tp / (tp + fp);
  }
  /**
   * The Matthews Correlation Coefficient, takes true negatives into account in contrast to F-Score
   * See <a href="http://en.wikipedia.org/wiki/Matthews_correlation_coefficient">MCC</a>
   * MCC = Correlation between observed and predicted binary classification
   * @return mcc ranges from -1 (total disagreement) ... 0 (no better than random) ... 1 (perfect)
   */
  public double mcc() {
    if(!isBinary())throw new UnsupportedOperationException("precision is only implemented for 2 class problems.");
    double tn = _arr[0][0];
    double fp = _arr[0][1];
    double tp = _arr[1][1];
    double fn = _arr[1][0];
    double mcc = (tp*tn - fp*fn)/Math.sqrt((tp+fp)*(tp+fn)*(tn+fp)*(tn+fn));
    return mcc;
  }
  /**
   * The maximum per-class error
   * @return max(classErr(i))
   */
  public double max_per_class_error() {
    int n = nclasses();
    if(n == 0)throw new UnsupportedOperationException("max per class error is only defined for classification problems");
    double res = classErr(0);
    for(int i = 1; i < n; ++i)
      res = Math.max(res,classErr(i));
    return res;
  }

  public final int nclasses(){return _arr == null?0:_arr.length;}
  public final boolean isBinary(){return nclasses() == 2;}

  /**
   * Returns the F-measure which combines precision and recall. <br>
   * C.f. end of http://en.wikipedia.org/wiki/Precision_and_recall.
   */
  public double F1() {
    final double precision = precision();
    final double recall = recall();
    return 2. * (precision * recall) / (precision + recall);
  }

  /**
   * Returns the F-measure which combines precision and recall and weights recall higher than precision. <br>
   * See <a href="http://en.wikipedia.org/wiki/F1_score.">F1_score</a>
   */
  public double F2() {
    final double precision = precision();
    final double recall = recall();
    return 5. * (precision * recall) / (4. * precision + recall);
  }

  /**
   * Returns the F-measure which combines precision and recall and weights precision higher than recall. <br>
   * See <a href="http://en.wikipedia.org/wiki/F1_score.">F1_score</a>
   */
  public double F0point5() {
    final double precision = precision();
    final double recall = recall();
    return 1.25 * (precision * recall) / (.25 * precision + recall);
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    for( long[] r : _arr )
      sb.append(Arrays.toString(r) + "\n");
    return sb.toString();
  }
}
