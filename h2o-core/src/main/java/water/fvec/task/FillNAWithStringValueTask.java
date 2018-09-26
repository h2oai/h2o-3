package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;


public class FillNAWithStringValueTask extends MRTask<FillNAWithStringValueTask> {

  private int columnIdx;
  private int indexForNACategory;

  public FillNAWithStringValueTask(int columnIdx, int indexForNACategory) {
    this.columnIdx = columnIdx;
    this.indexForNACategory = indexForNACategory;
  }

  @Override
  public void map(Chunk cs[]) {
    Chunk num = cs[columnIdx];
    for (int i = 0; i < num._len; i++) {
      if (num.isNA(i)) {
        num.set(i, indexForNACategory);
      }
    }
  }
}