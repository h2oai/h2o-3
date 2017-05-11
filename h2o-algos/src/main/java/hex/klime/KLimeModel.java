package hex.klime;

import hex.*;

import hex.glm.GLMModel;
import hex.klime.KLimeModel.*;
import hex.kmeans.KMeansModel;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.*;

import static hex.kmeans.KMeansModel.KMeansParameters;

public class KLimeModel extends Model<KLimeModel, KLimeParameters, KLimeOutput> {

  public KLimeModel(Key<KLimeModel> selfKey, KLimeParameters params, KLimeOutput output) {
    super(selfKey, params, output);
    assert(Arrays.equals(_key._kb, selfKey._kb));
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new KLimeMetricBuilder(_output._names.length - 1);
  }

  @Override
  public double[] score0(Chunk[] chks, double weight, int row_in_chunk, double[] tmp, double[] preds) {
    final double[] ps = _output._clustering.score0(chks, weight, row_in_chunk, tmp, preds);
    final int cluster = (int) ps[0];
    final GLMModel m = _output.getClusterModel(cluster);

    // preds[0] = value predicted by regression
    m.score0(chks, weight, row_in_chunk, tmp, preds);

    // preds[1] = cluster id
    preds[1] = cluster;

    // preds[2..n] = glm terms
    final DataInfo dinfo = m.dinfo();
    final double[] b = m.beta();
    int p = 2;
    for (int i = 0; i < dinfo._cats; i++) {
      int l = dinfo.getCategoricalId(i, tmp[i]);
      preds[p++] = (l >= 0) ? b[l] : Double.NaN;
    }
    int numStart = dinfo.numStart();
    for (int i = 0; i < dinfo._nums; i++) {
      double d = tmp[dinfo._cats + i];
      if (! dinfo._skipMissing && Double.isNaN(d))
        d = dinfo._numMeans[i];
      preds[p++] = b[numStart + i] * d;
    }
    return preds;
  }

  @Override
  protected String[] makeScoringNames() {
    String[] names = new String[_output._names.length + 1];
    int offset = 0;
    names[offset++] = "predict_klime";
    names[offset++] = "cluster_klime";
    for (int i = 0; i < _output._names.length - 1; i++) // last item of _output._names is the response column, remove it
      names[offset++] = "rc_" + _output._names[i];
    return names;
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    throw H2O.unimpl("Intentionally not implemented, should never be called!");
  }

  @Override
  public double deviance(double w, double y, double f) {
    return (y - f) * (y - f);
  }

  public static class KLimeParameters extends Model.Parameters {
    public String algoName() { return "KLime"; }
    public String fullName() { return "k-LIME"; }
    public String javaName() { return KLimeModel.class.getName(); }

    public int _min_cluster_size = 20;
    public int _max_k = 20;
    public double _alpha = 0.5;
    public boolean _estimate_k = true;

    @Override
    public long progressUnits() {
      return fillClusteringParms(new KMeansParameters(), null).progressUnits() + _max_k /*local GLMs*/ + 1 /*global GLM*/;
    }

    KMeansParameters fillClusteringParms(KMeansParameters p, Key<Frame> clusteringFrameKey) {
      p._k = _max_k;
      p._estimate_k = _estimate_k;
      p._train = clusteringFrameKey;
      p._auto_rebalance = false;
      p._seed = _seed;
      return p;
    }

    GLMModel.GLMParameters fillRegressionParms(GLMModel.GLMParameters p, Key<Frame> frameKey, boolean isWeighted) {
      p._family = GLMModel.GLMParameters.Family.gaussian;
      p._alpha = new double[] {_alpha};
      p._lambda_search = true;
      p._intercept = true;
      p._train = frameKey;
      p._response_column = _response_column;
      if (isWeighted)
        p._weights_column = "__cluster_weights";
      p._auto_rebalance = false;
      p._seed = _seed;
      return p;
    }
  }

  public static class KLimeOutput extends Model.Output {
    public KLimeOutput(KLime b) { super(b); }

    public KMeansModel _clustering;
    public GLMModel _globalRegressionModel;
    public GLMModel[] _regressionModels;

    public GLMModel getClusterModel(int cluster) {
      if ((cluster < 0) || (cluster >= _regressionModels.length)) {
        throw new IllegalStateException("Unknown cluster, cluster id = " + cluster);
      }
      return _regressionModels[cluster] != null ? _regressionModels[cluster] : _globalRegressionModel;
    }

    @Override public ModelCategory getModelCategory() { return ModelCategory.Regression; }
  }

  @Override
  protected Futures remove_impl(Futures fs) {
    if (_output._clustering != null)
      _output._clustering.remove(fs);
    if (_output._globalRegressionModel != null)
      _output._globalRegressionModel.remove(fs);
    if (_output._regressionModels != null)
      for (Model m : _output._regressionModels)
        if (m != null)
          m.remove(fs);
    return super.remove_impl(fs);
  }

  private static class KLimeMetricBuilder extends ModelMetricsRegression.MetricBuilderRegression<KLimeMetricBuilder> {
    private KLimeMetricBuilder(int numCodes) {
      _work = new double[1 /*predict_klime*/ + 1 /*cluster_klime*/ + numCodes];
    }
  }

}
