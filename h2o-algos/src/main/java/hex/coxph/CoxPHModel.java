package hex.coxph;

import hex.*;
import hex.coxph.CoxPHModel.CoxPHOutput;
import hex.coxph.CoxPHModel.CoxPHParameters;
import hex.schemas.CoxPHModelV3;
import water.Job;
import water.Key;
import water.MRTask;
import water.api.schemas3.ModelSchemaV3;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.ArrayUtils;

import java.util.Arrays;

public class CoxPHModel extends Model<CoxPHModel,CoxPHParameters,CoxPHOutput> {

  public static class CoxPHParameters extends Model.Parameters {
    public String algoName() { return "CoxPH"; }
    public String fullName() { return "Cox Proportional Hazards"; }
    public String javaName() { return CoxPHModel.class.getName(); }

    @Override public long progressUnits() { return _iter_max; }

    public String _start_column;
    public String _stop_column;
    final String _strata_column = "__strata";
    public String[] _stratify_by;

    public enum CoxPHTies { efron, breslow }

    public CoxPHTies _ties = CoxPHTies.efron;

    public String _rcall;
    public double _init = 0;
    public double _lre_min = 9;
    public int _iter_max = 20;

    public boolean _use_all_factor_levels;

    public String[] _interactions_only;
    public String[] _interactions = null;
    public StringPair[] _interaction_pairs = null;

    Vec startVec() { return train().vec(_start_column); }
    Vec stopVec() { return train().vec(_stop_column); }
    InteractionSpec interactionSpec() {
      // add "stratify by" columns to "interaction only"
      final String[] interOnly;
      if (_interactions_only != null && _stratify_by != null) {
        String[] io = _interactions_only.clone();
        Arrays.sort(io);
        String[] sb = _stratify_by.clone();
        Arrays.sort(sb);
        interOnly = ArrayUtils.union(io, sb, true);
      } else {
        interOnly = _interactions_only != null ? _interactions_only : _stratify_by;
      }
      return InteractionSpec.create(_interactions, _interaction_pairs, interOnly, _stratify_by);
    }

    boolean isStratified() { return _stratify_by != null && _stratify_by.length > 0; }
  }

  public static class CoxPHOutput extends Model.Output {

    public CoxPHOutput(CoxPH coxPH, Frame adaptFr) {
      super(coxPH, adaptFr);
      _ties = coxPH._parms._ties;
      _rcall = coxPH._parms._rcall;
      _interactionSpec = coxPH._parms.interactionSpec();
    }

    @Override
    public ModelCategory getModelCategory() { return ModelCategory.CoxPH; }

    @Override
    public InteractionSpec interactions() { return _interactionSpec; }

    InteractionSpec _interactionSpec;
    DataInfo data_info;

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
    double[] _time;
    double[] _n_risk;
    double[] _n_event;
    double[] _n_censor;
    double[] _cumhaz_0;
    double[] _var_cumhaz_1;
    double[][] _var_cumhaz_2;

    CoxPHParameters.CoxPHTies _ties;
    String _rcall;
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new ModelMetricsRegression.MetricBuilderRegression();
  }

  public ModelSchemaV3 schema() { return new CoxPHModelV3(); }

  public CoxPHModel(final Key destKey, final CoxPHParameters parms, final CoxPHOutput output) {
    super(destKey, parms, output);
  }

  @Override
  protected Frame predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    DataInfo scoringInfo = _output.data_info.scoringInfo(_output._names, adaptFrm);
    return new CoxPHScore(scoringInfo, _output).doAll(Vec.T_NUM, scoringInfo._adaptedFrame)
            .outputFrame(Key.<Frame>make(destination_key), new String[]{"lp"}, null);
  }

  @Override
  public String[] adaptTestForTrain(Frame test, boolean expensive, boolean computeMetrics) {
    if (_parms.isStratified() && (test.vec(_parms._strata_column) == null)) {
      Vec strataVec = test.anyVec().makeCon(Double.NaN);
      _toDelete.put(strataVec._key, "adapted missing strata vector");
      test.add(_parms._strata_column, strataVec);
    }
    return super.adaptTestForTrain(test, expensive, computeMetrics);
  }

  private static class CoxPHScore extends MRTask<CoxPHScore> {
    private DataInfo _dinfo;
    private double[] _coef;
    private double _lpBase;
    private int _numStart;

    private CoxPHScore(DataInfo dinfo, CoxPHOutput o) {
      _dinfo = dinfo;
      _coef = o._coef;
      _numStart = o._x_mean_cat.length;
      _lpBase = 0;
      for (int i = 0; i < o._x_mean_cat.length; i++)
        _lpBase += o._x_mean_cat[i] * _coef[i];
      for (int i = 0; i < o._x_mean_num.length; i++)
        _lpBase += o._x_mean_num[i] * _coef[i + _numStart];
    }

    @Override
    public void map(Chunk[] chks, NewChunk nc) {
      DataInfo.Row r = _dinfo.newDenseRow();
      for (int rid = 0; rid < chks[0]._len; ++rid) {
        _dinfo.extractDenseRow(chks, rid, r);
        if (r.predictors_bad) {
          nc.addNA();
          continue;
        }
        double lp = r.innerProduct(_coef) - _lpBase;
        nc.addNum(lp);
      }
    }
  }

  @Override public double[] score0(double[] data, double[] preds) {
    throw new UnsupportedOperationException("CoxPHModel.score0 should never be called");
  }

}

