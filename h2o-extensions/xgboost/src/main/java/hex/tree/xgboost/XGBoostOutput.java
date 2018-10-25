package hex.tree.xgboost;

import hex.Model;
import hex.ScoreKeeper;
import hex.VarImp;
import water.util.TwoDimTable;

import java.util.ArrayList;

public class XGBoostOutput extends Model.Output {
  public XGBoostOutput(XGBoost b) {
    super(b);
    _scored_train = new ScoreKeeper[]{new ScoreKeeper(Double.NaN)};
    _scored_valid = new ScoreKeeper[]{new ScoreKeeper(Double.NaN)};
  }

  int _nums;
  int _cats;
  int[] _catOffsets;
  boolean _useAllFactorLevels;
  public boolean _sparse;

  public int _ntrees;
  public ScoreKeeper[/*ntrees+1*/] _scored_train;
  public ScoreKeeper[/*ntrees+1*/] _scored_valid;
  public ScoreKeeper[] scoreKeepers() {
    ArrayList<ScoreKeeper> skl = new ArrayList<>();
    ScoreKeeper[] ska = _validation_metrics != null ? _scored_valid : _scored_train;
    for( ScoreKeeper sk : ska )
      if (!sk.isEmpty())
        skl.add(sk);
    return skl.toArray(new ScoreKeeper[skl.size()]);
  }
  public long[/*ntrees+1*/] _training_time_ms = {System.currentTimeMillis()};
  public TwoDimTable _variable_importances;
  public VarImp _varimp;
  public TwoDimTable _native_parameters;
}
