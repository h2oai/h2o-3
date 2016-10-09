package water.fvec;

import com.google.common.base.Function;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.MRTask;
import water.TestUtil;
import water.util.Closure;

import javax.annotation.Nullable;

class Op implements Function<Long, Double> {
  public Double apply(Long x) {
    return Math.sin(0.0001 * x);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return false;
  }
}

public class FunVecTest extends TestUtil {
  public FunVecTest() {}

  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }

  @Test public void testSineFunction() {
    Vec v=null;
    try {
      v = Vec.makeFromFunction(1 << 20, new Op());
      int random = 44444;
      Assert.assertEquals(Math.sin(random * 0.0001), v.at(random), 0.000001);
      Function<Double, Double> sq = new Function<Double, Double>() {
        public Double apply(Double x) { return x*x;}
      };
      Vec iv = new FunVec(v, sq);
      new MRTask() {
        @Override
        public void map(Chunk c) {
          for (int i = 0; i < c._len; ++i) {
            long index = c._start + i;
            double expected = Math.sin(0.0001 * index) * Math.sin(0.0001 * index);
            double x = c.atd(i);
            if (x != expected)
              throw new RuntimeException("moo @" + c._cidx + "/" + i + " x=" + x + "; expected=" + expected);
          }
        }
      }.doAll(iv);
      iv.remove();
    } catch(Exception x) {
      x.printStackTrace();
      Assert.fail("Oops, exception " + x);
    } finally {
      if (v != null) v.remove();
    }
  }
}
