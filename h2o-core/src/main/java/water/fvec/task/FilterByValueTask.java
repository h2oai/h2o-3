package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

public class FilterByValueTask extends MRTask<FilterByValueTask> {

  private double value;
  private boolean isInverted;

  public FilterByValueTask( double value, boolean isInverted ) {
    this.value = value;
    this.isInverted = isInverted;
  }

  @Override
  public void map(Chunk cs[], NewChunk ncs[]) {
    for (int col = 0; col < cs.length; col++) {
      Chunk c = cs[col];
      NewChunk nc = ncs[col];
      for (int i = 0; i < c._len; i++) {
          double currentValue = c.atd(i);
          if(isInverted)
            nc.addNum(value == currentValue ? 0 : 1);
          else
            nc.addNum(value == currentValue ? 1 : 0);
      }
    }
  }
}