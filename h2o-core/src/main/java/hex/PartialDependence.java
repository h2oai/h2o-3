package hex;

import jsr166y.CountedCompleter;
import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils.CalculateWeightMeanSTD;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.Arrays;


public class PartialDependence extends Lockable<PartialDependence> {
  transient final public Job _job;
  public Key<Model> _model_id;
  public Key<Frame> _frame_id;
  public String[] _cols;
  public int _weight_column_index =-1;  // weight column index, -1 implies no weight
  public boolean _add_missing_na = false; // set to be false for default
  public int _nbins = 20;
  public TwoDimTable[] _partial_dependence_data; //OUTPUT
  public double[] _user_splits = null;    // store all user splits for all column
  public double[][] _user_split_per_col = null; // point to correct location of user splits per column
  public int[] _num_user_splits = null;   // record number of user split values per column
  public String[] _user_cols = null;
  public boolean _user_splits_present = false;

  public PartialDependence(Key<PartialDependence> dest, Job j) {
    super(dest);
    _job = j;
  }

  public PartialDependence(Key<PartialDependence> dest) {
    this(dest, new Job<>(dest, PartialDependence.class.getName(), "PartialDependence"));
  }

  public PartialDependence execNested() {
    checkSanityAndFillParams();
    this.delete_and_lock(_job);
    _frame_id.get().write_lock(_job._key);
    new PartialDependenceDriver().compute2();
    return this;
  }

  public Job<PartialDependence> execImpl() {
    checkSanityAndFillParams();
    delete_and_lock(_job);
    _frame_id.get().write_lock(_job._key);
    // Don't lock the model since the act of unlocking at the end would
    // freshen the DKV version, but the live POJO must survive all the way
    // to be able to delete the model metrics that got added to it.
    // Note: All threads doing the scoring call model_id.get() and then
    // update the _model_metrics only on the temporary live object, not in DKV.
    // At the end, we call model.remove() and we need those model metrics to be
    // deleted with it, so we must make sure we keep the live POJO alive.
    _job.start(new PartialDependenceDriver(), _cols.length);
    return _job;
  }

  private void checkSanityAndFillParams() {
    if (_cols==null) {
      Model m = _model_id.get();
      if (m==null) throw new IllegalArgumentException("Model not found.");
      if (!m._output.isSupervised() || m._output.nclasses() > 2)
        throw new IllegalArgumentException("Partial dependence plots are only implemented for regression and binomial classification models");
      Frame f = _frame_id.get();
      if (f==null) throw new IllegalArgumentException("Frame not found.");

      if (Model.GetMostImportantFeatures.class.isAssignableFrom(m.getClass())) {
        _cols = ((Model.GetMostImportantFeatures)m).getMostImportantFeatures(10);
        if (_cols != null) {
          Log.info("Selecting the top " + _cols.length + " features from the model's variable importances");
        }
      }
    }
    if (_nbins < 2) {
      throw new IllegalArgumentException("_nbins must be >=2.");
    }

    if ((_user_splits != null) && (_user_splits.length > 0)) {
      _user_splits_present = true;
      int numUserSplits = _user_cols.length;
      // convert one dimension info into two dimensionl
      _user_split_per_col = new double[numUserSplits][];
      int[] user_splits_start = new int[numUserSplits];
      System.arraycopy(_num_user_splits, 0, user_splits_start, 1, numUserSplits-1);
      for (int cindex=0; cindex < numUserSplits; cindex++) {
        int splitNum = _num_user_splits[cindex];
        _user_split_per_col[cindex] = new double[splitNum];
        System.arraycopy(_user_splits, user_splits_start[cindex], _user_split_per_col[cindex], 0, splitNum);
      }
    }

    final Frame fr = _frame_id.get();

    if (_weight_column_index >= 0) { // grab and make weight column as a separate frame
      if (!fr.vec(_weight_column_index).isNumeric() || fr.vec(_weight_column_index).isCategorical())
        throw new IllegalArgumentException("Weight column " + _weight_column_index + " must be a numerical column.");
    }

    for (int i = 0; i < _cols.length; ++i) {
      final String col = _cols[i];
      Vec v = fr.vec(col);
      if (v.isCategorical() && v.cardinality() > _nbins) {
        throw new IllegalArgumentException("Column " + col + "'s cardinality of " + v.cardinality() + " > nbins of " + _nbins);
      }
    }
  }

  private class PartialDependenceDriver extends H2O.H2OCountedCompleter<PartialDependenceDriver> {
    public void compute2() {
      assert (_job != null);
      final Frame fr = _frame_id.get();
      // loop over PDPs (columns)
      _partial_dependence_data = new TwoDimTable[_cols.length];
      for (int i = 0; i < _cols.length; ++i) {
        final String col = _cols[i];
        Log.debug("Computing partial dependence of model on '" + col + "'.");
        Vec v = fr.vec(col);
        int actualbins = _nbins;

        double[] colVals;
        // use user defined value here if requested by user
        if (_user_splits_present && Arrays.asList(_user_cols).contains(col)) {
          int user_col_index = Arrays.asList(_user_cols).indexOf(col);
          actualbins = _num_user_splits[user_col_index];
          colVals = _add_missing_na?new double[_num_user_splits[user_col_index]+1]:new double[_num_user_splits[user_col_index]];
          for (int rindex = 0; rindex < _num_user_splits[user_col_index]; rindex++) {
            colVals[rindex] = _user_split_per_col[user_col_index][rindex];
          }
        } else {
          if (v.isInt() && (v.max() - v.min() + 1) < _nbins) {
            actualbins = (int) (v.max() - v.min() + 1);
          }
          colVals = _add_missing_na?new double[actualbins+1]:new double[actualbins];
          double delta = (v.max() - v.min()) / (actualbins - 1);
          if (actualbins == 1) delta = 0;
          for (int j = 0; j < colVals.length; ++j) {
            colVals[j] = v.min() + j * delta;
          }
        }

        if (_add_missing_na)
          colVals[actualbins] = Double.NaN; // set last bin to contain nan

        Log.debug("Computing PartialDependence for column " + col + " at the following values: ");
        Log.debug(Arrays.toString(colVals));

        Futures fs = new Futures();
        final double meanResponse[] = new double[colVals.length];
        final double stddevResponse[] = new double[colVals.length];
        final double stdErrorOfTheMeanResponse[] = new double[colVals.length];

        final boolean cat = fr.vec(col).isCategorical();
        // loop over column values (fill one PartialDependence)
        for (int k = 0; k < colVals.length; ++k) {
          final double value = colVals[k];
          final int which = k;
          H2O.H2OCountedCompleter pdp = new H2O.H2OCountedCompleter() {
            @Override
            public void compute2() {
              Frame fr = _frame_id.get();
              Frame test = new Frame(fr.names(), fr.vecs());
              Vec orig = test.remove(col);
              Vec cons = orig.makeCon(value);
              if (cat) cons.setDomain(fr.vec(col).domain());
              test.add(col, cons);
              Frame preds = null;
              try {
                preds = _model_id.get().score(test, Key.make().toString(), _job, false);
                if (preds == null || preds.numRows() == 0) {  // this can happen if algo will not predict on rows with NAs
                  meanResponse[which] = Double.NaN;
                  stddevResponse[which] = Double.NaN;;
                  stdErrorOfTheMeanResponse[which] = Double.NaN;;
                } else {
                  if (_model_id.get()._output.nclasses() == 2) {
                    if (_weight_column_index >= 0) { // calculated weighted statistics
                      CalculateWeightMeanSTD calMeansSTD = getWeightedStat(fr, preds,  2);
                      meanResponse[which] = calMeansSTD.getWeightedMean();;
                      stddevResponse[which] = calMeansSTD.getWeightedSigma();
                      stdErrorOfTheMeanResponse[which] = stddevResponse[which] / Math.sqrt(preds.numRows());
                    } else {
                      meanResponse[which] = preds.vec(2).mean();
                      stddevResponse[which] = preds.vec(2).sigma();
                      stdErrorOfTheMeanResponse[which] = stddevResponse[which] / Math.sqrt(preds.numRows());
                    }
                  } else if (_model_id.get()._output.nclasses() == 1) {
                    if (_weight_column_index >= 0) {
                      CalculateWeightMeanSTD calMeansSTD = getWeightedStat(fr, preds, 0);
                      meanResponse[which] = calMeansSTD.getWeightedMean();;
                      stddevResponse[which] = calMeansSTD.getWeightedSigma();
                      stdErrorOfTheMeanResponse[which] = stddevResponse[which] / Math.sqrt(preds.numRows());
                    } else {
                      meanResponse[which] = preds.vec(0).mean();
                      stddevResponse[which] = preds.vec(0).sigma();
                      stdErrorOfTheMeanResponse[which] = stddevResponse[which] / Math.sqrt(preds.numRows());
                    }
                  } else throw H2O.unimpl();
                }
              } finally {
                if (preds != null) preds.remove();
              }
              cons.remove();
              tryComplete();
            }
          };
          fs.add(H2O.submitTask(pdp));
        }
        fs.blockForPending();

        _partial_dependence_data[i] = new TwoDimTable("PartialDependence",
                ("Partial Dependence Plot of model " + _model_id + " on column '" + _cols[i] + "'"),
                new String[colVals.length],
                new String[]{_cols[i], "mean_response", "stddev_response", "std_error_mean_response"},
                new String[]{cat ? "string" : "double", "double", "double", "double"},
                new String[]{cat ? "%s" : "%5f", "%5f", "%5f", "%5f"}, null);
        for (int j = 0; j < meanResponse.length; ++j) {
          if (fr.vec(col).isCategorical()) {
            if (_add_missing_na && Double.isNaN(colVals[j]))
              _partial_dependence_data[i].set(j, 0, ".missing(NA)"); // accomodate NA
            else
              _partial_dependence_data[i].set(j, 0, fr.vec(col).domain()[(int) colVals[j]]);
          } else {
            _partial_dependence_data[i].set(j, 0, colVals[j]);
          }
          _partial_dependence_data[i].set(j, 1, meanResponse[j]);
          _partial_dependence_data[i].set(j, 2, stddevResponse[j]);
          _partial_dependence_data[i].set(j, 3, stdErrorOfTheMeanResponse[j]);
        }
        _job.update(1);
        update(_job);
        if (_job.stop_requested())
          break;
      }
      tryComplete();
    }

    public CalculateWeightMeanSTD getWeightedStat(Frame dataFrame, Frame pred, int targetIndex) {
      CalculateWeightMeanSTD calMeansSTD = new CalculateWeightMeanSTD();
      calMeansSTD.doAll(pred.vec(targetIndex), dataFrame.vec(_weight_column_index));

      return calMeansSTD;
    }

    @Override
    public void onCompletion(CountedCompleter caller) {
      _frame_id.get().unlock(_job._key);
      unlock(_job);
    }

    @Override
    public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
      _frame_id.get().unlock(_job._key);
      unlock(_job);
      return true;
    }
  }

  @Override public Class<KeyV3.PartialDependenceKeyV3> makeSchema() { return KeyV3.PartialDependenceKeyV3.class; }

}

