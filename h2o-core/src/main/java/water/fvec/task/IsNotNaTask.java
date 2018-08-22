package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

public class IsNotNaTask extends MRTask<IsNotNaTask> {
  @Override
  public void map(Chunk cs[], NewChunk ncs[]) {
    for (int col = 0; col < cs.length; col++) {
      Chunk c = cs[col];
      NewChunk nc = ncs[col];
      for (int i = 0; i < c._len; i++)
        nc.addNum(c.isNA(i) ? 0 : 1);
    }
  }
}