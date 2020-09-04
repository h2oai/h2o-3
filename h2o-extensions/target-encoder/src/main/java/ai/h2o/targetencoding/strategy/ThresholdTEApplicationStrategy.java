package ai.h2o.targetencoding.strategy;

import water.fvec.Frame;
import java.util.Arrays;

/**
 * Strategy that will select only categorical columns with cardinality of greater of equal than specified threshold
 */
public class ThresholdTEApplicationStrategy extends TEApplicationStrategy {
  
  private Frame _frame;
  private String[] _excludedColumnNames;
  private long _threshold;

  /**
   *  Constructor for selection of categorical columns strategy based on threshold value
   * @param frame the frame selection is being done from
   * @param excludedColumnNames the column names we want to exclude from the result 
   *                            ( i.e. response column for classification tasks , fold column in case it is categorical etc.)
   * @param threshold categorical columns with higher cardinality than {@code threshold} value will be selected
   */
  public ThresholdTEApplicationStrategy(Frame frame, long threshold, String[] excludedColumnNames) {
    _frame = frame;
    _excludedColumnNames = excludedColumnNames; 
    _threshold = threshold;
  }
  
  public String[] getColumnsToEncode() {
    return Arrays.stream(_frame.names())
            .filter(columnName ->
                    _frame.vec(columnName).isCategorical() 
                    && ! Arrays.asList(_excludedColumnNames).contains(columnName) 
                    && _frame.vec(columnName).cardinality() >= _threshold
            )
            .toArray(String[]::new);
  }

}
