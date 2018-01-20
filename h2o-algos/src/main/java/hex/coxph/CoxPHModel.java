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
import water.api.schemas3.ModelSchemaV3;
import water.fvec.Vec;

public class CoxPHModel extends Model<CoxPHModel,CoxPHParameters,CoxPHOutput> {

  public static class CoxPHParameters extends Model.Parameters {
    public String algoName() { return "CoxPH"; }
    public String fullName() { return "Cox Proportional Hazards"; }
    public String javaName() { return CoxPHModel.class.getName(); }

    @Override public long progressUnits() { return _iter_max; }

    public String _start_column;
    public String _stop_column;

    public enum CoxPHTies { efron, breslow }

    public CoxPHTies _ties = CoxPHTies.efron;

    public double _init = 0;
    public double _lre_min = 9;
    public int _iter_max = 20;

    Vec startVec() { return train().vec(_start_column); }
    Vec stopVec() { return train().vec(_stop_column); }
  }

  public static class CoxPHOutput extends Model.Output {
    public CoxPHOutput( CoxPH b ) { super(b); }

    DataInfo data_info;
    // FIXME: these should most likely not be in the model output
    double[] gradient;
    double[][] hessian;

    String[] _coef_names;
    double[] _coef;
    double[] _exp_coef;
    double[] _exp_neg_coef;
    double[] _se_coef;
    double[] _z_coef;
    double[][] _var_coef;
    double _null_loglik;
    double _loglik;
    double _loglik_test;
    double _wald_test;
    double _score_test;
    double _rsq;
    double _maxrsq;
    double _lre;
    int _iter;
    double[] _x_mean_cat;
    double[] _x_mean_num;
    double[] _mean_offset;
    String[] _offset_names;
    long _n;
    long _n_missing;
    long _total_event;
    long _min_time;
    long _max_time;
    long[] _time;
    double[] _n_risk;
    double[] _n_event;
    double[] _n_censor;
    double[] _cumhaz_0;
    double[] _var_cumhaz_1;
    double[][] _var_cumhaz_2;
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsRegression.MetricBuilderRegression();
  }

  // Default publically visible Schema is V2
  public ModelSchemaV3 schema() { return new CoxPHModelV3(); }

  // @Override
  public final CoxPHParameters get_params() { return _parms; }

  public CoxPHModel(final Key destKey, final CoxPHParameters parms, final CoxPHOutput output) {
    super(destKey, parms, output);
  }

  /**
   * Predict from raw double values representing the data
   * @param data raw array containing categorical values (horizontalized to 1,0,0,1,0,0 etc.) and numerical values (0.35,1.24,5.3234,etc), both can contain NaNs
   * @param preds predicted label and per-class probabilities (for classification), predicted target (regression), can contain NaNs
   * @return preds, can contain NaNs
   */
  @Override public double[] score0(double[] data, double[] preds) {
    final int n_offsets = _parms._offset_column == null ? 0 : 1;
    final int n_time    = _output._time.length;
    final int n_coef    = _output._coef.length;
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
          System.arraycopy(_output._x_mean_cat, kst, full_data, kst, klen);
        } else if (data[j] != 0)
          full_data[_output.data_info._catOffsets[j] + (int) (data[j] - 1)] = 1;
      for (int j = 0; j < n_nums; ++j)
        full_data[numStart + j] = data[n_cats + j] - _output.data_info._normSub[j];
      double logRisk = 0;
      for (int j = 0; j < n_coef; ++j)
        logRisk += full_data[j] * _output._coef[j];
      for (int j = n_coef; j < full_data.length; ++j)
        logRisk += full_data[j];
      final double risk = Math.exp(logRisk);
      for (int t = 0; t < n_time; ++t)
        preds[t + 1] = risk * _output._cumhaz_0[t];
      for (int t = 0; t < n_time; ++t) {
        final double cumhaz_0_t = _output._cumhaz_0[t];
        double var_cumhaz_2_t = 0;
        for (int j = 0; j < n_coef; ++j) {
          double sum = 0;
          for (int k = 0; k < n_coef; ++k)
            sum += _output._var_coef[j][k] * (full_data[k] * cumhaz_0_t - _output._var_cumhaz_2[t][k]);
          var_cumhaz_2_t += (full_data[j] * cumhaz_0_t - _output._var_cumhaz_2[t][j]) * sum;
        }
        preds[t + 1 + n_time] = risk * Math.sqrt(_output._var_cumhaz_1[t] + var_cumhaz_2_t);
      }
    }
    preds[0] = Double.NaN;
    return preds;
  }
}

