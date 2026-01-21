package hex.hglm;

import hex.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.H2O;
import water.Job;
import water.Key;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.glm.GLMModel.GLMParameters.MissingValuesHandling.*;
import static hex.hglm.HGLMModel.HGLMParameters.Method.EM;
import static hex.hglm.HGLMUtils.*;
import static hex.hglm.MetricBuilderHGLM.calHGLMLlg;
import static water.util.ArrayUtils.*;

public class HGLM extends ModelBuilder<HGLMModel, HGLMModel.HGLMParameters, HGLMModel.HGLMModelOutput> {
  /***
   * the doc = document attached to https://github.com/h2oai/h2o-3/issues/8487, title HGLM_H2O_Implementation.pdf
   * I will be referring to the doc and different parts of it to explain my implementation.
   */
  long _startTime;  // model building start time;
  private transient ComputationStateHGLM _state;
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ModelCategory.Regression};
  }

  @Override
  public boolean isSupervised() {
    return true;
  }

  @Override
  public BuilderVisibility builderVisibility() {
    return BuilderVisibility.Experimental;
  }

  @Override
  public boolean havePojo() {
    return false;
  }

  @Override
  public boolean haveMojo() {
    return false;
  }

  public HGLM(boolean startup_once) {
    super(new HGLMModel.HGLMParameters(), startup_once);
  }

  protected HGLM(HGLMModel.HGLMParameters parms) {
    super(parms);
    init(false);
  }

  public HGLM(HGLMModel.HGLMParameters parms, Key<HGLMModel> key) {
    super(parms, key);
    init(false);
  }

  @Override
  protected ModelBuilder<HGLMModel, HGLMModel.HGLMParameters, HGLMModel.HGLMModelOutput>.Driver trainModelImpl() {
    return new HGLMDriver();
  }
  
  static class ScoringHistory {
    private ArrayList<Integer> _scoringIters = new ArrayList<>();
    private ArrayList<Long> _scoringTimes = new ArrayList<>();
    private ArrayList<Double> _logLikelihood = new ArrayList<>();
    private ArrayList<Double> _tauEVar = new ArrayList<>();

    public ArrayList<Integer> getScoringIters() { return _scoringIters;}
    
    public void addIterationScore(int iter, double loglikelihood, double tauEVar) {
      _scoringIters.add(iter);
      _scoringTimes.add(System.currentTimeMillis());
      _logLikelihood.add(loglikelihood);
      _tauEVar.add(tauEVar);
    }
    
    public TwoDimTable to2dTable() {
      String[] cnames = new String[]{"timestamp", "number_of_iterations", "loglikelihood", "noise_variance"};
      String[] ctypes = new String[]{"string", "int", "double", "double"};
      String[] cformats = new String[]{"%s", "%d", "%.5f", "%.5f"};
      int tableSize = _scoringIters.size();
      TwoDimTable res = new TwoDimTable("Scoring History", "", 
              new String[tableSize], cnames, ctypes, cformats, "");
      int col = 0;
      for (int i=0; i<tableSize; i++) {
        res.set(i, col++, DATE_TIME_FORMATTER.print(_scoringTimes.get(i)));
        res.set(i, col++, _scoringIters.get(i));
        res.set(i, col++, _logLikelihood.get(i));
        res.set(i, col, _tauEVar.get(i));
        col = 0;
      }
      return res;
    }
  }

  @Override
  public void init(boolean expensive) {
    if (_parms._nfolds > 0 || _parms._fold_column != null)
      error("nfolds or _fold_coumn", " cross validation is not supported in HGLM right now.");

    if (null != _parms._family && !gaussian.equals(_parms._family))
      error("family", " only Gaussian families are supported now");

    if (null != _parms._method && !EM.equals(_parms._method))
      error("method", " only EM (expectation maximization) is supported for now.");

    if (null != _parms._missing_values_handling &&
            PlugValues == _parms._missing_values_handling && _parms._plug_values == null)
      error("PlugValues", " if specified, must provide a frame with plug values in plug_values.");

    if (_parms._tau_u_var_init < 0)
      error("tau_u_var_init", "if set, must > 0.0.");

    if (_parms._tau_e_var_init < 0)
      error("tau_e_var_init", "if set, must > 0.0.");

    if (_parms._seed == 0)
      error("seed", "cannot be set to any number except zero.");

    if (_parms._em_epsilon < 0)
      error("em_epsilon", "if specified, must >= 0.0.");
    
    if (_parms._score_iteration_interval <= 0)
      error("score_iteration_interval", "if specified must be >= 1.");
    
    super.init(expensive);
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(HGLM.this);
    if (expensive) {
      if (_parms._max_iterations == 0) {
        warn("max_iterations", "for HGLM, must be >= 1 (or -1 for unlimited or default setting) " +
                "to obtain proper model.  Setting it to be 0 will only return the correct coefficient names and an empty" +
                " model.");
        warn("_max_iterations", H2O.technote(2, "for HGLM, if specified, must be >= 1 or == -1."));
      }

      if (_parms._max_iterations == -1)
        _parms._max_iterations = 1000;

      Frame trainFrame = train();
      List<String> columnNames = Arrays.stream(trainFrame.names()).collect(Collectors.toList());
      if (_parms._group_column == null) {
        error("group_column", " column used to generate level 2 units is missing");
      } else {
        if (!columnNames.contains(_parms._group_column))
          error("group_column", " is not found in the training frame.");
        else if (!trainFrame.vec(_parms._group_column).isCategorical())
          error("group_column", " should be a categorical column.");
      }

      if (_parms._random_columns == null && !_parms._random_intercept) {
        error("random_columns", " should not be null if random_intercept is false.  You must " +
                "specify predictors in random_columns or set random_intercept to true.");
      }
      if (_parms._random_columns != null) {
        boolean goodRandomColumns = (Arrays.stream(_parms._random_columns).filter(x -> columnNames.contains(x)).count()
                == _parms._random_columns.length);
        if (!goodRandomColumns)
          error("random_columns", " can only contain columns in the training frame.");
      }
      
      if (_parms._gen_syn_data) {
        _parms._max_iterations = 0;
        if (_parms._tau_e_var_init <= 0) 
          error("tau_e_var_init", "If gen_syn_data is true, tau_e_var_init must be > 0.");
      }
    }
  }

  private class HGLMDriver extends Driver {
    DataInfo _dinfo = null;

    @Override
    public void computeImpl() {
      _startTime = System.currentTimeMillis();
      init(true);
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(HGLM.this);

      _job.update(0, "Initializing HGLM model training");
      HGLMModel model = null;
      ScoringHistory scTrain = new ScoringHistory();
      ScoringHistory scValid = _parms._valid == null ? null : new ScoringHistory();
      try {
        /***
         * Need to do the following things:
         * 1. Generate all the various coefficient names;
         * 2. Initialize the coefficient values (fixed and random)
         * 3. Set modelOutput fields.
         */
        // _dinfo._adaptedFrame will contain group_column.  Check and make sure clients will pass that along as well.
        _dinfo = new DataInfo(_train.clone(), null, 1, _parms._use_all_factor_levels, 
                DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                _parms.missingValuesHandling() == Skip,
                _parms.missingValuesHandling() == MeanImputation
                        || _parms.missingValuesHandling() == PlugValues,
                _parms.makeImputer(), false, hasWeightCol(), hasOffsetCol(), hasFoldCol(), null);

        model = new HGLMModel(dest(), _parms, new HGLMModel.HGLMModelOutput(HGLM.this, _dinfo));
        model.write_lock(_job);
        _job.update(1, "Starting to build HGLM model...");
        if (EM == _parms._method)
          fitEM(model, _job, scTrain, scValid);
        model._output.setModelOutputFields(_state); // must be called before calling scoring
        scoreAndUpdateModel(model, true, scTrain);
        model._output._model_summary = generateSummary(model._output);
        model._output._start_time = _startTime;
        model._output._training_time_ms = System.currentTimeMillis() - _startTime;
        model._output._scoring_history = scTrain.to2dTable();
        if (valid() != null) {
          scoreAndUpdateModel(model, false, scValid);
          if (scValid._scoringIters.size() > 0)
            model._output._scoring_history_valid = scValid.to2dTable();
        }
      } finally {
        model.update(_job);
        model.unlock(_job);
      }
    }
    
    private TwoDimTable generateSummary(HGLMModel.HGLMModelOutput modelOutput) {
      String[] names = new String[]{"number_of_iterations", "loglikelihood", "noise_variance"};
      String[] types = new String[]{"int", "double", "double"};
      String[] formats = new String[]{"%d", "%.5f", "%.5f"};
      TwoDimTable summary = new TwoDimTable("HGLM Model", "summary", new String[]{""}, names, types, formats, "");
      summary.set(0, 0, modelOutput._iterations);
      summary.set(0, 1, modelOutput._log_likelihood);
      summary.set(0, 2, modelOutput._tau_e_var);
      return summary;
    }
    
    private long timeSinceLastScoring(long startTime) { return System.currentTimeMillis() - startTime; }
    
    private void scoreAndUpdateModel(HGLMModel model, boolean forTraining, ScoringHistory sc) {
      Log.info("Scoring after " + timeSinceLastScoring(_startTime) + "ms at iteration "+model._output._iterations);
      long tcurrent = System.currentTimeMillis();
      if (forTraining) {
        model.score(_parms.train(), null, CFuncRef.from(_parms._custom_metric_func)).delete();
        ModelMetricsRegressionHGLM mtrain = (ModelMetricsRegressionHGLM) ModelMetrics.getFromDKV(model, _parms.train());
        model._output._training_metrics = mtrain;
        model._output._training_time_ms = tcurrent - model._output._start_time;
        if (null != mtrain) {
          model._output._log_likelihood = mtrain._log_likelihood;
          model._output._icc = mtrain._icc.clone();
          sc.addIterationScore(_state._iter, model._output._log_likelihood, mtrain._var_residual);
        }
      } else {
        Log.info("Scoring on validation dataset.");
        model.score(_parms.valid(), null, CFuncRef.from(_parms._custom_metric_func)).delete();
        ModelMetricsRegressionHGLM mvalid = (ModelMetricsRegressionHGLM) ModelMetrics.getFromDKV(model, _parms.valid());
        if (null != mvalid) {
          model._output._validation_metrics = mvalid;
          model._output._log_likelihood_valid = ((ModelMetricsRegressionHGLM) model._output._validation_metrics).llg();
          sc.addIterationScore(_state._iter, model._output._log_likelihood_valid, model._output._tau_e_var);
        }
      }
    }

    /**
     * Build HGLM model using EM (Expectation Maximization) described in section II of the doc.
     */
    void fitEM(HGLMModel model, Job job, ScoringHistory scTrain, ScoringHistory scValid) {
      int iteration = 0;
      // form fixed arrays and matrices whose values do not change
      HGLMTask.ComputationEngineTask engineTask = new HGLMTask.ComputationEngineTask(job, _parms, _dinfo);
      engineTask.doAll(_dinfo._adaptedFrame);
      model._output.setModelOutput(engineTask);
      if (_parms._showFixedMatVecs)
        model._output.setModelOutputFixMatVec(engineTask);
      _state = new ComputationStateHGLM(_job, _parms, _dinfo, engineTask, iteration);
      try {
        if (_parms._max_iterations > 0) {
          // grab current value of fixed beta, tauEVar, tauUVar
          double[] beta = _state.getBeta().clone();
          double[][] ubeta;
          double tauEVarE10 = _state.getTauEVarE10();
          double[][] tMat = copy2DArray(_state.getT());
          double[][][] cjInv;
          double[][] tMatInv;

          while (true) {
            iteration++;
            // E step: estimate the random beta (random effect coefficient, need to grab Cj (inverse)
            tMatInv = generateTInverse(tMat);
            cjInv = generateCJInverse(engineTask._ArjTArj, tauEVarE10, tMatInv); // for each level 2 value
            ubeta = estimateNewRandomEffects(cjInv, engineTask._ArjTYj, engineTask._ArjTAfj, beta);// new random coefficients
            // M step
            beta = estimateFixedCoeff(engineTask._AfTAftInv, engineTask._AfjTYjSum, engineTask._AfjTArj, ubeta);// new fixed coeficients
            tMat = estimateNewtMat(ubeta, tauEVarE10, cjInv, engineTask._oneOverJ);  // provide better estimate of tauEVar
            HGLMTask.ResidualLLHTask rLlhE10 = new HGLMTask.ResidualLLHTask(_job, _parms, _dinfo, ubeta, beta, engineTask);
            rLlhE10.doAll(_dinfo._adaptedFrame);
            tauEVarE10 = rLlhE10._residualSquare * engineTask._oneOverN; // from equation 10 of the doc
             // check to make sure determinant of V is positive, see section II.V of the doc
            if (!checkPositiveG(engineTask._numLevel2Units, tMat))
              Log.info("HGLM model building is stopped due to matrix G in section II.V of the doc is no longer PSD");
            // check if stopping conditions are satisfied
            if (!progress(beta, ubeta, tMat, tauEVarE10, scTrain, scValid, model, rLlhE10))
              return;
          }
        }
      } catch(Exception ex) { // will catch matrix singular during loglikelihood calculation
        if (iteration > 1)  // some coefficients are valid, just return
          return;
        else
          throw new RuntimeException(ex); // bad matrix from the start, no model is built.
      }
    }

    public boolean progress(double[] beta, double[][] ubeta, double[][] tmat, double tauEVarE10, ScoringHistory scTrain,
                            ScoringHistory scValid, HGLMModel model, HGLMTask.ResidualLLHTask rLlh) {
      _state._iter++;
      if (_state._iter >= _parms._max_iterations || stop_requested())
        return false;
      double[] betaDiff = new double[beta.length];
      minus(betaDiff, beta, _state.getBeta());
      double maxBetaDiff = maxMag(betaDiff) / maxMag(beta);
      double[][] tmatDiff = new double[tmat.length][tmat[0].length];
      minus(tmatDiff, tmat, _state.getT());
      double maxTmatDiff = maxMag(tmatDiff) / maxMag(tmat);
      double[][] ubetaDiff = new double[ubeta.length][ubeta[0].length];
      minus(ubetaDiff, ubeta, _state.getUbeta());
      double maxUBetaDiff = maxMag(ubetaDiff) / maxMag(ubeta);
      double tauEVarDiff = Math.abs(tauEVarE10 - _state.getTauEVarE10()) / tauEVarE10;
      boolean converged = ((maxBetaDiff <= _parms._em_epsilon) && (maxTmatDiff <= _parms._em_epsilon) && (maxUBetaDiff
              <= _parms._em_epsilon) && (tauEVarDiff <= _parms._em_epsilon));
      if (!converged) { // update values in _state
        _state.setBeta(beta);
        _state.setUbeta(ubeta);
        _state.setT(tmat);
        _state.setTauEVarE10(tauEVarE10);
        if (_parms._score_each_iteration || ((_parms._score_iteration_interval % _state._iter) == 0)) {
          model._output.setModelOutputFields(_state);
          scoreAndUpdateModel(model, true, scTrain); // perform scoring and updating scoring history
          if (_parms.valid() != null)
            scoreAndUpdateModel(model, false, scValid);
        } else {
          // calculate log likelihood with current parameter settings
          double logLikelihood = calHGLMLlg(_state._nobs, tmat, tauEVarE10, model._output._arjtarj, rLlh._sse_fixed,
                  rLlh._yMinusXTimesZ);
          scTrain.addIterationScore(_state._iter, logLikelihood, tauEVarE10);
        }
      }
      return !converged;
    }
  }
}
