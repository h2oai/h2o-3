package hex.tree;

import water.MRTask;
import water.fvec.Chunk;

import java.util.Random;

/**
 * Created by arno on 9/18/15.
 */ // Deterministic sampling
public class Sample extends MRTask<Sample> {
  final DTree _tree;
  final float _rate;

  public Sample(DTree tree, float rate) {
    _tree = tree;
    _rate = rate;
  }

  @Override
  public void map(Chunk nids, Chunk ys) {
    Random rand = _tree.rngForChunk(nids.cidx());
    for (int row = 0; row < nids._len; row++)
      if (rand.nextFloat() >= _rate || Double.isNaN(ys.atd(row))) {
        nids.set(row, ScoreBuildHistogram.OUT_OF_BAG);     // Flag row as being ignored by sampling
      }
  }
}
