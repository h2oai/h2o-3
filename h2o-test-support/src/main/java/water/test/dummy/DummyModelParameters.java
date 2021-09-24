package water.test.dummy;

import hex.Model;
import water.Key;

public class DummyModelParameters extends Model.Parameters {
  public DummyAction _action;
  public boolean _makeModel;
  public boolean _cancel_job;
  public String _column_param;
  public String[] _column_list_param;
  public String _dummy_string_param;
  public String[] _dummy_string_array_param;
  public DummyAction _on_exception_action;
  public DummyModelParameters() {}
  public DummyModelParameters(String msg, Key trgt) {
    _action = new MessageInstallAction(trgt, msg);
  }
  @Override public String fullName() { return algoName(); }
  @Override public String algoName() { return "dummymodelbuilder"; }
  @Override public String javaName() { return DummyModelBuilder.class.getName(); }
  @Override public long progressUnits() { return 1; }

  // Used for HyperSpaceWalkerTest - XGBoost's combination of parameters ended up making an infinite loop due to hash collision
  public int _max_depth;
  public double _min_rows;
  public double _sample_rate;
  public double _col_sample_rate;
  public double _col_sample_rate_per_tree;
  public String _booster;
  public float _reg_lambda;
  public float _reg_alpha;
  public float _scale_pos_weight;
  public float _max_delta_step;
}