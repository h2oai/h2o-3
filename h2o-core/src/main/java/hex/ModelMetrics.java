package hex;

import water.*;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.Comparator;

/** Container to hold the metric for a model as scored on a specific frame.
 *
 *  The MetricBuilder class is used in a hot inner-loop of a Big Data pass, and
 *  when given a class-distribution, can be used to compute CM's, and AUC's "on
 *  the fly" during ModelBuilding - or after-the-fact with a Model and a new
 *  Frame to be scored.
 */
public class ModelMetrics extends Keyed {
  final Key _modelKey;
  final Key _frameKey;
  final Model.ModelCategory _model_category;
  final long _model_checksum;
  final long _frame_checksum;
  transient Model _model;
  transient Frame _frame;

  public double _mse;     // Mean Squared Error (Every model is assumed to have this, otherwise leave at NaN)

  long duration_in_ms = -1L;
  long scoring_time = -1L;

  public ModelMetrics(Model model, Frame frame, double mse) {
    this(model, frame);
    _mse = mse;
  }

  public ModelMetrics(Model model, Frame frame) {
    super(buildKey(model, frame));
    _modelKey = model._key;
    _frameKey = frame._key;
    _model_category = model._output.getModelCategory();
    _model = model;
    _frame = frame;
    _model_checksum = model.checksum();
    _frame_checksum = frame.checksum();
    _mse = Double.NaN;

    DKV.put(this);
  }

  public Model model() { return _model==null ? (_model=DKV.getGet(_modelKey)) : _model; }
  public Frame frame() { return _frame==null ? (_frame=DKV.getGet(_frameKey)) : _frame; }

  public ConfusionMatrix cm() { return null; }
  public float[] hr() { return null; }
  public AUCData auc() { return null; }

  public static TwoDimTable calcVarImp(final double[] rel_imp, String[] coef_names) {
    return calcVarImp(rel_imp, coef_names, "Variable Importance", new String[] {"Relative Importance", "Scaled Importance", "Percentage"});
  }
  public static TwoDimTable calcVarImp(final double[] rel_imp, String[] coef_names, String table_header, String[] col_headers) {
    if(rel_imp == null) return null;
    if(coef_names == null) {
      coef_names = new String[rel_imp.length];
      for(int i = 0; i < coef_names.length; i++)
        coef_names[i] = "C" + String.valueOf(i+1);
    }
    assert rel_imp.length == coef_names.length;

    // Sort in descending order by relative importance
    Integer[] sorted_idx = new Integer[rel_imp.length];
    for(int i = 0; i < sorted_idx.length; i++) sorted_idx[i] = i;
    Arrays.sort(sorted_idx, new Comparator<Integer>() {
      public int compare(Integer idx1, Integer idx2) {
        return Double.compare(-rel_imp[idx1], -rel_imp[idx2]);
      }
    });

    double total = 0;
    double max = rel_imp[sorted_idx[0]];
    String[] sorted_names = new String[coef_names.length];
    double[][] sorted_imp = new double[rel_imp.length][3];

    // First pass to sum up relative importance measures
    int j = 0;
    for(int i : sorted_idx) {
      total += rel_imp[i];
      sorted_names[j] = coef_names[i];
      sorted_imp[j][0] = rel_imp[i];         // Relative importance
      sorted_imp[j++][1] = rel_imp[i] / max;   // Scaled importance
    }
    // Second pass to calculate percentages
    j = 0;
    for(int i : sorted_idx)
      sorted_imp[j++][2] = rel_imp[i] / total; // Percentage

    String [] col_types = new String[3];
    String [] col_formats = new String[3];
    Arrays.fill(col_types, "double");
    Arrays.fill(col_formats, "%5f");
    return new TwoDimTable(table_header, sorted_names, col_headers, col_types, col_formats,
            new String[rel_imp.length][], sorted_imp);
  }

  private static Key buildKey(Key model_key, long model_checksum, Key frame_key, long frame_checksum) {
    return Key.make("modelmetrics_" + model_key + "@" + model_checksum + "_on_" + frame_key + "@" + frame_checksum);
  }

  private static Key buildKey(Model model, Frame frame) {
    return buildKey(model._key, model.checksum(), frame._key, frame.checksum());
  }

  public boolean isForModel(Model m) { return _model_checksum == m.checksum(); }
  public boolean isForFrame(Frame f) { return _frame_checksum == f.checksum(); }

  public static ModelMetrics getFromDKV(Model model, Frame frame) {
    Key metricsKey = buildKey(model, frame);
    Value v = DKV.get(metricsKey);
    return null == v ? null : (ModelMetrics)v.get();
  }

  @Override protected long checksum_impl() { return _frame_checksum * 13 + _model_checksum * 17; }

  /** Class used to compute AUCs, CMs & HRs "on the fly" during other passes
   *  over Big Data.  This class is intended to be embedded in other MRTask
   *  objects.  The {@code perRow} method is called once-per-scored-row, and
   *  the {@code reduce} method called once per MRTask.reduce, and the {@code
   *  <init>} called once per MRTask.map.
   */
  public static abstract class MetricBuilder extends Iced {
    public double _sumsqe;      // Sum-squared-error
    transient public float[] _work;
    public long _count;

    abstract public float[] perRow( float ds[], float yact[], Model m);
    public void reduce( MetricBuilder mb ) {
      _sumsqe += mb._sumsqe;
      _count += mb._count;
    }

    public void postGlobal() {}
    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public abstract ModelMetrics makeModelMetrics( Model m, Frame f, double sigma);
  }
}
