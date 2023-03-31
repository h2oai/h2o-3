package hex.tree.xgboost;

import hex.Model;
import hex.ModelBuilder;
import hex.ScoreKeeper;
import hex.tree.CalibrationHelper;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class XGBoostOutput extends Model.Output implements Model.GetNTrees, CalibrationHelper.OutputWithCalibration {
  public XGBoostOutput(XGBoost b) {
    super(b);
    _scored_train = new ScoreKeeper[]{new ScoreKeeper(Double.NaN)};
    _scored_valid = new ScoreKeeper[]{new ScoreKeeper(Double.NaN)};
  }

  int _nums;
  int _cats;
  int[] _catOffsets;
  boolean _useAllFactorLevels;
  boolean _useValidForScoreKeeping;
  public boolean _sparse;

  public int _ntrees;
  public ScoreKeeper[/*ntrees+1*/] _scored_train;
  public ScoreKeeper[/*ntrees+1*/] _scored_valid;
  public ScoreKeeper[] scoreKeepers() {
    List<ScoreKeeper> skl = new ArrayList<>();
    ScoreKeeper[] ska = trainedWithValidation() ? _scored_valid : _scored_train;
    for( ScoreKeeper sk : ska )
      if (!sk.isEmpty())
        skl.add(sk);
    return skl.toArray(new ScoreKeeper[0]);
  }
  private boolean trainedWithValidation() {
    return _validation_metrics != null || _useValidForScoreKeeping;
  }
  public long[/*ntrees+1*/] _training_time_ms = {System.currentTimeMillis()};
  public long _total_scoring_time_ms;
  public TwoDimTable _variable_importances; // gain
  @Override
  public TwoDimTable getVariableImportances() {
    return _variable_importances;
  }
  public TwoDimTable _variable_importances_cover;
  public TwoDimTable _variable_importances_frequency;
  public XgbVarImp _varimp;
  public TwoDimTable _native_parameters;

  public Model<?, ?, ?> _calib_model;

  @Override
  public TwoDimTable createInputFramesInformationTable(ModelBuilder modelBuilder) {
    XGBoostModel.XGBoostParameters params = (XGBoostModel.XGBoostParameters) modelBuilder._parms;
    TwoDimTable table = super.createInputFramesInformationTable(modelBuilder);
    table.set(2, 0, "calibration_frame");
    table.set(2, 1, params.getCalibrationFrame() != null ? params.getCalibrationFrame().checksum() : -1);
    table.set(2, 2, params.getCalibrationFrame() != null ? Arrays.toString(params.getCalibrationFrame().anyVec().espc()) : -1);
    return table;
  }

  @Override
  public int getInformationTableNumRows() {
    return super.getInformationTableNumRows() + 1;// +1 row for calibration frame
  }
  
  @Override
  public int getNTrees() {
    return _ntrees;
  }

  @Override
  public Model<?, ?, ?> calibrationModel() {
    return _calib_model;
  }

  @Override
  public void setCalibrationModel(Model<?, ?, ?> model) {
    _calib_model = model;
  }

}
