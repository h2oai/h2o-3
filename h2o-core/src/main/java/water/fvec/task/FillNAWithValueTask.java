package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;

public class FillNAWithValueTask extends MRTask<FillNAWithValueTask> {

  private int columnIdx;
  private double valueToImpute;

  public FillNAWithValueTask(int columnIdx, double valueToImpute ) {
    this.columnIdx = columnIdx;
    this.valueToImpute = valueToImpute;
  }

  @Override
  public void map(Chunk cs[]) {
    Chunk num = cs[columnIdx];
    for (int i = 0; i < num._len; i++) {
      if (num.isNA(i)) {
        num.set(i, valueToImpute);
      }
    }
  }
}