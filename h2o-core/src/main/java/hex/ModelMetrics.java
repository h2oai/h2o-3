package hex;

import water.*;
import water.api.ModelMetricsBase;
import water.api.ModelMetricsV3;
import water.fvec.Chunk;
import water.fvec.Frame;

/** Container to hold the metric for a model as scored on a specific frame. 
 *  
 *  The MetricBuilder class is used in a hot inner-loop of a Big Data pass, and
 *  when given a class-distribution, can be used to compute CM's, and AUC's "on
 *  the fly" during ModelBuilding - or after-the-fact with a Model and a new
 *  Frame to be scored.
 */
public final class ModelMetrics extends Keyed {
  final Key _modelKey;
  final Key _frameKey;
  final long _model_checksum;
  final long _frame_checksum;
  transient Model _model;
  transient Frame _frame;

  long duration_in_ms = -1L;
  long scoring_time = -1L;

  public final double _sigma;   // stddev of the response (if any)
  public final double _mse;     // Mean Squared Error
  public final AUCData _aucdata;
  public final ConfusionMatrix2 _cm;
  public final HitRatio _hr;

  public ModelMetrics(Model model, Frame frame) {this(model,frame,null,null,null,Double.NaN,Double.NaN); }
  private ModelMetrics(Model model, Frame frame, AUCData aucdata, ConfusionMatrix2 cm, HitRatio hr, double sigma, double mse) {
    super(buildKey(model, frame));
    _modelKey = model._key;
    _frameKey = frame._key;
    _model = model;
    _frame = frame;
    _model_checksum = model.checksum();
    _frame_checksum = frame.checksum();
    _sigma = sigma;
    _mse = mse;
    _aucdata = aucdata;
    _cm = cm;
    _hr = hr;
    DKV.put(this);
  }

  public Model model() { return _model==null ? (_model=DKV.getGet(_modelKey)) : _model; }
  public Frame frame() { return _model==null ? (_frame=DKV.getGet(_frameKey)) : _frame; }

  // r2 => improvement over random guess of the mean
  public double r2() {
    double var = _sigma*_sigma;
    return 1.0-(_mse/var);
  }

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
      int nclasses = _nclasses = domain.length;
      // Thresholds are only for binomial classes
      assert (nclasses==2 && thresholds.length>0) || (nclasses!=2 && thresholds.length==1);
      _thresholds = thresholds;
      _cms = new long[thresholds.length][nclasses][nclasses];
      _work = new float[nclasses+1];
    }

    // Passed a float[] sized nclasses+1; ds[0] must be a class prediction
    // (ignored for regression).  ds[1...nclasses-1] must be a class
    // distribution (and ds[1] has the regression prediction)
    public float[] perRow( float ds[], float yact ) {
      final int nclass = ds.length-1;
      // Compute error 
      float err;
      if( nclass>1 ) {          // Classification
        float sum = 0;          // Check for *no prediction at all*
        for( int i=1; i<ds.length; i++ ) { assert 0 <= ds[i] && ds[i] <= 1; sum += ds[i]; }
        assert Math.abs(sum-1.0f) < 1e-6;
        err = 1.0f-ds[(int)yact+1];  // Error: distance from predicting ycls as 1.0
      } else {                  // Regression
        err = yact - ds[1];
      }
      _sumsqe += err*err;       // Squared error
      assert !Double.isNaN(_sumsqe);
      // Pick highest prob for our prediction.
      if( nclass == 1 ) {       // Regression?
        _cms[0][0][0]++;        // Regression: count of rows only
      } else if( nclass == 2 ) { // Binomial classification -> compute AUC, draw ROC
        throw H2O.unimpl();
        //float snd = _validation ? fs[2] : (!Float.isInfinite(sum) ? fs[2] / sum : Float.isInfinite(fs[2]) ? 1 : 0); // for validation dataset sum is always 1
        //for(int i = 0; i < ModelUtils.DEFAULT_THRESHOLDS.length; i++) {
        //  int p = snd >= ModelUtils.DEFAULT_THRESHOLDS[i] ? 1 : 0; // Compute prediction based on threshold
        //  _cms[i][yact][p]++; // Increase matrix
        //}
      } else {                  // Plain Olde Confusion Matrix
        _cms[0][(int)yact][(int)ds[0]]++; // actual v. predicted
      }
      return ds;                // Flow coding
    }
    public void reduce( MetricBuilder mb ) {
      throw H2O.unimpl();
    }
    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) { 
      AUCData aucdata;
      ConfusionMatrix2 cm;
      if( _cms.length > 1 ) {
        ConfusionMatrix2[] cms = new ConfusionMatrix2[_cms.length];
        for( int i=0; i<cms.length; i++ ) cms[i] = new ConfusionMatrix2(_cms[i]);
        aucdata = new AUC(cms,_thresholds,_domain).data();
        cm = aucdata.CM();
        throw H2O.unimpl();
      } else {
        aucdata = null;
        cm = new ConfusionMatrix2(_cms[0]);
      }
      double mse = _sumsqe / cm.totalRows();
      HitRatio hr = null;       // TODO
      return m._output.addModelMetrics(new ModelMetrics(m,f,aucdata,cm,hr,sigma,mse));
    }
  }

  /**
   * Externally visible default schema
   * TODO: this is in the wrong layer: the internals should not know anything about the schemas!!!
   * This puts a reverse edge into the dependency graph.
   */
  public ModelMetricsBase schema() { return new ModelMetricsV3(); }


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

  @Override public long checksum() { return _frame_checksum * 13 + _model_checksum * 17; }
}
