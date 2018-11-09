package hex;

import hex.genmodel.GenModel;
import hex.genmodel.utils.DistributionFamily;
import water.MRTask;
import water.Scope;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.C8DVolatileChunk;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.util.Arrays;

public class ModelMetricsBinomial extends ModelMetricsSupervised {
  public final AUC2 _auc;
  public final double _logloss;
  public final double _mean_per_class_error;
  public final GainsLift _gainsLift;

  public ModelMetricsBinomial(Model model, Frame frame, long nobs, double mse, String[] domain,
                              double sigma, AUC2 auc, double logloss, GainsLift gainsLift,
                              CustomMetric customMetric) {
    super(model, frame,  nobs, mse, domain, sigma, customMetric);
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
    if (_auc != null) {
      sb.append(" AUC: " + (float)_auc._auc + "\n");
      sb.append(" pr_auc: " + (float)_auc.pr_auc() + "\n");
    }
    sb.append(" logloss: " + (float)_logloss + "\n");
    sb.append(" mean_per_class_error: " + (float)_mean_per_class_error + "\n");
    sb.append(" default threshold: " + (_auc == null ? 0.5 : (float)_auc.defaultThreshold()) + "\n");
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

  /**
   * Build a Binomial ModelMetrics object from target-class probabilities, from actual labels, and a given domain for both labels (and domain[1] is the target class)
   * @param targetClassProbs A Vec containing target class probabilities
   * @param actualLabels A Vec containing the actual labels (can be for fewer labels than what's in domain, since the predictions can be for a small subset of the data)
   * @return ModelMetrics object
   */
  static public ModelMetricsBinomial make(Vec targetClassProbs, Vec actualLabels) {
    return make(targetClassProbs,actualLabels,actualLabels.domain());
  }

  /**
   * Build a Binomial ModelMetrics object from target-class probabilities, from actual labels, and a given domain for both labels (and domain[1] is the target class)
   * @param targetClassProbs A Vec containing target class probabilities
   * @param actualLabels A Vec containing the actual labels (can be for fewer labels than what's in domain, since the predictions can be for a small subset of the data)
   * @param domain The two class labels (domain[0] is the non-target class, domain[1] is the target class, for which probabilities are given)
   * @return ModelMetrics object
   */
  static public ModelMetricsBinomial make(Vec targetClassProbs, Vec actualLabels, String[] domain) {
    Scope.enter();
    Vec _labels = actualLabels.toCategoricalVec();
    if (domain==null) domain = _labels.domain();
    if (_labels == null || targetClassProbs == null)
      throw new IllegalArgumentException("Missing actualLabels or predictedProbs for binomial metrics!");
    if (!targetClassProbs.isNumeric())
      throw new IllegalArgumentException("Predicted probabilities must be numeric per-class probabilities for binomial metrics.");
    if (targetClassProbs.min() < 0 || targetClassProbs.max() > 1)
      throw new IllegalArgumentException("Predicted probabilities must be between 0 and 1 for binomial metrics.");
    if (domain.length!=2)
      throw new IllegalArgumentException("Domain must have 2 class labels, but is " + Arrays.toString(domain) + " for binomial metrics.");
    _labels = _labels.adaptTo(domain);
    if (_labels.cardinality()!=2)
      throw new IllegalArgumentException("Adapted domain must have 2 class labels, but is " + Arrays.toString(_labels.domain()) + " for binomial metrics.");
    Frame predsLabel = new Frame(targetClassProbs);
    predsLabel.add("labels", _labels);
    MetricBuilderBinomial mb = new BinomialMetrics(_labels.domain()).doAll(predsLabel)._mb;
    _labels.remove();
    Frame preds = new Frame(targetClassProbs);
    ModelMetricsBinomial mm = (ModelMetricsBinomial)mb.makeModelMetrics(null, predsLabel, null, preds);
    mm._description = "Computed on user-given predictions and labels, using F1-optimal threshold: " + mm.auc_obj().defaultThreshold() + ".";
    Scope.exit();
    return mm;
  }

  // helper to build a ModelMetricsBinomial for a N-class problem from a Frame that contains N per-class probability columns, and the actual label as the (N+1)-th column
  private static class BinomialMetrics extends MRTask<BinomialMetrics> {
    public BinomialMetrics(String[] domain) { this.domain = domain; }
    String[] domain;
    public MetricBuilderBinomial _mb;
    @Override public void map(Chunk[] chks) {
      _mb = new MetricBuilderBinomial(domain);
      Chunk actuals = chks[1];
      double [] ds = new double[3];
      for (int i=0;i<chks[0]._len;++i) {
        ds[2] = chks[0].atd(i); //class 1 probs (user-given)
        ds[1] = 1-ds[2]; //class 0 probs
        ds[0] = GenModel.getPrediction(ds, null, ds, Double.NaN/*ignored - uses AUC's default threshold*/); //label
        _mb.perRow(ds, new float[]{(float)actuals.atd(i)}, null);
      }
    }
    @Override public void reduce(BinomialMetrics mrt) { _mb.reduce(mrt._mb); }
  }

  public static class MetricBuilderBinomial<T extends MetricBuilderBinomial<T>> extends MetricBuilderSupervised<T> {
    protected double _logloss;
    protected AUC2.AUCBuilder _auc;

    public MetricBuilderBinomial( String[] domain ) { super(2,domain); _auc = new AUC2.AUCBuilder(AUC2.NBINS); }

    public double auc() {return new AUC2(_auc)._auc;}
    public double pr_auc() { return new AUC2(_auc)._pr_auc;}

    // Passed a float[] sized nclasses+1; ds[0] must be a prediction.  ds[1...nclasses-1] must be a class
    // distribution;
    @Override public double[] perRow(double ds[], float[] yact, Model m) {return perRow(ds, yact, 1, 0, m);}
    @Override public double[] perRow(double ds[], float[] yact, double w, double o, Model m) {
      if( Float .isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if(ArrayUtils.hasNaNs(ds)) return ds;  // No errors if prediction has missing values (can happen for GLM)
      if(w == 0 || Double.isNaN(w)) return ds;
      int iact = (int)yact[0];
      boolean quasibinomial = (m!=null && m._parms._distribution == DistributionFamily.quasibinomial);
      if (quasibinomial) {
        if (yact[0] != 0)
          iact = 1;  // actual response index needed for confusion matrix, AUC, etc.
        _wY += w * yact[0];
        _wYY += w * yact[0] * yact[0];
        // Compute error
        double err = yact[0] - ds[iact + 1];
        _sumsqe += w * err * err;           // Squared error
        // Compute negative loglikelihood loss, according to https://0xdata.atlassian.net/secure/attachment/30135/30135_TMLErare.pdf Appendix C
        _logloss += - w * (yact[0] * Math.log(Math.max(1e-15, ds[2])) + (1-yact[0]) * Math.log(Math.max(1e-15, ds[1])));
      } else {
        if (iact != 0 && iact != 1) return ds; // The actual is effectively a NaN
        _wY += w * iact;
        _wYY += w * iact * iact;
        // Compute error
        double err = iact + 1 < ds.length ? 1 - ds[iact + 1] : 1;  // Error: distance from predicting ycls as 1.0
        _sumsqe += w * err * err;           // Squared error
        // Compute log loss
        _logloss += w * MathUtils.logloss(err);
      }
      _count++;
      _wcount += w;
      assert !Double.isNaN(_sumsqe);
      _auc.perRow(ds[2], iact, w);
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
     * @return ModelMetricsBinomial
     */
    @Override public ModelMetrics makeModelMetrics(Model m, Frame f, Frame frameWithWeights, Frame preds) {
      GainsLift gl = null;
      if (_wcount > 0) {
        if (preds!=null) {
          if (frameWithWeights == null) frameWithWeights = f;
          Vec resp = m==null && frameWithWeights.vec(f.numCols()-1).isCategorical() ? frameWithWeights.vec(f.numCols()-1) //work-around for the case where we don't have a model, assume that the last column is the actual response
                  : frameWithWeights.vec(m._parms._response_column);
          if (resp != null) {
            Vec weight = m==null?null : frameWithWeights.vec(m._parms._weights_column);
            gl = calculateGainsLift(m, preds, resp, weight);
          }
        }
      }
      return makeModelMetrics(m, f, gl);
    }

    private ModelMetrics makeModelMetrics(Model m, Frame f, GainsLift gl) {
      double mse = Double.NaN;
      double logloss = Double.NaN;
      double sigma = Double.NaN;
      final AUC2 auc;
      if (_wcount > 0) {
        sigma = weightedSigma();
        mse = _sumsqe / _wcount;
        logloss = _logloss / _wcount;
        auc = new AUC2(_auc);
      } else {
        auc = new AUC2();
      }
      ModelMetricsBinomial mm = new ModelMetricsBinomial(m, f, _count, mse, _domain, sigma, auc,  logloss, gl, _customMetric);
      if (m!=null) m.addModelMetrics(mm);
      return mm;
    }

    private GainsLift calculateGainsLift(Model m, Frame preds, Vec resp, Vec weights) {
      GainsLift gl = null;
      try {
        gl = new GainsLift(preds.lastVec(), resp, weights);
        gl.exec(m != null ? m._output._job : null);
      } catch(Throwable t) { // TODO: Why do we need to catch Throwable here?
        Log.debug("Calculating Gains-Lift failed", t);
      }
      return gl;
    }

    @Override
    public Frame makePredictionCache(Model m, Vec response) {
      return new Frame(response.makeVolatileDoubles(1));
    }

    @Override
    public void cachePrediction(double[] cdist, Chunk[] chks, int row, int cacheChunkIdx, Model m) {
      assert cdist.length == 3;
      ((C8DVolatileChunk) chks[cacheChunkIdx]).getValues()[row] = cdist[cdist.length - 1];
    }

    public String toString(){
      if(_wcount == 0) return "empty, no rows";
      return "auc = " + MathUtils.roundToNDigits(auc(),3) + ", logloss = " + _logloss / _wcount;
    }
  }
}
