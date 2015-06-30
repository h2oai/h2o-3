package hex.coxph;


import hex.DataInfo;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsRegression;
import hex.coxph.CoxPHModel.CoxPHOutput;
import hex.coxph.CoxPHModel.CoxPHParameters;
import hex.schemas.CoxPHModelV3;
import water.Key;
import water.MemoryManager;
import water.api.ModelSchema;
import water.fvec.Vec;

/**
 * The Deep Learning model
 * It contains a DeepLearningModelInfo with the most up-to-date model,
 * a scoring history, as well as some helpers to indicate the progress
 */

public class CoxPHModel extends Model<CoxPHModel,CoxPHParameters,CoxPHOutput> {
  public static class CoxPHParameters extends Model.Parameters {
    // get destination_key  from SupervisedModel.SupervisedParameters from Model.Parameters
    // get training_frame   from SupervisedModel.SupervisedParameters from Model.Parameters
    // get validation_frame from SupervisedModel.SupervisedParameters from Model.Parameters
    // get "response_column" from SupervisedModel.SupervisedParameters
    // get "ignored_columns" from SupervisedModel.SupervisedParameters from Model.Parameters

    public Vec start_column;
    public Vec stop_column;
    public Vec event_column;
    public Vec weights_column;
    public Vec[] offset_columns;
    public static enum CoxPHTies { efron, breslow }
    public CoxPHTies ties = CoxPHTies.efron;
    public double init = 0;
    public double lre_min = 9;
    public int iter_max = 20;
  }

  public static class CoxPHOutput extends Model.Output {
    public CoxPHOutput( CoxPH b ) { super(b); }

    DataInfo data_info;
    String[] coef_names;
    double[] coef;
    double[] exp_coef;
    double[] exp_neg_coef;
    double[] se_coef;
    double[] z_coef;
    double[][] var_coef;
    double null_loglik;
    double loglik;
    double loglik_test;
    double wald_test;
    double score_test;
    double rsq;
    double maxrsq;
    double[] gradient;
    double[][] hessian;
    double lre;
    int iter;
    double[] x_mean_cat;
    double[] x_mean_num;
    double[] mean_offset;
    String[] offset_names;
    long n;
    long n_missing;
    long total_event;
    long min_time;
    long max_time;
    long[] time;
    double[] n_risk;
    double[] n_event;
    double[] n_censor;
    double[] cumhaz_0;
    double[] var_cumhaz_1;
    double[][] var_cumhaz_2;
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsRegression.MetricBuilderRegression();
  }

  // Default publically visible Schema is V2
  public ModelSchema schema() { return new CoxPHModelV3(); }

  // @Override
  public final CoxPHParameters get_params() { return _parms; }

  public CoxPHModel(final Key destKey, final CoxPHParameters parms, final CoxPHOutput output) {
    super(destKey, parms, output);
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("CoxPHModel toString() UNIMPLEMENTED");
    return sb.toString();
  }

  public String toStringAll() {
    StringBuilder sb = new StringBuilder();
    sb.append("CoxPHModel toStringAll() UNIMPLEMENTED");
    return sb.toString();
  }

  /**
   * Predict from raw double values representing the data
   * @param data raw array containing categorical values (horizontalized to 1,0,0,1,0,0 etc.) and numerical values (0.35,1.24,5.3234,etc), both can contain NaNs
   * @param preds predicted label and per-class probabilities (for classification), predicted target (regression), can contain NaNs
   * @return preds, can contain NaNs
   */
  @Override public double[] score0(double[] data, double[] preds) {
    final int n_offsets = (_parms.offset_columns == null) ? 0 : _parms.offset_columns.length;
    final int n_time    = _output.time.length;
    final int n_coef    = _output.coef.length;
    final int n_cats    = _output.data_info._cats;
    final int n_nums    = _output.data_info._nums;
    final int n_data    = n_cats + n_nums;
    final int n_full    = n_coef + n_offsets;
    final int numStart  = _output.data_info.numStart();
    boolean catsAllNA   = true;
    boolean catsHasNA   = false;
    boolean numsHasNA   = false;
    for (int j = 0; j < n_cats; ++j) {
      catsAllNA &= Double.isNaN(data[j]);
      catsHasNA |= Double.isNaN(data[j]);
    }
    for (int j = n_cats; j < n_data; ++j)
      numsHasNA |= Double.isNaN(data[j]);
    if (numsHasNA || (catsHasNA && !catsAllNA)) {
      for (int i = 1; i <= 2 * n_time; ++i)
        preds[i] = Double.NaN;
    } else {
      double[] full_data = MemoryManager.malloc8d(n_full);
      for (int j = 0; j < n_cats; ++j)
        if (Double.isNaN(data[j])) {
          final int kst = _output.data_info._catOffsets[j];
          final int klen = _output.data_info._catOffsets[j+1] - kst;
          System.arraycopy(_output.x_mean_cat, kst, full_data, kst, klen);
        } else if (data[j] != 0)
          full_data[_output.data_info._catOffsets[j] + (int) (data[j] - 1)] = 1;
      for (int j = 0; j < n_nums; ++j)
        full_data[numStart + j] = data[n_cats + j] - _output.data_info._normSub[j];
      double logRisk = 0;
      for (int j = 0; j < n_coef; ++j)
        logRisk += full_data[j] * _output.coef[j];
      for (int j = n_coef; j < full_data.length; ++j)
        logRisk += full_data[j];
      final double risk = Math.exp(logRisk);
      for (int t = 0; t < n_time; ++t)
        preds[t + 1] = risk * _output.cumhaz_0[t];
      for (int t = 0; t < n_time; ++t) {
        final double cumhaz_0_t = _output.cumhaz_0[t];
        double var_cumhaz_2_t = 0;
        for (int j = 0; j < n_coef; ++j) {
          double sum = 0;
          for (int k = 0; k < n_coef; ++k)
            sum += _output.var_coef[j][k] * (full_data[k] * cumhaz_0_t - _output.var_cumhaz_2[t][k]);
          var_cumhaz_2_t += (full_data[j] * cumhaz_0_t - _output.var_cumhaz_2[t][j]) * sum;
        }
        preds[t + 1 + n_time] = risk * Math.sqrt(_output.var_cumhaz_1[t] + var_cumhaz_2_t);
      }
    }
    preds[0] = Double.NaN;
    return preds;
  }
}

