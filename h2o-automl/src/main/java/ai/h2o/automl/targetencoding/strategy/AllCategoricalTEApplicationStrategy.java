package ai.h2o.automl.targetencoding.strategy;

import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;

public class AllCategoricalTEApplicationStrategy extends TEApplicationStrategy {
  
  private Frame _frame;
  private String _responseColumnName;
  
  public AllCategoricalTEApplicationStrategy(Frame frame, String responseColumnName) {
    _frame = frame; 
    _responseColumnName = responseColumnName;
  }
  
  public String[] getColumnsToEncode() {
    ArrayList<String> categoricalColumnNames = new ArrayList<>();
    for( String vecName : _frame.names()) {
      if(_frame.vec(vecName).isCategorical() && !vecName.equals(_responseColumnName))
        categoricalColumnNames.add(vecName);
    }
    return categoricalColumnNames.toArray(new String[categoricalColumnNames.size()]);
  }

}
