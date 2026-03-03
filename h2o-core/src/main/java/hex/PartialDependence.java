package hex;

import jsr166y.CountedCompleter;
import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.util.FrameUtils.CalculateWeightMeanSTD;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.ArrayList;


public class PartialDependence extends Lockable<PartialDependence> {
  transient final public Job _job;
  public Key<Model> _model_id;
  public Key<Frame> _frame_id;
  public long _row_index = -1; // row index, -1 implies no specific row to use in PDP calculation
  public String[] _cols;
  public ArrayList<String> _cols_1d_2d;  // include all columns specified for 1D pdp and 2D pdp
  public int _weight_column_index = -1;  // weight column index, -1 implies no weight
  public boolean _add_missing_na = false; // set to be false for default
  public int _nbins = 20;
  public String[] _targets;
  public TwoDimTable[] _partial_dependence_data; //OUTPUT
  public double[] _user_splits = null;    // store all user splits for all column
  public double[][] _user_split_per_col = null; // point to correct location of user splits per column
  public int[] _num_user_splits = null;   // record number of user split values per column
  public String[] _user_cols = null;      // contains columns with user defined splits
  public boolean _user_splits_present = false;
  public String[][] _col_pairs_2dpdp = null;
  public int _num_2D_pairs = 0; // number of 2D pdp pairs to work on
  public int _num_1D = 0;
  public int _predictor_column; // predictor column to use in calculating partial dependence
  public int[] _predictor_columns;

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
    _job.start(new PartialDependenceDriver(), _num_1D+_num_2D_pairs);
    return _job;
  }
  
  private int findTargetClassPredictorIndex(Model m, String target){
    int index = Arrays.asList(m._output.classNames()).indexOf(target);
    if (index == -1) {
      throw new IllegalArgumentException("Incorrect target class: " + target + ".");
    }
    return index+1;
  }

  private int[] findTargetClassPredictorIndices(Model m, String[] targets){
    int[] result = new int[targets.length];
    for(int i=0; i < targets.length; i++){
      result[i] = findTargetClassPredictorIndex(m, targets[i]);
    }
    return result;
  }
  
  private void checkSanityAndFillParams() {
    Model m = _model_id.get();
    if (m == null) {
      throw new IllegalArgumentException("Model not found.");
    }
    if (!m._output.isSupervised()) {
      throw new IllegalArgumentException("Partial dependence plots are only implemented for supervised models");
    }
    int nclasses = m._output.nclasses();
    if(nclasses <= 2 && _targets != null){
      throw new IllegalArgumentException("Targets parameter is available only for multinomial classification.");
    }
    if(nclasses == 1){
      _predictor_column = 0;
      _predictor_columns = new int[]{_predictor_column};
    } else if(nclasses == 2) {
      _predictor_column = 2;
      _predictor_columns = new int[]{_predictor_column};
    } else {
      if (_targets == null) {
        throw new IllegalArgumentException("Targets parameter has to be set for multinomial classification.");
      } else {
        _predictor_columns = findTargetClassPredictorIndices(m, _targets);
      }
    }
    
    if (_cols != null || _col_pairs_2dpdp != null) {
      _cols_1d_2d = new ArrayList<>();
      if (_cols != null)
        _cols_1d_2d.addAll(Arrays.asList(_cols));
      if (_col_pairs_2dpdp != null) {
        _num_2D_pairs = _col_pairs_2dpdp.length * _predictor_columns.length;
        for (int index=0; index < _num_2D_pairs; index++) {
          if (!(_cols_1d_2d.contains(_col_pairs_2dpdp[index][0]))) 
            _cols_1d_2d.add(_col_pairs_2dpdp[index][0]);
          if (!(_cols_1d_2d.contains(_col_pairs_2dpdp[index][1])))
            _cols_1d_2d.add(_col_pairs_2dpdp[index][1]);
        }
      }
    } else
      _cols_1d_2d = null; // all columns are null for some reason
    
    if (_cols_1d_2d==null) {  // no cols or cols pairs are specified
      Frame f = _frame_id.get();
      if (f==null) throw new IllegalArgumentException("Frame not found.");

      if (Model.GetMostImportantFeatures.class.isAssignableFrom(m.getClass())) {
        _cols = ((Model.GetMostImportantFeatures) m).getMostImportantFeatures(10);
        if (_cols != null) {
          Log.info("Selecting the top " + _cols.length + " features from the model's variable importances.");
        }
      } else {
        _cols = m._output._names;
        if (_cols != null) {
          Log.info("Selecting all features from the training data.");
        }
      }
      _cols_1d_2d = new ArrayList<>();
      _cols_1d_2d.addAll(Arrays.asList(_cols));
    }
    _num_1D = _cols==null ? 0 : _cols.length * _predictor_columns.length;
    if (_nbins < 2) {
      throw new IllegalArgumentException("_nbins must be >=2.");
    }
    if ((_user_splits != null) && (_user_splits.length > 0)) {
      _user_splits_present = true;
      int numUserSplits = _user_cols.length;
      // convert one dimension info into two dimension
      _user_split_per_col = new double[numUserSplits][];
      int[] user_splits_start = new int[numUserSplits];
      for (int cindex = 1; cindex < numUserSplits; cindex++) {  // fixed bug in user_splits_start
        user_splits_start[cindex] = _num_user_splits[cindex-1]+user_splits_start[cindex-1];
      }
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

    if (! _user_splits_present) {
      for (String col : _cols_1d_2d) {
        Vec v = fr.vec(col);
        if (v.isCategorical() && v.cardinality() > _nbins) {
          throw new IllegalArgumentException("Column " + col + "'s cardinality of " + v.cardinality() + " > nbins of " + _nbins);
        }
      }
    }
  }

  double[] extractColValues(String col, int actualbins, Vec v) {
    double[] colVals;
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
      colVals = _add_missing_na ? new double[actualbins+1] : new double[actualbins];
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
    return colVals;
  }
  
  private class PartialDependenceDriver extends H2O.H2OCountedCompleter<PartialDependenceDriver> {
    public void compute2() {
      assert (_job != null);
      final Frame fr = _frame_id.get();
      // loop over PDPs (columns)
      int num_cols_1d_2d = _num_1D+_num_2D_pairs;
      _partial_dependence_data = new TwoDimTable[num_cols_1d_2d];
      
      int column = 0;
      for (int i = 0; i < num_cols_1d_2d; ++i) {  // take care of the 1d pdp first, then 2d pdp
        boolean workingOn1D = (i < _num_1D);
        final String col = workingOn1D ? _cols[column] : _col_pairs_2dpdp[column - _num_1D][0];
        final String col2 = workingOn1D ? null : _col_pairs_2dpdp[column - _num_1D][1];
        final int whichPredictorColumn = i % _predictor_columns.length;
        Log.debug("Computing partial dependence of model on '" + col + "'"+(_targets == null ? "." : " and class "+_targets[whichPredictorColumn]+"."));
        double[] colVals = extractColValues(col, _nbins, fr.vec(col));
        double[] col2Vals = workingOn1D ? null : extractColValues(col2, _nbins, fr.vec(col2));

        Futures fs = new Futures();
        int responseLength = workingOn1D ? colVals.length : colVals.length * col2Vals.length;
        final double[] meanResponse = new double[responseLength];
        final double[] stddevResponse = new double[responseLength];
        final double[] stdErrorOfTheMeanResponse = new double[responseLength];

        final boolean cat = fr.vec(col).isCategorical();
        final boolean cat2 = workingOn1D ? false : fr.vec(col2).isCategorical();
        
        // loop over column values (fill one PartialDependence)
        if (workingOn1D) {  // 1d pdp 
          for (int k = 0; k < colVals.length; ++k) {
            final double value = colVals[k];
            CalculatePdpPerBin pdp = new CalculatePdpPerBin(col, col2, value, -1, cat, cat2, k,
                      false, meanResponse, stddevResponse, stdErrorOfTheMeanResponse, _predictor_columns[whichPredictorColumn]);  // perform actual pdp calculation
            fs.add(H2O.submitTask(pdp));
          }
        } else {            // 2d pdp
          int colLen1 = colVals.length;
          int colLen2 = col2Vals.length;
          int totLen = colLen1*colLen2;
          
          for (int k=0; k < totLen; k++) {
            int index1 = k / colLen2;
            int index2 = k % colLen2;
            final double value = colVals[index1];
            final double value2 = col2Vals[index2];
            CalculatePdpPerBin pdp = new CalculatePdpPerBin(col, col2, value, value2, cat, cat2, k, true,
                    meanResponse, stddevResponse,stdErrorOfTheMeanResponse, _predictor_columns[whichPredictorColumn]);  // perform actual pdp calculation
            fs.add(H2O.submitTask(pdp));
          }
        }
        fs.blockForPending();

        if (workingOn1D) {
          _partial_dependence_data[i] = new TwoDimTable("PartialDependence",
                  _row_index < 0 ? ("Partial Dependence Plot of model " + _model_id + " on column '" + col + "'" + (_targets == null ? "." : " and class "+ _targets[whichPredictorColumn])) :
                          ("Partial Dependence Plot of model " + _model_id + " on column '" + col + "'" + (_targets == null ? "'" : " and class "+_targets[whichPredictorColumn]) +" for row index" + _row_index),
                  new String[colVals.length],
                  new String[]{col, "mean_response", "stddev_response", "std_error_mean_response"},
                  new String[]{cat ? "string" : "double", "double", "double", "double"},
                  new String[]{cat ? "%s" : "%5f", "%5f", "%5f", "%5f"}, null);
        } else {
          _partial_dependence_data[i] = new TwoDimTable("2D-PartialDependence",
                  _row_index < 0 ? ("2D Partial Dependence Plot of model " + _model_id + " on 1st column '" +
                          col + "' and 2nd column '" + col2 +"'") :
                          ("Partial Dependence Plot of model " + _model_id + " on columns '" + 
                                  col + "', '"+ col2
                                  +"' for row " + _row_index),
                  new String[colVals.length*col2Vals.length],
                  new String[]{col, col2, "mean_response", 
                          "stddev_response", "std_error_mean_response"},
                  new String[]{cat ? "string" : "double", cat2 ? "string":"double", "double", "double", "double"},
                  new String[]{cat ? "%s" : "%5f", cat2 ? "%s" : "%5f","%5f", "%5f", "%5f"}, null);
        }
        for (int j = 0; j < meanResponse.length; ++j) {
          int colIndex = 0;
          int countval1 = workingOn1D? j : j / col2Vals.length;
          if (fr.vec(col).isCategorical()) {
            if (_add_missing_na && Double.isNaN(colVals[countval1]))
              _partial_dependence_data[i].set(j, colIndex, ".missing(NA)"); // accomodate NA
            else
              _partial_dependence_data[i].set(j, colIndex, fr.vec(col).domain()[(int) colVals[countval1]]);
          } else {
            _partial_dependence_data[i].set(j, colIndex, colVals[countval1]);
          }
          colIndex++;
          
          if (!workingOn1D) {
            int countval2 = j%col2Vals.length;
            if (fr.vec(col2).isCategorical()) {
              if (_add_missing_na && Double.isNaN(col2Vals[countval2]))
                _partial_dependence_data[i].set(j, colIndex, ".missing(NA)"); // accomodate NA
              else
                _partial_dependence_data[i].set(j, colIndex, fr.vec(col2).domain()[(int) col2Vals[countval2]]);
            } else {
              _partial_dependence_data[i].set(j, colIndex, col2Vals[countval2]);
            }
            colIndex++;
          }
          _partial_dependence_data[i].set(j, colIndex++, meanResponse[j]);
          _partial_dependence_data[i].set(j, colIndex++, stddevResponse[j]);
          _partial_dependence_data[i].set(j, colIndex++, stdErrorOfTheMeanResponse[j]);
        }
        if(_targets == null){
          column++;
        } else if((i+1) % _targets.length == 0){
          column++;
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
    
    private class CalculatePdpPerBin extends H2O.H2OCountedCompleter<CalculatePdpPerBin> {
      final String _col; // column name 
      final String _col2; // column name for 2nd column for 2d pdp
      final double _value;  // value of column to keep constant
      final double _value2; // value of 2nd column to keep constant for 2d pdp
      final boolean _workOn2D;  // true for 2d pdp, false for 1d pdp
      final int _pdp_row_index; // column index into pdp frame
      final boolean _col1_cat;  // true if first column is enum
      final boolean _col2_cat;  // true if second column is enum
      final double[] _meanResponse;
      final double[] _stddevResponse;
      final double[] _stdErrorOfTheMeanResponse;
      final int _predictorColumn; 

      CalculatePdpPerBin(String col, String col2, double value, double value2, boolean cat, boolean cat2, int which,
                         boolean workon2D, double[] meanResp, double[] stddevResp, double[] stdErrMeanResp, int predictorColumn) {
        _col = col;
        _col2 = col2;
        _value = value;
        _value2 = value2;
        _workOn2D = workon2D;
        _pdp_row_index = which;
        _col1_cat = cat;
        _col2_cat = cat2;
        _meanResponse = meanResp;
        _stddevResponse = stddevResp;
        _stdErrorOfTheMeanResponse = stdErrMeanResp;
        _predictorColumn = predictorColumn;
      }
      
      public void compute2() {
        Frame fr;
        if (_row_index >= 0) {
          fr = Rapids.exec("(rows " + _frame_id + "  " + _row_index + ")").getFrame();
        } else {
          fr = _frame_id.get();
        }
        Frame test = new Frame(fr);
        Vec orig = test.remove(_col);
        Vec cons = orig.makeCon(_value);
        if (_col1_cat) cons.setDomain(fr.vec(_col).domain());
        test.add(_col, cons);

        Vec cons2 = null;
        if (_workOn2D) {
          Vec orig2 = test.remove(_col2);
          cons2 = orig2.makeCon(_value2);
          if (_col2_cat) cons2.setDomain(fr.vec(_col2).domain());
          test.add(_col2, cons2);
        }
        Frame preds = null;
        try {
          preds = _model_id.get().score(test, Key.make().toString(), _job, false);
          if (preds == null || preds.numRows() == 0) {  // this can happen if algo will not predict on rows with NAs
            _meanResponse[_pdp_row_index] = Double.NaN;
            _stddevResponse[_pdp_row_index] = Double.NaN;
            _stdErrorOfTheMeanResponse[_pdp_row_index] = Double.NaN;
          } else {
            CalculateWeightMeanSTD calMeansSTD = (_weight_column_index >= 0)?
                    getWeightedStat(fr, preds, _predictorColumn):null;
            _meanResponse[_pdp_row_index] = (_weight_column_index >= 0)?calMeansSTD.getWeightedMean()
                    :preds.vec(_predictorColumn).mean();
            _stddevResponse[_pdp_row_index] = (_weight_column_index >= 0)?calMeansSTD.getWeightedSigma()
                    :preds.vec(_predictorColumn).sigma();
            _stdErrorOfTheMeanResponse[_pdp_row_index] = _stddevResponse[_pdp_row_index]/Math.sqrt(preds.numRows());
          }
        } finally {
          if (preds != null) preds.remove();
        }
        cons.remove();
        if (cons2!=null) cons2.remove();
        if (_row_index >= 0) {
          fr.remove();
        }
        tryComplete();
      }
    }
  }

  @Override public Class<KeyV3.PartialDependenceKeyV3> makeSchema() { return KeyV3.PartialDependenceKeyV3.class; }

}

