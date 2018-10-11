package water.fvec.task;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.Arrays;

public class AsQuasiBinomialTask extends MRTask<AsQuasiBinomialTask> {

  // Categorical binary column that is actually set of two strings ( e.g. "NO" and "YES") is represented with CBSChunk in runtime.
  // The problem is that for the same domains we can get different values in chunks depending on what domain's value we met first in our vector.
  private boolean _neededToBeReversed;

  public AsQuasiBinomialTask( String[] domain ) {
    String[] sortedDomain = domain.clone();
    Arrays.sort(sortedDomain);
    _neededToBeReversed = ! sortedDomain[0].equals(domain[0])  ;
  }
  @Override
  public void map(Chunk cs[], NewChunk ncs[]) {
    for (int col = 0; col < cs.length; col++) {
      Chunk c = cs[col];
      NewChunk nc = ncs[col];
      for (int i = 0; i < c._len; i++) {
        if (c.isNA(i)) nc.addNA();
        else {
          if(_neededToBeReversed) nc.addNum((c.at8(i) == 0) ? 1 : 0);
          else nc.addNum((c.at8(i) == 0) ? 0 : 1);
        }
      }
    }
  }
}