package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;

public class FillNAWithDoubleValueTask extends MRTask<FillNAWithDoubleValueTask> {

  private int _columnIdx;
  private double _valueToImpute;

  public FillNAWithDoubleValueTask(int columnIdx, double valueToImpute ) {
    _columnIdx = columnIdx;
    _valueToImpute = valueToImpute;
  }

  @Override
  public void map(Chunk cs[]) {
    Chunk num = cs[_columnIdx];
    for (int i = 0; i < num._len; i++) {
      if (num.isNA(i)) {
        num.set(i, _valueToImpute);
      }
    }
  }
}