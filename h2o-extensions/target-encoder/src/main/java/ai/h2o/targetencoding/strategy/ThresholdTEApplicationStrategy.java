package ai.h2o.targetencoding.strategy;

import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;

/**
 * Strategy that will select only categorical columns which cardinality is greater of equal than specified threshold
 */
public class ThresholdTEApplicationStrategy extends TEApplicationStrategy {
  
  private Frame _frame;
  private String _responseColumnName;
  private long _threshold;

  /**
   * 
   * @param frame
   * @param responseColumn
   * @param threshold
   */
  public ThresholdTEApplicationStrategy(Frame frame, Vec responseColumn, long threshold) {
    _frame = frame; 
    _responseColumnName = _frame.name(_frame.find(responseColumn)); // Question: can we add .name(Vec vec) to Frame's API?
    _threshold = threshold;
  }
  
  
  public String[] getColumnsToEncode() {
    ArrayList<String> categoricalColumnNames = new ArrayList<>();
    for( String vecName : _frame.names()) {
      Vec vec = _frame.vec(vecName);
      if(vec.isCategorical() && !vecName.equals(_responseColumnName) && vec.cardinality() >= _threshold)
        categoricalColumnNames.add(vecName);
    }
    return categoricalColumnNames.toArray(new String[categoricalColumnNames.size()]);
  }

}
