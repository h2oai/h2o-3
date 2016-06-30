package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;

import java.awt.color.ICC_ColorSpace;

public class ModelMetricsBinomial extends ModelMetricsSupervised {
  public final AUC2 _auc;
  public final double _logloss;
  public final double _mean_per_class_error;
  public final GainsLift _gainsLift;

  public ModelMetricsBinomial(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma, AUC2 auc, double logloss, GainsLift gainsLift) {
    super(model, frame,  nobs, mse, domain, sigma);
    _auc = auc;
    _logloss = logloss;
    _gainsLift = gainsLift;
    _mean_per_class_error = cm() == null ? Double.NaN : cm().mean_per_class_error();
  }

  public static ModelMetricsBinomial getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);
    if( !(mm instanceof ModelMetricsBinomial) )
      throw new H2OIllegalArgumentException("Expected to find a Binomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsBinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + (mm == null ? null : mm.getClass()));
    return (ModelMetricsBinomial) mm;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    if (_auc != null) sb.append(" AUC: " + (float)_auc._auc + "\n");
    sb.append(" logloss: " + (float)_logloss + "\n");
    sb.append(" mean_per_class_error: " + (float)_mean_per_class_error + "\n");
    if (cm() != null) sb.append(" CM: " + cm().toASCII());
    if (_gainsLift != null) sb.append(_gainsLift);
    return sb.toString();
  }

  public double logloss() { return _logloss; }
  public double mean_per_class_error() { return _mean_per_class_error; }
  @Override public AUC2 auc_obj() { return _auc; }
  @Override public ConfusionMatrix cm() {
    if( _auc == null ) return null;
    double[][] cm = _auc.defaultCM();
    return cm == null ? null : new ConfusionMatrix(cm, _domain);
  }
  public GainsLift gainsLift() { return _gainsLift; }

  // expose simple metrics criteria for sorting
  public double auc() { return auc_obj()._auc; }
  public double lift_top_group() { return gainsLift().response_rates[0] / gainsLift().avg_response_rate; }


  public static class MetricBuilderBinomial<T extends MetricBuilderBinomial<T>> extends MetricBuilderSupervised<T> {
    protected double _logloss;
    protected AUC2.AUCBuilder _auc;

    public MetricBuilderBinomial( String[] domain ) { super(2,domain); _auc = new AUC2.AUCBuilder(AUC2.NBINS); }

    public double auc() {return new AUC2(_auc)._auc;}

    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution;
    @Override public double[] perRow(double ds[], float[] yact, Model m) {return perRow(ds, yact, 1, 0, m);}
    @Override public double[] perRow(double ds[], float[] yact, double w, double o, Model m) {
      if( Float .isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if(ArrayUtils.hasNaNs(ds)) return ds;  // No errors if prediction has missing values (can happen for GLM)
      if(w == 0 || Double.isNaN(w)) return ds;
      final int iact = (int)yact[0];
      if( iact != 0 && iact != 1 ) return ds; // The actual is effectively a NaN
      _count++;
      _wcount += w;
      _wY += w*iact;
      _wYY += w*iact*iact;
      // Compute error
      double err = iact+1 < ds.length ? 1-ds[iact+1] : 1;  // Error: distance from predicting ycls as 1.0
      _sumsqe += w*err*err;           // Squared error
      assert !Double.isNaN(_sumsqe);

      // Compute log loss
      final double eps = 1e-15;
      _logloss -= w*Math.log(Math.max(eps, 1-err));
      _auc.perRow(ds[2],iact,w);
      return ds;                // Flow coding
    }

    @Override public void reduce( T mb ) {
      super.reduce(mb); // sumseq, count
      _logloss += mb._logloss;
      _auc.reduce(mb._auc);
    }

    /**
     * Create a ModelMetrics for a given model and frame
     * @param m Model
     * @param f Frame
     * @param frameWithWeights Frame that contains extra columns such as weights
     * @param preds Optional predictions (can be null), only used to compute Gains/Lift table for binomial problems  @return
     * @return
     */
    @Override public ModelMetrics makeModelMetrics(Model m, Frame f, Frame frameWithWeights, Frame preds) {
      if (frameWithWeights ==null) frameWithWeights = f;
      double mse = Double.NaN;
      double logloss = Double.NaN;
      double sigma = Double.NaN;
      if (_wcount > 0) {
        sigma = weightedSigma();
        mse = _sumsqe / _wcount;
        logloss = _logloss / _wcount;
        AUC2 auc = new AUC2(_auc);
        GainsLift gl = null;
        if (preds!=null) {
          Vec resp = f.vec(m._parms._response_column);
          Vec weight = frameWithWeights.vec(m._parms._weights_column);
          if (resp != null) {
            gl = new GainsLift(preds.lastVec(), resp, weight);
            gl.exec(m._output._job);
          }
        }
        return m._output.addModelMetrics(new ModelMetricsBinomial(m, f, _count, mse, _domain, sigma, auc,  logloss, gl));
      } else {
        return m._output.addModelMetrics(new ModelMetricsBinomial(m, f, _count, mse,   null,  sigma, null, logloss, null));
      }
    }
    public String toString(){
      if(_wcount == 0) return "empty, no rows";
      return "auc = " + MathUtils.roundToNDigits(auc(),3) + ", logloss = " + _logloss / _wcount;
    }
  }
}
