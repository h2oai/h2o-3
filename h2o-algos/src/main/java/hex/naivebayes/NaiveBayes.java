package hex.naivebayes;

import hex.*;
import hex.schemas.ModelBuilderSchema;
import hex.schemas.NaiveBayesV3;
import hex.naivebayes.NaiveBayesModel.NaiveBayesOutput;
import hex.naivebayes.NaiveBayesModel.NaiveBayesParameters;
import jsr166y.CountedCompleter;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.PrettyPrint;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Naive Bayes
 * This is an algorithm for computing the conditional a-posterior probabilities of a categorical
 * response from independent predictors using Bayes rule.
 * <a href = "http://en.wikipedia.org/wiki/Naive_Bayes_classifier">Naive Bayes on Wikipedia</a>
 * <a href = "http://cs229.stanford.edu/notes/cs229-notes2.pdf">Lecture Notes by Andrew Ng</a>
 * @author anqi_fu
 *
 */
public class NaiveBayes extends ModelBuilder<NaiveBayesModel,NaiveBayesParameters,NaiveBayesOutput> {
  @Override
  public ModelBuilderSchema schema() {
    return new NaiveBayesV3();
  }

  public boolean isSupervised(){return true;}

  @Override
  public Job<NaiveBayesModel> trainModel() {
    return start(new NaiveBayesDriver(), 6);
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ ModelCategory.Unknown };
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; };

  @Override
  protected void checkMemoryFootPrint() {
    // compute memory usage for pcond matrix
    long mem_usage = (_train.numCols() - 1) * _train.lastVec().cardinality();
    String[][] domains = _train.domains();
    long count = 0;
    for (int i = 0; i < _train.numCols() - 1; i++) {
      count += domains[i] == null ? 2 : domains[i].length;
    }
    mem_usage *= count;
    mem_usage *= 8; //doubles
    long max_mem = H2O.SELF.get_max_mem();
    if (mem_usage > max_mem) {
      String msg = "Conditional probabilities won't fit in the driver node's memory ("
              + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
              + ") - try reducing the number of columns, the number of response classes or the number of categorical factors of the predictors.";
      error("_train", msg);
      cancel(msg);
    }
  }

  // Called from an http request
  public NaiveBayes(NaiveBayesModel.NaiveBayesParameters parms) {
    super("NaiveBayes", parms);
    init(false);
  }

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
    if (_response != null) {
      if (!_response.isEnum()) error("_response", "Response must be a categorical column");
      else if (_response.isConst()) error("_response", "Response must have at least two unique categorical levels");
    }
    if (_parms._laplace < 0) error("_laplace", "Laplace smoothing must be an integer >= 0");
    if (_parms._min_sdev < 1e-10) error("_min_sdev", "Min. standard deviation must be at least 1e-10");
    if (_parms._eps_sdev < 0) error("_eps_sdev", "Threshold for standard deviation must be positive");
    if (_parms._min_prob < 1e-10) error("_min_prob", "Min. probability must be at least 1e-10");
    if (_parms._eps_prob < 0) error("_eps_prob", "Threshold for probability must be positive");
    hide("_balance_classes", "Balance classes is not applicable to NaiveBayes.");
    hide("_class_sampling_factors", "Class sampling factors is not applicable to NaiveBayes.");
    hide("_max_after_balance_size", "Max after balance size is not applicable to NaiveBayes.");
    if (expensive && error_count() == 0) checkMemoryFootPrint();
  }
  private static boolean couldBeBool(Vec v) { return v != null && v.isInt() && v.min()+1==v.max(); }

  class NaiveBayesDriver extends H2O.H2OCountedCompleter<NaiveBayesDriver> {

    public boolean computeStatsFillModel(NaiveBayesModel model, DataInfo dinfo, NBTask tsk) {
      model._output._levels = _response.domain();
      model._output._rescnt = tsk._rescnt;
      model._output._ncats = dinfo._cats;

      if(!isRunning(_key)) return false;
      update(1, "Initializing arrays for model statistics");
      // String[][] domains = dinfo._adaptedFrame.domains();
      String[][] domains = model._output._domains;
      double[] apriori = new double[tsk._nrescat];
      double[][][] pcond = new double[tsk._npreds][][];
      for(int i = 0; i < pcond.length; i++) {
        int ncnt = domains[i] == null ? 2 : domains[i].length;
        pcond[i] = new double[tsk._nrescat][ncnt];
      }

      if(!isRunning(_key)) return false;
      update(1, "Computing probabilities for categorical cols");
      // A-priori probability of response y
      for(int i = 0; i < apriori.length; i++)
        apriori[i] = ((double)tsk._rescnt[i] + _parms._laplace)/(tsk._nobs + tsk._nrescat * _parms._laplace);
        // apriori[i] = tsk._rescnt[i]/tsk._nobs;     // Note: R doesn't apply laplace smoothing to priors, even though this is textbook definition

      // Probability of categorical predictor x_j conditional on response y
      for(int col = 0; col < dinfo._cats; col++) {
        assert pcond[col].length == tsk._nrescat;
        for(int i = 0; i < pcond[col].length; i++) {
          for(int j = 0; j < pcond[col][i].length; j++)
            pcond[col][i][j] = ((double)tsk._jntcnt[col][i][j] + _parms._laplace)/((double)tsk._rescnt[i] + domains[col].length * _parms._laplace);
        }
      }

      if(!isRunning(_key)) return false;
      update(1, "Computing mean and standard deviation for numeric cols");
      // Mean and standard deviation of numeric predictor x_j for every level of response y
      for(int col = 0; col < dinfo._nums; col++) {
        for(int i = 0; i < pcond[0].length; i++) {
          int cidx = dinfo._cats + col;
          double num = tsk._rescnt[i];
          double pmean = tsk._jntsum[col][i][0]/num;

          pcond[cidx][i][0] = pmean;
          // double pvar = tsk._jntsum[col][i][1]/num - pmean * pmean;
          double pvar = tsk._jntsum[col][i][1]/(num - 1) - pmean * pmean * num/(num - 1);
          pcond[cidx][i][1] = Math.sqrt(pvar);
        }
      }
      model._output._apriori_raw = apriori;
      model._output._pcond_raw = pcond;

      // Create table of conditional probabilities for every predictor
      model._output._pcond = new TwoDimTable[pcond.length];
      String[] rowNames = _response.domain();
      for(int col = 0; col < dinfo._cats; col++) {
        String[] colNames = _train.vec(col).domain();
        String[] colTypes = new String[colNames.length];
        String[] colFormats = new String[colNames.length];
        Arrays.fill(colTypes, "double");
        Arrays.fill(colFormats, "%5f");
        model._output._pcond[col] = new TwoDimTable(_train.name(col), null, rowNames, colNames, colTypes, colFormats,
                "Y_by_" + _train.name(col), new String[rowNames.length][], pcond[col]);
      }

      for(int col = 0; col < dinfo._nums; col++) {
        int cidx = dinfo._cats + col;
        model._output._pcond[cidx] = new TwoDimTable(_train.name(cidx), null, rowNames, new String[] {"Mean", "Std_Dev"},
                new String[] {"double", "double"}, new String[] {"%5f", "%5f"}, "Y_by_" + _train.name(cidx),
                new String[rowNames.length][], pcond[cidx]);
      }

      // Create table of a-priori probabilities for the response
      String[] colTypes = new String[_response.cardinality()];
      String[] colFormats = new String[_response.cardinality()];
      Arrays.fill(colTypes, "double");
      Arrays.fill(colFormats, "%5f");
      model._output._apriori = new TwoDimTable("A Priori Response Probabilities", null, new String[1], _response.domain(), colTypes, colFormats, "",
              new String[1][], new double[][] {apriori});
      model._output._model_summary = createModelSummaryTable(model._output);

      if(!isRunning(_key)) return false;
      update(1, "Scoring and computing metrics on training data");
      if (_parms._compute_metrics) {
        model.score(_parms.train()).delete(); // This scores on the training data and appends a ModelMetrics
        ModelMetricsSupervised mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        model._output._training_metrics = mm;
      }

      // At the end: validation scoring (no need to gather scoring history)
      if(!isRunning(_key)) return false;
      update(1, "Scoring and computing metrics on validation data");
      if (_valid != null) {
        Frame pred = model.score(_parms.valid()); //this appends a ModelMetrics on the validation set
        model._output._validation_metrics = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        pred.delete();
      }

      return true;
    }

    @Override protected void compute2() {
      NaiveBayesModel model = null;
      DataInfo dinfo = null;

      try {
        init(true);   // Initialize parameters
        _parms.read_lock_frames(NaiveBayes.this); // Fetch & read-lock input frames
        if (error_count() > 0) throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(NaiveBayes.this);
        dinfo = new DataInfo(Key.make(), _train, _valid, 1, false, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false);

        // The model to be built
        model = new NaiveBayesModel(dest(), _parms, new NaiveBayesOutput(NaiveBayes.this));
        model.delete_and_lock(_key);
        _train.read_lock(_key);

        update(1, "Begin distributed Naive Bayes calculation");
        NBTask tsk = new NBTask(_key, dinfo, _response.cardinality()).doAll(dinfo._adaptedFrame);
        if (computeStatsFillModel(model, dinfo, tsk))
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
  }

  private TwoDimTable createModelSummaryTable(NaiveBayesOutput output) {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();
    colHeaders.add("Number of Response Levels"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Min Apriori Probability"); colTypes.add("double"); colFormat.add("%.5f");
    colHeaders.add("Max Apriori Probability"); colTypes.add("double"); colFormat.add("%.5f");

    double apriori_min = output._apriori_raw[0];
    double apriori_max = output._apriori_raw[0];
    for(int i = 1; i < output._apriori_raw.length; i++) {
      if(output._apriori_raw[i] < apriori_min) apriori_min = output._apriori_raw[i];
      else if(output._apriori_raw[i] > apriori_max) apriori_max = output._apriori_raw[i];
    }

    final int rows = 1;
    TwoDimTable table = new TwoDimTable(
            "Model Summary", null,
            new String[rows],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    int row = 0;
    int col = 0;
    table.set(row, col++, output._apriori_raw.length);
    table.set(row, col++, apriori_min);
    table.set(row, col++, apriori_max);
    return table;
  }

  // Note: NA handling differs from R for efficiency purposes
  // R's method: For each predictor x_j, skip counting that row for p(x_j|y) calculation if x_j = NA.
  //             If response y = NA, skip counting row entirely in all calculations
  // H2O's method: Just skip all rows where any x_j = NA or y = NA. Should be more memory-efficient, but results incomparable with R.
  private static class NBTask extends MRTask<NBTask> {
    final protected Key _jobKey;
    final DataInfo _dinfo;
    final String[][] _domains;  // Domains of the training frame
    final int _nrescat;         // Number of levels for the response y
    final int _npreds;          // Number of predictors in the training frame

    public int _nobs;                     // Number of rows counted in calculation
    public int[/*nrescat*/] _rescnt;      // Count of each level in the response
    public int[/*npreds*/][/*nrescat*/][] _jntcnt;  // For each categorical predictor, joint count of response and predictor levels
    public double[/*npreds*/][/*nrescat*/][] _jntsum; // For each numeric predictor, sum and squared sum of entries for every response level

    public NBTask(Key jobKey, DataInfo dinfo, int nres) {
      _jobKey = jobKey;
      _dinfo = dinfo;
      _nrescat = nres;
      _domains = dinfo._adaptedFrame.domains();
      _npreds = dinfo._adaptedFrame.numCols()-1;
      assert _npreds == dinfo._nums + dinfo._cats;
      assert _nrescat == _domains[_npreds].length;       // Response in last vec of adapted frame
    }

    @Override public void map(Chunk[] chks) {
      if(_jobKey != null && !isRunning(_jobKey)) {
        throw new JobCancelledException();
      }
      _nobs = 0;
      _rescnt = new int[_nrescat];

      if(_dinfo._cats > 0) {
        _jntcnt = new int[_dinfo._cats][][];
        for (int i = 0; i < _dinfo._cats; i++) {
          _jntcnt[i] = new int[_nrescat][_domains[i].length];
        }
      }

      if(_dinfo._nums > 0) {
        _jntsum = new double[_dinfo._nums][][];
        for (int i = 0; i < _dinfo._nums; i++) {
          _jntsum[i] = new double[_nrescat][2];
        }
      }

      Chunk res = chks[_npreds];    // Response at the end
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
          _jntsum[col][rlevel][0] += x;
          _jntsum[col][rlevel][1] += x*x;
        }
        _rescnt[rlevel]++;
        _nobs++;
      }
    }

    @Override public void reduce(NBTask nt) {
      _nobs += nt._nobs;
      ArrayUtils.add(_rescnt, nt._rescnt);
      if(null != _jntcnt) {
        for (int col = 0; col < _jntcnt.length; col++)
          ArrayUtils.add(_jntcnt[col], nt._jntcnt[col]);
      }
      if(null != _jntsum) {
        for (int col = 0; col < _jntsum.length; col++)
          ArrayUtils.add(_jntsum[col], nt._jntsum[col]);
      }
    }
  }
}
