package hex;

import water.H2O;
import water.Iced;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.Arrays;

public class ConfusionMatrix extends Iced {

  private static final String MAX_CM_CLASSES_KEY = H2O.OptArgs.SYSTEM_PROP_PREFIX + "cm.maxClasses";
  private static final int MAX_CM_CLASSES_DEFAULT = 1000;

  private TwoDimTable _table;
  public final double[][] _cm; // [actual][predicted], typed as double because of observation weights (which can be doubles)
  public final String[] _domain;

  /**
   * Constructor for Confusion Matrix
   * @param value 2D square matrix with co-occurrence counts for actual vs predicted class membership
   * @param domain class labels (unified domain between actual and predicted class labels)
   */
  public ConfusionMatrix(double[][] value, String[] domain) { _cm = value; _domain = domain; }

  public void add(int i, int j) { _cm[i][j]++; }

  public final int size() { return _domain.length; }

  boolean tooLarge() { return size() > maxClasses(); }

  static int maxClasses() {
    String maxClassesSpec = System.getProperty(MAX_CM_CLASSES_KEY);
    if (maxClassesSpec == null)
      return MAX_CM_CLASSES_DEFAULT;
    return parseMaxClasses(maxClassesSpec);
  }
  
  static int parseMaxClasses(String maxClassesSpec) {
    try {
      int maxClasses = Integer.parseInt(maxClassesSpec);
      if (maxClasses <= 0) {
        Log.warn("Using default limit of max classes in a confusion matrix (" + MAX_CM_CLASSES_DEFAULT + ", user specification is invalid: " + maxClasses + ")");
        return MAX_CM_CLASSES_DEFAULT;
      } else
        return maxClasses;
    } catch (NumberFormatException e) {
      Log.warn("Using default limit of max classes in a confusion matrix (" + MAX_CM_CLASSES_DEFAULT + ", user specification is invalid: " + maxClassesSpec + ")", e);
      return MAX_CM_CLASSES_DEFAULT;
    }
  }

  public final double mean_per_class_error() {
    if(tooLarge())throw new UnsupportedOperationException("mean per class error cannot be computed: too many classes");
    double err = 0;
    for( int d = 0; d < _cm.length; ++d )
      err += class_error(d); //can be 0 if no actuals, but we're still dividing by the total count of classes
    return err / _cm.length;
  }

  // mean(accuracy) = mean(1-error) = 1-mean(error)
  public final double mean_per_class_accuracy() {
    return 1-mean_per_class_error();
  }

  public final double class_error(int c) {
    if(tooLarge())throw new UnsupportedOperationException("class errors cannot be computed: too many classes");
    double s = ArrayUtils.sum(_cm[c]);
    if( s == 0 ) return 0.0;    // Either 0 or NaN, but 0 is nicer
    return (s - _cm[c][c]) / s;
  }
  public double total_rows() {
    double n = 0;
    for (double[] a_arr : _cm)
      n += ArrayUtils.sum(a_arr);
    return n;
  }

  public void add(ConfusionMatrix other) {
    if (_cm != null && other._cm != null)
      ArrayUtils.add(_cm, other._cm);
  }

  /**
   * @return overall classification error
   */
  public double err() {
    if(tooLarge())throw new UnsupportedOperationException("error cannot be computed: too many classes");
    double n = total_rows();
    double err = n;
    for( int d = 0; d < _cm.length; ++d )
      err -= _cm[d][d];
    return err / n;
  }
  public double err_count() {
    if(tooLarge())throw new UnsupportedOperationException("error count cannot be computed: too many classes");
    double err = total_rows();
    for( int d = 0; d < _cm.length; ++d )
      err -= _cm[d][d];
    assert(err >= 0);
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
    if(tooLarge())throw new UnsupportedOperationException("specificity cannot be computed: too many classes");
    double tn = _cm[0][0];
    double fp = _cm[0][1];
    return tn / (tn + fp);
  }
  /**
   * The percentage of positive labeled instances that were predicted as positive.
   * @return Recall / TPR / Sensitivity
   */
  public double recall() {
    if(!isBinary())throw new UnsupportedOperationException("recall is only implemented for 2 class problems.");
    if(tooLarge())throw new UnsupportedOperationException("recall cannot be computed: too many classes");
    double tp = _cm[1][1];
    double fn = _cm[1][0];
    return tp / (tp + fn);
  }
  /**
   * The percentage of positive predictions that are correct.
   * @return Precision
   */
  public double precision() {
    if(!isBinary())throw new UnsupportedOperationException("precision is only implemented for 2 class problems.");
    if(tooLarge())throw new UnsupportedOperationException("precision cannot be computed: too many classes");
    double tp = _cm[1][1];
    double fp = _cm[0][1];
    return tp / (tp + fp);
  }
  /**
   * The Matthews Correlation Coefficient, takes true negatives into account in contrast to F-Score
   * See <a href="http://en.wikipedia.org/wiki/Matthews_correlation_coefficient">MCC</a>
   * MCC = Correlation between observed and predicted binary classification
   * @return mcc ranges from -1 (total disagreement) ... 0 (no better than random) ... 1 (perfect)
   */
  public double mcc() {
    if(!isBinary())throw new UnsupportedOperationException("mcc is only implemented for 2 class problems.");
    if(tooLarge())throw new UnsupportedOperationException("mcc cannot be computed: too many classes");
    double tn = _cm[0][0];
    double fp = _cm[0][1];
    double tp = _cm[1][1];
    double fn = _cm[1][0];
    return (tp*tn - fp*fn)/Math.sqrt((tp+fp)*(tp+fn)*(tn+fp)*(tn+fn));
  }
  /**
   * The maximum per-class error
   * @return max[classErr(i)]
   */
  public double max_per_class_error() {
    int n = nclasses();
    if(n == 0)throw new UnsupportedOperationException("max per class error is only defined for classification problems");
    if(tooLarge())throw new UnsupportedOperationException("max per class error cannot be computed: too many classes");
    double res = class_error(0);
    for(int i = 1; i < n; ++i)
      res = Math.max(res, class_error(i));
    return res;
  }

  public final int nclasses(){return _domain == null ? 0: _domain.length;}
  public final boolean isBinary(){return nclasses() == 2;}

  /**
   * Returns the F-measure which combines precision and recall. <br>
   * C.f. end of http://en.wikipedia.org/wiki/Precision_and_recall.
   */
  public double f1() {
    final double precision = precision();
    final double recall = recall();
    return 2. * (precision * recall) / (precision + recall);
  }

  /**
   * Returns the F-measure which combines precision and recall and weights recall higher than precision. <br>
   * See <a href="http://en.wikipedia.org/wiki/F1_score.">F1_score</a>
   */
  public double f2() {
    final double precision = precision();
    final double recall = recall();
    return 5. * (precision * recall) / (4. * precision + recall);
  }

  /**
   * Returns the F-measure which combines precision and recall and weights precision higher than recall. <br>
   * See <a href="http://en.wikipedia.org/wiki/F1_score.">F1_score</a>
   */
  public double f0point5() {
    final double precision = precision();
    final double recall = recall();
    return 1.25 * (precision * recall) / (.25 * precision + recall);
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    for( double[] r : _cm)
      sb.append(Arrays.toString(r)).append('\n');
    return sb.toString();
  }

  private static String[] createConfusionMatrixHeader( double xs[], String ds[] ) {
    String ss[] = new String[xs.length]; // the same length
    for( int i=0; i<xs.length; i++ )
      if( xs[i] >= 0 || (ds[i] != null && ds[i].length() > 0) && !Double.toString(i).equals(ds[i]) )
        ss[i] = ds[i];
    if( ds.length == xs.length-1 && xs[xs.length-1] > 0 )
      ss[xs.length-1] = "NA";
    return ss;
  }

  public String toASCII() { return table() == null ? "" : _table.toString(); }

  /** Convert this ConfusionMatrix into a fully annotated TwoDimTable
   *  @return TwoDimTable  */
  public TwoDimTable table() { return _table == null ? (_table=toTable()) : _table; }

  // Do the work making a TwoDimTable
  private TwoDimTable toTable() {
    if (tooLarge()) return null;
    if (_cm == null || _domain == null) return null;
    for( double cm[] : _cm ) assert(_cm.length == cm.length);
    // Sum up predicted & actuals
    double acts [] = new double[_cm.length];
    double preds[] = new double[_cm[0].length];
    boolean isInt = true;
    for( int a=0; a< _cm.length; a++ ) {
      double sum=0;
      for( int p=0; p< _cm[a].length; p++ ) {
        sum += _cm[a][p];
        preds[p] += _cm[a][p];
        isInt &= (_cm[a][p] == (long)_cm[a][p]);
      }
      acts[a] = sum;
    }
    String adomain[] = createConfusionMatrixHeader(acts , _domain);
    String pdomain[] = createConfusionMatrixHeader(preds, _domain);
    assert adomain.length == pdomain.length : "The confusion matrix should have the same length for both directions.";

    String[] rowHeader = Arrays.copyOf(adomain,adomain.length+1);
    rowHeader[adomain.length] = "Totals";

    String[] colHeader = Arrays.copyOf(pdomain,pdomain.length+2);
    colHeader[colHeader.length-2] = "Error";
    colHeader[colHeader.length-1] = "Rate";

    String[] colType = new String[colHeader.length];
    String[] colFormat = new String[colHeader.length];
    for (int i=0; i<colFormat.length-1; ++i) {
      colType[i] = isInt ? "long":"double";
      colFormat[i] = isInt ? "%d":"%.2f";
    }
    colType[colFormat.length-2]   = "double";
    colFormat[colFormat.length-2] = "%.4f";
    colType[colFormat.length-1]   = "string";

    // pass 1: compute width of last column
    double terr = 0;
    int width = 0;
    for (int a = 0; a < _cm.length; a++) {
      if (adomain[a] == null) continue;
      double correct = 0;
      for (int p = 0; p < pdomain.length; p++) {
        if (pdomain[p] == null) continue;
        boolean onDiag = adomain[a].equals(pdomain[p]);
        if (onDiag) correct = _cm[a][p];
      }
      double err = acts[a] - correct;
      terr += err;
      width = isInt ?
              Math.max(width, String.format("%,d / %,d", (long)err, (long)acts[a]).length()):
              Math.max(width, String.format("%.4f / %.4f",     err,       acts[a]).length());
    }
    double nrows = 0;
    for (double n : acts) nrows += n;
    width = isInt?
            Math.max(width, String.format("%,d / %,d", (long)terr, (long)nrows).length()):
            Math.max(width, String.format("%.4f / %.4f",     terr,       nrows).length());

    // set format width
    colFormat[colFormat.length-1] = "= %" + width + "s";

    TwoDimTable table = new TwoDimTable("Confusion Matrix", "Row labels: Actual class; Column labels: Predicted class", rowHeader, colHeader, colType, colFormat, null);

    // Main CM Body
    for (int a = 0; a < _cm.length; a++) {
      if (adomain[a] == null) continue;
      double correct = 0;
      for (int p = 0; p < pdomain.length; p++) {
        if (pdomain[p] == null) continue;
        boolean onDiag = adomain[a].equals(pdomain[p]);
        if (onDiag) correct = _cm[a][p];
        if (isInt)
          table.set(a, p, (long)_cm[a][p]);
        else
          table.set(a, p, _cm[a][p]);
      }
      double err = acts[a] - correct;
      table.set(a, pdomain.length, err / acts[a]);
      table.set(a, pdomain.length + 1,
              isInt ? String.format("%,d / %,d", (long)err, (long)acts[a]):
                      String.format("%.4f / %.4f",     err,       acts[a])
      );
    }

    // Last row of CM
    for (int p = 0; p < pdomain.length; p++) {
      if (pdomain[p] == null) continue;
      if (isInt)
        table.set(adomain.length, p, (long)preds[p]);
      else
        table.set(adomain.length, p, preds[p]);
    }
    table.set(adomain.length, pdomain.length, (float) terr / nrows);
    table.set(adomain.length, pdomain.length + 1,
            isInt ? String.format("%,d / %,d", (long)terr, (long)nrows):
                    String.format("%.2f / %.2f",     terr,       nrows));

    return table;
  }
}
