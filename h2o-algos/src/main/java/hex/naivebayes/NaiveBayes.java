package hex.naivebayes;

import hex.DataInfo;
import hex.Model;
import hex.SupervisedModelBuilder;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.NaiveBayesV2;
import water.*;
import water.fvec.Chunk;
import water.util.ArrayUtils;
import water.util.Log;

/**
 * Naive Bayes
 * This is an algorithm for computing the conditional a-posterior probabilities of a categorical
 * response from independent predictors using Bayes rule.
 * <a href = "http://en.wikipedia.org/wiki/Naive_Bayes_classifier">Naive Bayes on Wikipedia</a>
 * <a href = "http://cs229.stanford.edu/notes/cs229-notes2.pdf">Lecture Notes by Andrew Ng</a>
 * @author anqi_fu
 *
 */
public class NaiveBayes extends SupervisedModelBuilder<NaiveBayesModel,NaiveBayesModel.NaiveBayesParameters,NaiveBayesModel.NaiveBayesOutput> {
  @Override
  public ModelBuilderSchema schema() {
    return new NaiveBayesV2();
  }

  @Override
  public Job<NaiveBayesModel> trainModel() {
    return start(new NaiveBayesDriver(), 0);
  }

  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{ Model.ModelCategory.Unknown };
  }

  // Called from an http request
  public NaiveBayes(NaiveBayesModel.NaiveBayesParameters parms) {
    super("NaiveBayes", parms);
    init(false);
  }

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
    if (_response != null && !_response.isEnum()) error("_response", "Response must be a categorical column");
    if (_parms._laplace < 0) error("_laplace", "Laplace smoothing must be an integer >= 0.");
    if (_parms._min_sdev <= 1e-10) error("_min_sdev", "Min. standard deviation must be at least 1e-10.");
  }

  class NaiveBayesDriver extends H2O.H2OCountedCompleter<NaiveBayesDriver> {

    public void computeStatsFillModel(NaiveBayesModel model, DataInfo dinfo, NBTask tsk) {
      double[] pprior = tsk._rescnt.clone();
      double[][][] pcond = tsk._jntcnt.clone();
      String[][] domains = dinfo._adaptedFrame.domains();

      // A-priori probability of response y
      for(int i = 0; i < pprior.length; i++)
        pprior[i] = (pprior[i] + _parms._laplace)/(tsk._nobs + tsk._nres * _parms._laplace);
      // pprior[i] = pprior[i]/tsk._nobs;     // Note: R doesn't apply laplace smoothing to priors, even though this is textbook definition

      // Probability of categorical predictor x_j conditional on response y
      for(int col = 0; col < dinfo._cats; col++) {
        assert pcond[col].length == tsk._nres;
        for(int i = 0; i < pcond[col].length; i++) {
          for(int j = 0; j < pcond[col][i].length; j++)
            pcond[col][i][j] = (pcond[col][i][j] + _parms._laplace)/(tsk._rescnt[i] + domains[col].length * _parms._laplace);
        }
      }

      // Mean and standard deviation of numeric predictor x_j for every level of response y
      for(int col = 0; col < dinfo._nums; col++) {
        for(int i = 0; i < pcond[0].length; i++) {
          int cidx = dinfo._cats + col;
          double num = tsk._rescnt[i];
          double pmean = pcond[cidx][i][0]/num;

          pcond[cidx][i][0] = pmean;
          // double pvar = pcond[cidx][i][1]/num - pmean * pmean;
          double pvar = pcond[cidx][i][1]/(num - 1) - pmean * pmean * num/(num - 1);
          pcond[cidx][i][1] = Math.sqrt(pvar);
        }
      }

      model._output._pprior = pprior;
      model._output._pcond = pcond;
    }

    @Override
    protected void compute2() {
      NaiveBayesModel model = null;
      DataInfo dinfo = null;

      try {
        _parms.read_lock_frames(NaiveBayes.this); // Fetch & read-lock input frames
        init(true);
        if (error_count() > 0) throw new IllegalArgumentException("Found validation errors: " + validationErrors());

        // The model to be built
        model = new NaiveBayesModel(dest(), _parms, new NaiveBayesModel.NaiveBayesOutput(NaiveBayes.this));
        model.delete_and_lock(_key);
        _train.read_lock(_key);

        dinfo = new DataInfo(Key.make(), _train, null, 1, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
        NBTask tsk = new NBTask(dinfo).doAll(dinfo._adaptedFrame);
        computeStatsFillModel(model, dinfo, tsk);

        model.update(_key);
        done();
      } catch (Throwable t) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        _train.unlock(_key);
        if (model != null) model.unlock(_key);
        if (dinfo != null) dinfo.remove();
        _parms.read_unlock_frames(NaiveBayes.this);
      }
      tryComplete();
    }

    Key self() {
      return _key;
    }
  }

  // Note: NA handling differs from R for efficiency purposes
  // R's method: For each predictor x_j, skip counting that row for p(x_j|y) calculation if x_j = NA. If response y = NA, skip counting row entirely in all calculations
  // H2O's method: Just skip all rows where any x_j = NA or y = NA. Should be more memory-efficient, but results incomparable with R.
  private static class NBTask extends MRTask<NBTask> {
    final protected DataInfo _dinfo;
    final int _nres;              // Number of levels for the response y

    public int _nobs;             // Number of rows counted in calculation
    public double[] _rescnt;      // Count of each level in the response
    public double[][][] _jntcnt;  // For each categorical predictor, joint count of response and predictor levels
    // For each numeric predictor, sum of entries for every response level

    public NBTask(DataInfo dinfo) {
      _dinfo = dinfo;
      _nobs = 0;

      String[][] domains = dinfo._adaptedFrame.domains();
      int ncol = dinfo._adaptedFrame.numCols();
      assert ncol-1 == dinfo._nums + dinfo._cats;   // ncol-1 because we drop response col
      _nres = domains[ncol-1].length;

      _rescnt = new double[_nres];
      _jntcnt = new double[ncol-1][][];
      for(int i = 0; i < _jntcnt.length; i++) {
        int ncnt = domains[i] == null ? 2 : domains[i].length;
        _jntcnt[i] = new double[_nres][ncnt];
      }
    }

    @Override public void map(Chunk[] chks) {
      int res_idx = chks.length - 1;
      Chunk res = chks[res_idx];

      OUTER:
      for(int row = 0; row < chks[0]._len; row++) {
        // Skip row if any entries in it are NA
        for(int col = 0; col < chks.length; col++) {
          if(Double.isNaN(chks[col].atd(row))) continue OUTER;
        }

        // Record joint counts of categorical predictors and response
        int rlevel = (int)res.atd(row);
        for(int col = 0; col < _dinfo._cats; col++) {
          int plevel = (int)chks[col].atd(row);
          _jntcnt[col][rlevel][plevel]++;
        }

        // Record sum for each pair of numerical predictors and response
        for(int col = 0; col < _dinfo._nums; col++) {
          int cidx = _dinfo._cats + col;
          double x = chks[cidx].atd(row);
          _jntcnt[cidx][rlevel][0] += x;
          _jntcnt[cidx][rlevel][1] += x*x;
        }
        _rescnt[rlevel]++;
        _nobs++;
      }
    }

    @Override public void reduce(NBTask nt) {
      _nobs += nt._nobs;
      ArrayUtils.add(_rescnt, nt._rescnt);
      for(int col = 0; col < _jntcnt.length; col++)
        _jntcnt[col] = ArrayUtils.add(_jntcnt[col], nt._jntcnt[col]);
    }
  }
}
