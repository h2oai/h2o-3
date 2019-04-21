package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

public class FilterByValueTask extends MRTask<FilterByValueTask> {

  private double _value;
  private boolean _isInverted;

  public FilterByValueTask( double value, boolean isInverted ) {
    _value = value;
    _isInverted = isInverted;
  }

  @Override
  public void map(Chunk cs[], NewChunk ncs[]) {
    for (int col = 0; col < cs.length; col++) {
      Chunk c = cs[col];
      NewChunk nc = ncs[col];
      for (int i = 0; i < c._len; i++) {
          double currentValue = c.atd(i);
          if(_isInverted)
            nc.addNum(_value == currentValue ? 0 : 1);
          else
            nc.addNum(_value == currentValue ? 1 : 0);
      }
    }
  }
}