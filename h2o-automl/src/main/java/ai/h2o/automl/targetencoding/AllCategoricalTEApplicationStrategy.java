package ai.h2o.automl.targetencoding;

import water.fvec.Frame;

import java.util.ArrayList;

public class AllCategoricalTEApplicationStrategy implements TEApplicationStrategy {
  
  private Frame _frame;
  
  public AllCategoricalTEApplicationStrategy(Frame frame) {
    this._frame = frame; 
  }
  
  
  public String[] getColumnsToEncode() {
    ArrayList<String> categoricalColumnNames = new ArrayList<>();
    for( String vecName : _frame.names()) {
      if(_frame.vec(vecName).isCategorical())
        categoricalColumnNames.add(vecName);
    }
    return categoricalColumnNames.toArray(new String[categoricalColumnNames.size()]);
  }

}
