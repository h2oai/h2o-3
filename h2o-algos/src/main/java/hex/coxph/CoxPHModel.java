package hex.coxph;

import hex.*;
import hex.coxph.CoxPHModel.CoxPHOutput;
import hex.coxph.CoxPHModel.CoxPHParameters;
import hex.schemas.CoxPHModelV3;
import water.*;
import water.api.schemas3.ModelSchemaV3;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.ast.prims.mungers.AstGroup;
import water.udf.CFuncRef;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.IcedInt;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CoxPHModel extends Model<CoxPHModel,CoxPHParameters,CoxPHOutput> {

  public static class CoxPHParameters extends Model.Parameters {
    public String algoName() { return "CoxPH"; }
    public String fullName() { return "Cox Proportional Hazards"; }
    public String javaName() { return CoxPHModel.class.getName(); }

    @Override public long progressUnits() { return ((_max_iterations + 1) * 2) + 1; }

    public String _start_column;
    public String _stop_column;
    final String _strata_column = "__strata";
    public String[] _stratify_by;

    public enum CoxPHTies { efron, breslow }

    public CoxPHTies _ties = CoxPHTies.efron;

    public double _init = 0;
    public double _lre_min = 9;
    public int _max_iterations = 20;

    public boolean _use_all_factor_levels;

    public String[] _interactions_only;
    public String[] _interactions = null;
    public StringPair[] _interaction_pairs = null;

    public boolean _calc_cumhaz = true; // support survfit

    String[] responseCols() {
      String[] cols = _start_column != null ? new String[]{_start_column} : new String[0];
      if (isStratified())
        cols = ArrayUtils.append(cols, _start_column);
      return ArrayUtils.append(cols, _stop_column, _response_column);
    }

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

    String toFormula(Frame f) {
      StringBuilder sb = new StringBuilder();
      sb.append("Surv(");
      if (_start_column != null) {
        sb.append(_start_column).append(", ");
      }
      sb.append(_stop_column).append(", ").append(_response_column);
      sb.append(") ~ ");
      Set<String> stratifyBy = _stratify_by != null ? new HashSet<>(Arrays.asList(_stratify_by)) : Collections.<String>emptySet();
      Set<String> interactionsOnly = _interactions_only != null ? new HashSet<>(Arrays.asList(_interactions_only)) : Collections.<String>emptySet();
      Set<String> specialCols = new HashSet<String>() {{
        add(_start_column);
        if (_stop_column != null)
          add(_stop_column);
        add(_response_column);
        add(_strata_column);
        if (_weights_column != null)
          add(_weights_column);
        if (_ignored_columns != null)
          addAll(Arrays.asList(_ignored_columns));
      }};
      String sep = "";
      for (String col : f._names) {
        if (_offset_column != null && _offset_column.equals(col))
          continue;
        if (stratifyBy.contains(col) || interactionsOnly.contains(col) || specialCols.contains(col))
          continue;
        sb.append(sep).append(col);
        sep = " + ";
      }
      if (_offset_column != null)
        sb.append(sep).append("offset(").append(_offset_column).append(")");
      InteractionSpec interactionSpec = interactionSpec();
      if (interactionSpec != null) {
        InteractionPair[] interactionPairs = interactionSpec().makeInteractionPairs(f);
        for (InteractionPair ip : interactionPairs) {
          sb.append(sep);
          String v1 = f._names[ip.getV1()];
          String v2 = f._names[ip.getV2()];
          if (stratifyBy.contains(v1))
            sb.append("strata(").append(v1).append(")");
          else
            sb.append(v1);
          sb.append(":");
          if (stratifyBy.contains(v2))
            sb.append("strata(").append(v2).append(")");
          else
            sb.append(v2);
          sep = " + ";
        }
      }
      if (_stratify_by != null) {
        final String tmp = sb.toString();
        for (String col : _stratify_by) {
          String strataCol = "strata(" + col + ")";
          if (! tmp.contains(strataCol)) {
            sb.append(sep).append(strataCol);
            sep = " + ";
          }
        }
      }
      return sb.toString();
    }
  }

  public static class CoxPHOutput extends Model.Output {

    public CoxPHOutput(CoxPH coxPH, Frame adaptFr, Frame train, IcedHashMap<AstGroup.G, IcedInt> strataMap) {
      super(coxPH, fullFrame(coxPH, adaptFr, train));
      _strataOnlyCols = new String[_names.length - adaptFr._names.length];
      for (int i = 0; i < _strataOnlyCols.length; i++)
        _strataOnlyCols[i] = _names[i];
      _ties = coxPH._parms._ties;
      _formula = coxPH._parms.toFormula(train);
      _interactionSpec = coxPH._parms.interactionSpec();
      _strataMap = strataMap;
    }

    private static Frame fullFrame(CoxPH coxPH, Frame adaptFr, Frame train) {
      if (! coxPH._parms.isStratified())
        return adaptFr;
      Frame ff = new Frame();
      for (String col : coxPH._parms._stratify_by)
        if (adaptFr.vec(col) == null)
          ff.add(col, train.vec(col));
      ff.add(adaptFr);
      return ff;
    }

    @Override
    public ModelCategory getModelCategory() { return ModelCategory.CoxPH; }

    @Override
    public InteractionBuilder interactionBuilder() {
      return _interactionSpec != null ? new CoxPHInteractionBuilder() : null;
    }

    private class CoxPHInteractionBuilder implements InteractionBuilder {
      @Override
      public Frame makeInteractions(Frame f) {
        Model.InteractionPair[] interactions = _interactionSpec.makeInteractionPairs(f);
        f.add(Model.makeInteractions(f, false, interactions, data_info._useAllFactorLevels, data_info._skipMissing, data_info._predictor_transform == DataInfo.TransformType.STANDARDIZE));
        return f;
      }
    }

    InteractionSpec _interactionSpec;
    DataInfo data_info;
    IcedHashMap<AstGroup.G, IcedInt> _strataMap;
    String[] _strataOnlyCols;

    public String[] _coef_names;
    public double[] _coef;
    public double[] _exp_coef;
    public double[] _exp_neg_coef;
    public double[] _se_coef;
    public double[] _z_coef;

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
    double[][] _x_mean_cat;
    double[][] _x_mean_num;
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
    FrameMatrix _var_cumhaz_2_matrix;
    Key<Frame> _var_cumhaz_2;

    CoxPHParameters.CoxPHTies _ties;
    String _formula;
  }

  public static class FrameMatrix extends Storage.DenseRowMatrix {
    Key<Frame> _frame_key;

    FrameMatrix(Key<Frame> frame_key, int rows, int cols) {
      super(rows, cols);
      _frame_key = frame_key;
    }

    @SuppressWarnings("unused")
    public final AutoBuffer write_impl(AutoBuffer ab) {
      Key.write_impl(_frame_key, ab);
      return ab;
    }

    @SuppressWarnings({"unused", "unchecked"})
    public final FrameMatrix read_impl(AutoBuffer ab) {
      _frame_key = (Key<Frame>) Key.read_impl(null, ab);
      // install in DKV if not already there
      if (DKV.getGet(_frame_key) == null)
        toFrame(_frame_key);
      return this;
    }

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
    int nResponses = 0;
    for (String col : _parms.responseCols())
      if (adaptFrm.find(col) != -1)
        nResponses++;
    DataInfo scoringInfo = _output.data_info.scoringInfo(_output._names, adaptFrm, nResponses, false);
    return new CoxPHScore(scoringInfo, _output, _parms.isStratified())
            .doAll(Vec.T_NUM, scoringInfo._adaptedFrame)
            .outputFrame(Key.<Frame>make(destination_key), new String[]{"lp"}, null);
  }

  @Override
  public String[] adaptTestForTrain(Frame test, boolean expensive, boolean computeMetrics) {
    boolean createStrataVec = _parms.isStratified() && (test.vec(_parms._strata_column) == null);
    if (createStrataVec) {
      Vec strataVec = test.anyVec().makeCon(Double.NaN);
      _toDelete.put(strataVec._key, "adapted missing strata vector");
      test.add(_parms._strata_column, strataVec);
    }
    String[] msgs = super.adaptTestForTrain(test, expensive, computeMetrics);
    if (createStrataVec) {
      Vec strataVec = CoxPH.StrataTask.makeStrataVec(test, _parms._stratify_by, _output._strataMap);
      _toDelete.put(strataVec._key, "adapted missing strata vector");
      test.replace(test.find(_parms._strata_column), strataVec);
      if (_output._strataOnlyCols != null)
        test.remove(_output._strataOnlyCols);
    }
    return msgs;
  }

  private static class CoxPHScore extends MRTask<CoxPHScore> {
    private DataInfo _dinfo;
    private double[] _coef;
    private double[] _lpBase;
    private int _numStart;
    private boolean _hasStrata;

    private CoxPHScore(DataInfo dinfo, CoxPHOutput o, boolean hasStrata) {
      final int strataCount = o._x_mean_cat.length;
      _dinfo = dinfo;
      _hasStrata = hasStrata;
      _coef = o._coef;
      _numStart = o._x_mean_cat[0].length;
      _lpBase = new double[strataCount];
      for (int s = 0; s < strataCount; s++) {
        for (int i = 0; i < o._x_mean_cat[s].length; i++)
          _lpBase[s] += o._x_mean_cat[s][i] * _coef[i];
        for (int i = 0; i < o._x_mean_num[s].length; i++)
          _lpBase[s] += o._x_mean_num[s][i] * _coef[i + _numStart];
      }
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
        double s = _hasStrata ? chks[_dinfo.responseChunkId(0)].atd(rid) : 0;
        if (Double.isNaN(s)) {
          // unknown strata
          nc.addNA();
          continue;
        }
        double lp = r.innerProduct(_coef) - _lpBase[(int) s];
        nc.addNum(lp);
      }
    }
  }

  @Override public double[] score0(double[] data, double[] preds) {
    throw new UnsupportedOperationException("CoxPHModel.score0 should never be called");
  }

  protected Futures remove_impl( Futures fs ) {
    Frame varCumhaz2 = _output._var_cumhaz_2 != null ? _output._var_cumhaz_2.get() : null;
    if (varCumhaz2 != null)
      varCumhaz2.remove(fs);
    super.remove_impl(fs);
    return fs;
  }

}

