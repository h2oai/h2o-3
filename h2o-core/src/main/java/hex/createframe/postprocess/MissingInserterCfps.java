package hex.createframe.postprocess;

import hex.createframe.CreateFramePostprocessStep;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.RandomUtils;

import java.util.Random;


/**
 * This action randomly injects missing values into the dataframe.
 */
public class MissingInserterCfps extends CreateFramePostprocessStep {
  private double p;


  public MissingInserterCfps() {}

  /**
   * @param p Fraction of values to be converted into NAs.
   */
  public MissingInserterCfps(double p) {
    assert p >= 0 && p < 1 : "p should be in the range [0, 1), got " + p;
    this.p = p;
  }

  /** Execute this post-processing step. */
  @Override
  public void exec(Frame fr, Random rng) {
    // No need to do anything if p == 0
    if (p > 0)
      new InsertNAs(p, rng).doAll(fr);
  }

  /**
   * Task that does the actual job of imputing missing values.
   *
   * Typically the fraction p of values to be replaced with missings is fairly small, therefore it is inefficient
   * to visit each value individually and decide whether to flip it to NA based on comparing a uniform random number
   * against p. Instead we rely on the fact that the distribution of gaps between "successes" in a bernoulli experiment
   * follows the Geometric(p) distribution. Drawing from such distribution is also easy: if u is uniform on (0,1) then
   * <code>floor(log(u)/log(1-p))</code> is geometric with parameter p.
   */
  private static class InsertNAs extends MRTask<InsertNAs> {
    private long seed;
    private double p;

    public InsertNAs(double prob, Random random) {
      p = prob;
      seed = random.nextLong();
    }

    @Override
    public void map(Chunk[] cs) {
      int numRows = cs[0]._len;
      long chunkStart = cs[0].start();
      double denom = Math.log(1 - p);
      Random rng = RandomUtils.getRNG(0);
      for (int i = 0; i < cs.length; i++) {
        rng.setSeed(seed + i * 35602489 + chunkStart * 47582);
        int l = 0;
        while (true) {
          l += (int) Math.floor(Math.log(rng.nextDouble()) / denom);
          if (l < numRows)
            cs[i].set(l++, Double.NaN);
          else
            break;
        }
      }
    }
  }

}
