package hex.tree;

import water.MRTask;
import water.fvec.Chunk;
import water.util.RandomUtils;

import java.util.Random;

// Deterministic sampling
public class Sample extends MRTask<Sample> {
  final DTree _tree;
  final float _rate;

  public Sample(DTree tree, float rate) {
    _tree = tree;
    _rate = rate;
  }

  @Override
  public void map(Chunk nids, Chunk ys) {
    Random rand = RandomUtils.getRNG(_tree._seed);
    for (int row = 0; row < nids._len; row++) {
      rand.setSeed(_tree._seed + row + nids.start()); //seeding is independent of chunking
      if (rand.nextFloat() >= _rate || Double.isNaN(ys.atd(row))) {
        nids.set(row, ScoreBuildHistogram.OUT_OF_BAG);     // Flag row as being ignored by sampling
      }
    }
  }
}
