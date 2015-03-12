package hex;

import java.util.Arrays;
import water.Iced;
import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

public class ConfusionMatrix extends Iced {
  public TwoDimTable table;
  public final long[][] confusion_matrix; // [actual][predicted]
  public final double[] prediction_error_by_class;
  public double prediction_error;
  public String[] domain;

//  public enum ErrMetric {
//    MAXC, SUMC, TOTAL;
//
//    public double computeErr(ConfusionMatrix cm) {
//      switch( this ) {
//      case MAXC : return ArrayUtils.maxValue(cm.classErr());
//      case SUMC : return ArrayUtils.sum(cm.classErr());
//      case TOTAL: return cm.err();
//      default   : throw water.H2O.unimpl();
//      }
//    }
//  }

  /**
   * Constructor for Confusion Matrix
   * @param value 2D square matrix with co-occurrence counts for actual vs predicted class membership
   * @param domain class labels (unified domain between actual and predicted class labels)
   */
  public ConfusionMatrix(long[][] value, String[] domain) {
    confusion_matrix = value;
    prediction_error_by_class = classErr();
    prediction_error = err();
    this.domain = domain;
    table = toTable();
  }

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
    long _arr[/*actuals*/][/*predicted*/];
    CMBuilder(int len) { _len = len; }
    @Override public void map( Chunk ca, Chunk cp ) {
      // After adapting frames, the Actuals have all the levels in the
      // prediction results, plus any extras the model was never trained on.
      // i.e., Actual levels are at least as big as the predicted levels.
      _arr = new long[_len][_len];
      for( int i=0; i < ca._len; i++ )
        if( !ca.isNA(i) )
          _arr[(int)ca.at8(i)][(int)cp.at8(i)]++;
    }
    @Override public void reduce( CMBuilder cm ) { ArrayUtils.add(_arr,cm._arr); }
  }


  public void add(int i, int j) {
    confusion_matrix[i][j]++;
  }

  public double[] classErr() {
    double[] res = new double[confusion_matrix.length];
    for( int i = 0; i < res.length; ++i )
      res[i] = classErr(i);
    return res;
  }

  public final int size() {
    return confusion_matrix.length;
  }

  public void reComputeErrors(){
    for(int i = 0; i < confusion_matrix.length; ++i)
      prediction_error_by_class[i] = classErr(i);
    prediction_error = err();
  }
  public final long classErrCount(int c) {
    long s = ArrayUtils.sum(confusion_matrix[c]);
    return s - confusion_matrix[c][c];
  }
  public final double classErr(int c) {
    long s = ArrayUtils.sum(confusion_matrix[c]);
    if( s == 0 ) return 0.0;    // Either 0 or NaN, but 0 is nicer
    return (double) (s - confusion_matrix[c][c]) / s;
  }
  public long totalRows() {
    long n = 0;
    for (long[] a_arr : confusion_matrix)
      n += ArrayUtils.sum(a_arr);
    return n;
  }

  public void add(ConfusionMatrix other) {
    ArrayUtils.add(confusion_matrix, other.confusion_matrix);
  }

  /**
   * @return overall classification error
   */
  public double err() {
    long n = totalRows();
    long err = n;
    for( int d = 0; d < confusion_matrix.length; ++d )
      err -= confusion_matrix[d][d];
    return (double) err / n;
  }
  public long errCount() {
    long n = totalRows();
    long err = n;
    for( int d = 0; d < confusion_matrix.length; ++d )
      err -= confusion_matrix[d][d];
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
    double tn = confusion_matrix[0][0];
    double fp = confusion_matrix[0][1];
    return tn / (tn + fp);
  }
  /**
   * The percentage of positive labeled instances that were predicted as positive.
   * @return Recall / TPR / Sensitivity
   */
  public double recall() {
    if(!isBinary())throw new UnsupportedOperationException("recall is only implemented for 2 class problems.");
    double tp = confusion_matrix[1][1];
    double fn = confusion_matrix[1][0];
    return tp / (tp + fn);
  }
  /**
   * The percentage of positive predictions that are correct.
   * @return Precision
   */
  public double precision() {
    if(!isBinary())throw new UnsupportedOperationException("precision is only implemented for 2 class problems.");
    double tp = confusion_matrix[1][1];
    double fp = confusion_matrix[0][1];
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
    double tn = confusion_matrix[0][0];
    double fp = confusion_matrix[0][1];
    double tp = confusion_matrix[1][1];
    double fn = confusion_matrix[1][0];
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

  public final int nclasses(){return confusion_matrix == null?0: confusion_matrix.length;}
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
    for( long[] r : confusion_matrix)
      sb.append(Arrays.toString(r) + "\n");
    return sb.toString();
  }

  private static String[] createConfusionMatrixHeader( long xs[], String ds[] ) {
    String ss[] = new String[xs.length]; // the same length
    for( int i=0; i<ds.length; i++ )
      if( xs[i] >= 0 || (ds[i] != null && ds[i].length() > 0) && !Integer.toString(i).equals(ds[i]) )
        ss[i] = ds[i];
    if( ds.length == xs.length-1 && xs[xs.length-1] > 0 )
      ss[xs.length-1] = "NA";
    return ss;
  }

  public String toASCII() {
    return (table=toTable()) != null ? table.toString() : "";
  }

  /**
   * Convert this ConfusionMatrix into a fully annotated TwoDimTable
   * @return TwoDimTable
   */
  TwoDimTable toTable() {
    if (confusion_matrix == null || domain == null) return null;
    for (int i=0; i< confusion_matrix.length; ++i) assert(confusion_matrix.length == confusion_matrix[i].length);
    // Sum up predicted & actuals
    long acts [] = new long[confusion_matrix.length];
    long preds[] = new long[confusion_matrix[0].length];
    for( int a=0; a< confusion_matrix.length; a++ ) {
      long sum=0;
      for( int p=0; p< confusion_matrix[a].length; p++ ) {
        sum += confusion_matrix[a][p];
        preds[p] += confusion_matrix[a][p];
      }
      acts[a] = sum;
    }
    String adomain[] = createConfusionMatrixHeader(acts , domain);
    String pdomain[] = createConfusionMatrixHeader(preds, domain);
    assert adomain.length == pdomain.length : "The confusion matrix should have the same length for both directions.";

    String[] rowHeader = new String[adomain.length+1];
    for (int i=0; i<adomain.length; ++i)
      rowHeader[i] = adomain[i];
    rowHeader[adomain.length] = "Totals";

    String[] colHeader = new String[pdomain.length+2];
    for (int i=0; i<pdomain.length; ++i)
      colHeader[i] = pdomain[i];
    colHeader[colHeader.length-2] = "Error";
    colHeader[colHeader.length-1] = "";

    String[] colType = new String[colHeader.length];
    String[] colFormat = new String[colHeader.length];
    for (int i=0; i<colFormat.length-1; ++i) {
      colType[i]   = "integer";
      colFormat[i] = "%d";
    }
    colType[colFormat.length-2]   = "double";
    colFormat[colFormat.length-2] = "%.4f";
    colType[colFormat.length-1]   = "string";

    // pass 1: compute width of last column
    long terr = 0;
    int width = 0;
    for (int a = 0; a < confusion_matrix.length; a++) {
      if (adomain[a] == null) continue;
      long correct = 0;
      for (int p = 0; p < pdomain.length; p++) {
        if (pdomain[p] == null) continue;
        boolean onDiag = adomain[a].equals(pdomain[p]);
        if (onDiag) correct = confusion_matrix[a][p];
      }
      long err = acts[a] - correct;
      terr += err;
      width = Math.max(width, String.format("%,d / %,d", err, acts[a]).length());
    }
    long nrows = 0;
    for (long n : acts) nrows += n;
    width = Math.max(width, String.format("%,d / %,d", terr, nrows).length());

    // set format width
    colFormat[colFormat.length-1] = "= %" + width + "s";

    TwoDimTable table = new TwoDimTable("Confusion Matrix", rowHeader, colHeader, colType, colFormat, "Act/Pred");

    // Main CM Body
    for (int a = 0; a < confusion_matrix.length; a++) {
      if (adomain[a] == null) continue;
      long correct = 0;
      for (int p = 0; p < pdomain.length; p++) {
        if (pdomain[p] == null) continue;
        boolean onDiag = adomain[a].equals(pdomain[p]);
        if (onDiag) correct = confusion_matrix[a][p];
        table.set(a, p, confusion_matrix[a][p]);
      }
      long err = acts[a] - correct;
      terr += err;
      table.set(a, pdomain.length, (double) err / acts[a]);
      table.set(a, pdomain.length + 1, String.format("%,d / %,d", err, acts[a]));
    }

    // Last row of CM
    for (int p = 0; p < pdomain.length; p++) {
      if (pdomain[p] == null) continue;
      table.set(adomain.length, p, preds[p]);
    }
    for (long n : acts) nrows += n;
    table.set(adomain.length, pdomain.length, (float) terr / nrows);
    table.set(adomain.length, pdomain.length + 1, String.format("%,d / %,d", terr, nrows));
    return table;
  }
}
