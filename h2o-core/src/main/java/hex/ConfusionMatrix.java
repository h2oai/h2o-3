package hex;

import water.Iced;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.Arrays;

public class ConfusionMatrix extends Iced {
  private TwoDimTable _table;
  public final double[][] _cm; // [actual][predicted], typed as double because of observation weights (which can be doubles)
  public final String[] _domain;

  /**
   * Constructor for Confusion Matrix
   * @param value 2D square matrix with co-occurrence counts for actual vs predicted class membership
   * @param domain class labels (unified domain between actual and predicted class labels)
   */
  public ConfusionMatrix(double[][] value, String[] domain) { _cm = value; _domain = domain; }

  /** Build the CM data from the actuals and predictions, using the default
   *  threshold.  Print to Log.info if the number of classes is below the
   *  print_threshold.  Actuals might have extra levels not trained on (hence
   *  never predicted).  Actuals with NAs are not scored, and their predictions
   *  ignored. */
  public static ConfusionMatrix buildCM(Vec actuals, Vec predictions) {
    if (!actuals.isEnum()) throw new IllegalArgumentException("actuals must be enum.");
    if (!predictions.isEnum()) throw new IllegalArgumentException("predictions must be enum.");
    Scope.enter();
    try {
      Vec adapted = predictions.adaptTo(actuals.domain());
      int len = actuals.domain().length;
      CMBuilder cm = new CMBuilder(len).doAll(actuals, adapted);
      return new ConfusionMatrix(cm._arr, actuals.domain());
    } finally {
      Scope.exit();
    }
  }

  private static class CMBuilder extends MRTask<CMBuilder> {
    final int _len;
    double _arr[/*actuals*/][/*predicted*/];
    CMBuilder(int len) { _len = len; }
    @Override public void map( Chunk ca, Chunk cp ) {
      // After adapting frames, the Actuals have all the levels in the
      // prediction results, plus any extras the model was never trained on.
      // i.e., Actual levels are at least as big as the predicted levels.
      _arr = new double[_len][_len];
      for( int i=0; i < ca._len; i++ )
        if( !ca.isNA(i) )
          _arr[(int)ca.at8(i)][(int)cp.at8(i)]++;
    }
    @Override public void reduce( CMBuilder cm ) { ArrayUtils.add(_arr,cm._arr); }
  }


  public void add(int i, int j) { _cm[i][j]++; }

  public final int size() { return _cm.length; }

  public final double classErr(int c) {
    double s = ArrayUtils.sum(_cm[c]);
    if( s == 0 ) return 0.0;    // Either 0 or NaN, but 0 is nicer
    return (s - _cm[c][c]) / s;
  }
  public double totalRows() {
    double n = 0;
    for (double[] a_arr : _cm)
      n += ArrayUtils.sum(a_arr);
    return n;
  }

  public void add(ConfusionMatrix other) {
    ArrayUtils.add(_cm, other._cm);
  }

  /**
   * @return overall classification error
   */
  public double err() {
    double n = totalRows();
    double err = n;
    for( int d = 0; d < _cm.length; ++d )
      err -= _cm[d][d];
    return (double) err / n;
  }
  public double errCount() {
    double err = totalRows();
    for( int d = 0; d < _cm.length; ++d )
      err -= _cm[d][d];
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
    if(!isBinary())throw new UnsupportedOperationException("precision is only implemented for 2 class problems.");
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
    double res = classErr(0);
    for(int i = 1; i < n; ++i)
      res = Math.max(res,classErr(i));
    return res;
  }

  public final int nclasses(){return _cm == null?0: _cm.length;}
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
        isInt &= (_cm[a][p] == (int)_cm[a][p]);
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
      colType[i] = isInt ? "int":"double";
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
              Math.max(width, String.format("%,d / %,d", (int)err, (int)acts[a]).length()):
              Math.max(width, String.format("%.4f / %.4f",    err,      acts[a]).length());
    }
    double nrows = 0;
    for (double n : acts) nrows += n;
    width = isInt?
            Math.max(width, String.format("%,d / %,d", (int)terr, (int)nrows).length()):
            Math.max(width, String.format("%.4f / %.4f",    terr,      nrows).length());

    // set format width
    colFormat[colFormat.length-1] = "= %" + width + "s";

    TwoDimTable table = new TwoDimTable("Confusion Matrix", "vertical: actual; across: predicted", rowHeader, colHeader, colType, colFormat, null);

    // Main CM Body
    for (int a = 0; a < _cm.length; a++) {
      if (adomain[a] == null) continue;
      double correct = 0;
      for (int p = 0; p < pdomain.length; p++) {
        if (pdomain[p] == null) continue;
        boolean onDiag = adomain[a].equals(pdomain[p]);
        if (onDiag) correct = _cm[a][p];
        if (isInt)
          table.set(a, p, (int)_cm[a][p]);
        else
          table.set(a, p, _cm[a][p]);
      }
      double err = acts[a] - correct;
      table.set(a, pdomain.length, err / acts[a]);
      table.set(a, pdomain.length + 1,
              isInt ? String.format("%,d / %,d", (int)err, (int)acts[a]):
                      String.format("%.4f / %.4f",    err,      acts[a])
      );
    }

    // Last row of CM
    for (int p = 0; p < pdomain.length; p++) {
      if (pdomain[p] == null) continue;
      if (isInt)
        table.set(adomain.length, p, (int)preds[p]);
      else
        table.set(adomain.length, p, preds[p]);
    }
    table.set(adomain.length, pdomain.length, (float) terr / nrows);
    table.set(adomain.length, pdomain.length + 1,
            isInt ? String.format("%,d / %,d", (int)terr, (int)nrows):
                    String.format("%.2f / %.2f",    terr,      nrows));

    return table;
  }
}
