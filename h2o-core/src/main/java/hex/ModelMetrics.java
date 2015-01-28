package hex;

import water.*;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.ModelUtils;

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

  long duration_in_ms = -1L;
  long scoring_time = -1L;

  public ModelMetrics(Model model, Frame frame) {
    super(buildKey(model, frame));
    _modelKey = model._key;
    _frameKey = frame._key;
    _model_category = model._output.getModelCategory();
    _model = model;
    _frame = frame;
    _model_checksum = model.checksum();
    _frame_checksum = frame.checksum();

    DKV.put(this);
  }

  public Model model() { return _model==null ? (_model=DKV.getGet(_modelKey)) : _model; }
  public Frame frame() { return _frame==null ? (_frame=DKV.getGet(_frameKey)) : _frame; }

  // r2 => improvement over random guess of the mean
  public double r2() { return Double.NaN; }
  public ConfusionMatrix cm() { return null; }
  public HitRatio hr() { return null; }
  public AUCData auc() { return null; }

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
  public static final class MetricBuilder extends Iced {
    final String[] _domain;
    final int _nclasses;
    final float[] _thresholds;
    long[/*nthreshes*/][/*nclasses*/][/*nclasses*/] _cms; // Confusion Matric(es)
    public double _sumsqe;      // Sum-squared-error
    transient public float[] _work;
    public MetricBuilder( String[] domain ) { this(domain,new float[]{0.5f}); }
    public MetricBuilder( String[] domain, float[] thresholds ) {
      _domain = domain;
      int nclasses = _nclasses = (domain==null ? 1 : domain.length);
      // Thresholds are only for binomial classes
      assert (nclasses==2 && thresholds.length>0) || (nclasses!=2 && thresholds.length==1);
      _thresholds = thresholds;
      _cms = new long[thresholds.length][nclasses][nclasses];
      _work = new float[nclasses+1];
    }

    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution; (for regression, ds[0] has the prediction and ds[1] is ignored)
    public float[] perRow( float ds[], float yact ) {
      if( Float.isNaN(yact) ) return ds; // No errors if   actual   is missing
      if( Float.isNaN(ds[0])) return ds; // No errors if prediction is missing
      final int nclass = ds.length-1;
      final int iact = (int)yact;
      // Compute error
      float err;
      if( nclass>1 ) {          // Classification
        float sum = 0;          // Check for sane class distribution
        for( int i=1; i<ds.length; i++ ) { assert 0 <= ds[i] && ds[i] <= 1; sum += ds[i]; }
        assert Math.abs(sum-1.0f) < 1e-6;
        err = 1.0f-ds[iact+1];  // Error: distance from predicting ycls as 1.0
      } else {                  // Regression
        err = yact - ds[0];     // Error: distance from the actual
      }
      _sumsqe += err*err;       // Squared error
      assert !Double.isNaN(_sumsqe);
      // Pick highest prob for our prediction.
      if( nclass == 1 ) {       // Regression?
        _cms[0][0][0]++;        // Regression: count of rows only
      } else if( nclass == 2) { // Binomial classification -> compute AUC, draw ROC
        float snd = ds[2];      // Probability of a TRUE
        // TODO: Optimize this: just keep deltas from one CM to the next
        for(int i = 0; i < ModelUtils.DEFAULT_THRESHOLDS.length; i++) {
          int p = snd >= ModelUtils.DEFAULT_THRESHOLDS[i] ? 1 : 0; // Compute prediction based on threshold
          _cms[i][iact][p]++;   // Increase matrix
        }
      } else {                  // Plain Olde Confusion Matrix
        _cms[0][iact][(int)ds[0]]++; // actual v. predicted
      }
      return ds;                // Flow coding
    }
    public void reduce( MetricBuilder mb ) {
      ArrayUtils.add(_cms, mb._cms);
      _sumsqe += mb._sumsqe;
    }
    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
      AUCData aucdata;
      ConfusionMatrix cm;
      if( _cms.length > 1 ) {
        ConfusionMatrix[] cms = new ConfusionMatrix[_cms.length];
        for( int i=0; i<cms.length; i++ ) cms[i] = new ConfusionMatrix(_cms[i], _domain);
        aucdata = new AUC(cms,_thresholds,_domain).data();
        cm = aucdata.CM();
      } else {
        aucdata = null;
        cm = new ConfusionMatrix(_cms[0], _domain);
      }
      double mse = _sumsqe / cm.totalRows();
      HitRatio hr = null;       // TODO


      switch (m._output.getModelCategory()) {
        case Binomial:    return m._output.addModelMetrics(new ModelMetricsBinomial(   m, f, aucdata, cm, hr));
        case Multinomial: return m._output.addModelMetrics(new ModelMetricsMultinomial(m, f, cm, hr));
        case Regression:  return m._output.addModelMetrics(new ModelMetricsRegression( m, f, sigma, mse));
        case Clustering:  return m._output.addModelMetrics(new ModelMetricsClustering( m, f, null)); //FIXME: Each model should make its ModelMetrics object!
        case AutoEncoder: return m._output.addModelMetrics(new ModelMetricsAutoEncoder(m, f, mse));
//        case DimReduction: return m._output.addModelMetrics(new ModelMetricsDimReduction(m, f));
      }
      throw H2O.unimpl();
    }
  }
}
