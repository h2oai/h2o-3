package ai.h2o.automl.targetencoding.strategy;

import water.fvec.Frame;

import java.util.ArrayList;

public class AllCategoricalTEApplicationStrategy implements TEApplicationStrategy {
  
  private Frame _frame;
  private String _responseColumn;
  
  public AllCategoricalTEApplicationStrategy(Frame frame, String responseColumn) {
    this._frame = frame; 
    this._responseColumn = responseColumn; 
  }
  
  
  public String[] getColumnsToEncode() {
    ArrayList<String> categoricalColumnNames = new ArrayList<>();
    for( String vecName : _frame.names()) {
      if(_frame.vec(vecName).isCategorical() && !vecName.equals(_responseColumn))
        categoricalColumnNames.add(vecName);
    }
    return categoricalColumnNames.toArray(new String[categoricalColumnNames.size()]);
  }

}
