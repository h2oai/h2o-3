package water.util;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

import java.util.Random;

public class RNGTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  final static long[] seed = {7234723423423402343L, 1234882173459262304L};

  enum NumType { DOUBLE, FLOAT, LONG, INT }

  final static NumType[] types = new NumType[]{
          NumType.DOUBLE,
          NumType.FLOAT,
          NumType.LONG,
          NumType.INT
  };

  @Test public void JavaRandomBadSeed() {
    Assert.assertTrue(new Random(0).nextLong() == new Random(0).nextLong());
    for (NumType t : types)
      Assert.assertTrue("JavaRandomBadSeed " + t + " failed.",
              ChiSquareTest(new Random(0), t));
  }
  @Test public void JavaRandom() {
    Assert.assertTrue(new Random(seed[0]).nextLong() == new Random(seed[0]).nextLong());
    for (NumType t : types)
      Assert.assertTrue("JavaRandom " + t + " failed.",
              ChiSquareTest(new Random(seed[0]), t));
  }
  @Test public void MersenneTwister() {
    Assert.assertTrue(
            new RandomUtils.MersenneTwisterRNG(ArrayUtils.unpackInts(seed[0])).nextLong()
            == new RandomUtils.MersenneTwisterRNG(ArrayUtils.unpackInts(seed[0])).nextLong());
    for (NumType t : types)
      Assert.assertTrue("MersenneTwister " + t + " failed.",
              ChiSquareTest(new RandomUtils.MersenneTwisterRNG(ArrayUtils.unpackInts(seed[0])), t));
  }
  @Test public void XorShift() {
    Assert.assertTrue(new RandomUtils.XorShiftRNG(seed[0]).nextLong() == new RandomUtils.XorShiftRNG(seed[0]).nextLong());
    for (NumType t : types)
      Assert.assertTrue("XorShift " + t + " failed.",
              ChiSquareTest(new RandomUtils.XorShiftRNG(seed[0]), t));
  }
  @Test public void PCG() {
    Assert.assertTrue(new RandomUtils.PCGRNG(seed[0], seed[1]).nextLong() == new RandomUtils.PCGRNG(seed[0], seed[1]).nextLong());
    for (NumType t : types)
      Assert.assertTrue("PCG " + t + " failed.",
              ChiSquareTest(new RandomUtils.PCGRNG(seed[0], seed[1]), t));
  }

  static boolean ChiSquareTest(Random rng, NumType type) {
    final double n = 10000;		// observations
    final int k = 21 ;		// bins
    final int reps = 1000;		// reps

    double[] chi_2 = new double[reps];
    double[][] N = new double[reps][k];

    Timer timer = new Timer();
    for (int r=0; r<reps; ++r) {
      if (type == NumType.DOUBLE) {
        for (int i = 0; i < n; ++i) N[r][(int) (rng.nextDouble() * k)]++;
      }
      else if (type == NumType.FLOAT) {
        for (int i = 0; i < n; ++i) N[r][(int) (rng.nextFloat() * k)]++;
      }
      else if (type == NumType.INT) {
        for (int i = 0; i < n; ++i) N[r][Math.abs(rng.nextInt())/(Integer.MAX_VALUE/k)]++;
      }
      else if (type == NumType.LONG) {
        for (int i = 0; i < n; ++i) N[r][(int)(Math.abs(rng.nextLong())/(Long.MAX_VALUE/k))]++;
      }
      else throw water.H2O.unimpl();
    }
    double time = timer.time();

    for (int r=0; r<reps; ++r) {
      chi_2[r] = 0;
      for (int i = 0; i < k; i++) {
        chi_2[r] += (N[r][i] - n / k) * (N[r][i] - n / k) / (n / k);
      }
    }
    Log.info("\n" + rng.getClass().getSimpleName() + " " + type + ":\n"
            + reps + " Chi-Square tests (N=" + n + ", " + (k - 1) + " DOFs) in "
            + time/1000. + " secs");

    int suspect = 0;
    int nonrandom = 0;
    for (int r=0;r<reps;r++) {
      //See http://www.math.umn.edu/~garrett/students/reu/pRNGs.pdf for statistical bounds
      // outside 1-th or 99-th percentile - should only happen 2*1% of the time
      if (chi_2[r] > 37.57 || chi_2[r] < 8.26) {
        nonrandom++;
//        Log.warn("Non-Random RNG! chi^2 = " + chi_2[r]);
      }
      //between 1-th and 5-th or 95-th and 99-th percentile - should only happen 2*4% of the time
      else if (chi_2[r] > 31.41 || chi_2[r] < 10.85) {
        suspect++;
//        Log.warn("Suspect RNG! chi^2 = " + chi_2[r]);
      }
    }
    Log.info((float)nonrandom/reps*100 + "% non-random sequences.");
    Log.info((float)suspect/reps*100 + "% suspect sequences.");

    if (suspect > 0.08 * reps)
      Log.warn("Too many (>8%) suspect (between 1-th and 5-th and between 95-th and 99-th percentile) RNG sequences found!");
    if (nonrandom > 0.02 * reps)
      Log.warn("Too many (>2%) non-random (outside 1-th and 99-th percentile) RNG sequences found!");

    // add 50% extra margin for small data sampling noise
    return (suspect <= 0.08 * 1.5 * reps && nonrandom <= 0.02 * 1.5 * reps);
  }
}
