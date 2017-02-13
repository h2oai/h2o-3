package hex.tree;

import water.MRTask;
import water.fvec.ChunkAry;
import water.util.RandomUtils;

import java.util.Random;

// Deterministic sampling
public class Sample extends MRTask<Sample> {
  final DTree _tree;
  final double _rate;
  final double[] _rate_per_class;

  public Sample(DTree tree, double rate, double[] rate_per_class) {
    _tree = tree;
    _rate = rate;
    _rate_per_class = rate_per_class;
  }

  @Override
  public void map(ChunkAry chks) {
    // nids, ByteArraySupportedChunk ys
    int nids = 0, ys = 1;
    Random rand = RandomUtils.getRNG(_tree._seed);
    for (int row = 0; row < chks._len; row++) {
      boolean skip = chks.isNA(row,ys);
      if (!skip) {
        double rate = _rate_per_class==null ? _rate : _rate_per_class[chks.at4(row,ys)];
        rand.setSeed(_tree._seed + row + chks.start()); //seeding is independent of chunking
        skip = rand.nextFloat() >= rate; //float is good enough, half as much cost
      }
      if (skip) chks.set(row, nids, ScoreBuildHistogram.OUT_OF_BAG);     // Flag row as being ignored by sampling
    }
  }
}
