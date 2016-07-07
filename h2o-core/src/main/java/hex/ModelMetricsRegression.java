package hex;

import water.MRTask;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.MathUtils;

public class ModelMetricsRegression extends ModelMetricsSupervised {
  public double residual_deviance() { return _mean_residual_deviance; }
  public final double _mean_residual_deviance;
  Distribution _dist;
  public ModelMetricsRegression(Model model, Frame frame, long nobs, double mse, double sigma, double meanResidualDeviance) {
    super(model, frame, nobs, mse, null, sigma);
    _mean_residual_deviance = meanResidualDeviance;
    _dist = new Distribution(model._parms);
  }

  public static ModelMetricsRegression getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);

    if (! (mm instanceof ModelMetricsRegression))
      throw new H2OIllegalArgumentException("Expected to find a Regression ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsRegression for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + mm.getClass());

    return (ModelMetricsRegression) mm;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" mean residual deviance: " + (float)_mean_residual_deviance + "\n");
    return sb.toString();
  }

  public static class MetricBuilderRegression<T extends MetricBuilderRegression<T>> extends MetricBuilderSupervised<T> {
    double _sumdeviance;
    public MetricBuilderRegression() {
      super(1,null); //this will make _work = new float[2];
    }

    // ds[0] has the prediction and ds[1] is ignored
    @Override public double[] perRow(double ds[], float[] yact, Model m) {return perRow(ds, yact, 1, 0, m);}
    @Override public double[] perRow(double ds[], float[] yact, double w, double o,  Model m) {
      if( Float.isNaN(yact[0]) ) return ds; // No errors if   actual   is missing
      if(ArrayUtils.hasNaNs(ds)) return ds;  // No errors if prediction has missing values (can happen for GLM)
      if(w == 0 || Double.isNaN(w)) return ds;
      // Compute error
      double err = yact[0] - ds[0]; // Error: distance from the actual
      _sumsqe += w*err*err;       // Squared error
      assert !Double.isNaN(_sumsqe);
      if (m!=null && m._parms._distribution!=Distribution.Family.huber)
        _sumdeviance += m.deviance(w, yact[0], ds[0]);
      _count++;
      _wcount += w;
      _wY += w*yact[0];
      _wYY += w*yact[0]*yact[0];
      return ds;                // Flow coding
    }

    @Override public void reduce( T mb ) {
      super.reduce(mb);
      _sumdeviance += mb._sumdeviance;
    }

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      double mse = _sumsqe / _wcount;
      if (adaptedFrame ==null) adaptedFrame = f;
      double meanResDeviance = 0;
      if (m!=null && m._parms._distribution== Distribution.Family.huber) {
        assert(_sumdeviance==0); // should not yet be computed
        if (preds != null) {
          Vec actual = adaptedFrame.vec(m._parms._response_column);
          Vec absdiff = new MRTask() {
            @Override public void map(Chunk[] cs, NewChunk[] nc) {
              for (int i=0; i<cs[0].len(); ++i)
                nc[0].addNum(Math.abs(cs[0].atd(i) - cs[1].atd(i)));
            }
          }.doAll(1, (byte)3, new Frame(new String[]{"preds","actual"}, new Vec[]{preds.anyVec(),actual})).outputFrame().anyVec();
          Distribution dist = new Distribution(m._parms);
          Vec weight = adaptedFrame.vec(m._parms._weights_column);
          //compute huber delta based on huber alpha quantile on absolute prediction error
          double huberDelta = MathUtils.computeWeightedQuantile(weight, absdiff, m._parms._huber_alpha);
          dist.setHuberDelta(huberDelta);
          meanResDeviance = new MeanResidualDeviance(dist, preds.anyVec(), actual, weight).exec().meanResidualDeviance;
        }
      } else {
        meanResDeviance = _sumdeviance / _wcount; //mean residual deviance
      }
      return m._output.addModelMetrics(new ModelMetricsRegression( m, f, _count, mse, weightedSigma(), meanResDeviance));
    }
  }
}
