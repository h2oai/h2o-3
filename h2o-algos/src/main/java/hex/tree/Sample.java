package hex.tree;

import water.MRTask;
import water.fvec.C4VolatileChunk;
import water.fvec.Chunk;
import water.util.RandomUtils;

import java.util.Random;

// Deterministic sampling
public class Sample extends MRTask<Sample> {
  final long _seed;
  final int _useMarker;
  final int _ignoreMarker;
  final double _rate;
  final double[] _rate_per_class;

  public Sample(DTree tree, double rate, double[] rate_per_class) {
    this(tree._seed, rate, rate_per_class, 0, ScoreBuildHistogram.OUT_OF_BAG);
  }

  public Sample(long seed, double rate, double[] rate_per_class, int useMarker,int ignoreMarker) {
    _seed = seed;
    _useMarker = useMarker;
    _ignoreMarker = ignoreMarker;
    _rate = rate;
    _rate_per_class = rate_per_class;
  }

  @Override
  public void map(Chunk nids, Chunk ys) {
    C4VolatileChunk nids2 = (C4VolatileChunk) nids;
    Random rand = RandomUtils.getRNG(_seed);
    int [] is = nids2.getValues();
    for (int row = 0; row < nids._len; row++) {
      boolean skip = ys.isNA(row);
      if (!skip) {
        double rate = _rate_per_class==null ? _rate : _rate_per_class[(int)ys.at8(row)];
        rand.setSeed(_seed + row + nids.start()); //seeding is independent of chunking
        skip = rand.nextFloat() >= rate; //float is good enough, half as much cost
      }
      if (skip) is[row] = _ignoreMarker;     // Flag row as being ignored by sampling
      else if (_useMarker != 0) is[row] = _useMarker;
    }
  }
}
