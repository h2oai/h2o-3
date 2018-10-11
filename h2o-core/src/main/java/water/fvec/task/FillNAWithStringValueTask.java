package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;


public class FillNAWithStringValueTask extends MRTask<FillNAWithStringValueTask> {

  private int _columnIdx;
  private int _indexForNACategory;

  public FillNAWithStringValueTask(int columnIdx, int indexForNACategory) {
    _columnIdx = columnIdx;
    _indexForNACategory = indexForNACategory;
  }

  @Override
  public void map(Chunk cs[]) {
    Chunk num = cs[_columnIdx];
    for (int i = 0; i < num._len; i++) {
      if (num.isNA(i)) {
        num.set(i, _indexForNACategory);
      }
    }
  }
}