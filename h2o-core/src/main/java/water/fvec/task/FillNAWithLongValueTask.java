package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;


public class FillNAWithLongValueTask extends MRTask<FillNAWithLongValueTask> {

  private int _columnIdx;
  private long _intValue;
  public boolean _imputationHappened;

  public FillNAWithLongValueTask(int columnIdx, long intValue) {
    _columnIdx = columnIdx;
    _intValue = intValue;
    _imputationHappened = false;
  }

  @Override
  public void map(Chunk cs[]) {
    Chunk num = cs[_columnIdx];
    for (int i = 0; i < num._len; i++) {
      if (num.isNA(i)) {
        num.set(i, _intValue);
        _imputationHappened = true;
      }
    }
  }

  @Override
  public void reduce(FillNAWithLongValueTask mrt) {
    _imputationHappened = _imputationHappened || mrt._imputationHappened;
  }
}
