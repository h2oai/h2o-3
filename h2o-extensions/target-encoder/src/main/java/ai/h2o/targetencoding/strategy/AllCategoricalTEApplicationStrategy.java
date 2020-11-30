package ai.h2o.targetencoding.strategy;

import water.fvec.Frame;

import java.util.Arrays;

public class AllCategoricalTEApplicationStrategy extends TEApplicationStrategy {
  
  private Frame _frame;
  private String[] _excludedColumnNames;

  /**
   *  Constructor for selection of categorical columns strategy 
   * @param frame the frame selection is being done from
   * @param excludedColumnNames the column names we want to exclude from the result 
   *                            ( i.e. response column for classification tasks , fold column in case it is categorical etc.)
   */
  public AllCategoricalTEApplicationStrategy(Frame frame, String[] excludedColumnNames) {
    _frame = frame;
    _excludedColumnNames = excludedColumnNames;
  }
  
  public String[] getColumnsToEncode() {
    return Arrays.stream(_frame.names())
        .filter(columnName ->
            _frame.vec(columnName).isCategorical() 
            && ! Arrays.asList(_excludedColumnNames).contains(columnName)
        )
        .toArray(String[]::new);
  }

}
