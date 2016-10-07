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

public class UdfVecTest extends TestUtil {
  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }

  @Test public void testSineFunction() {
    Vec v=null;
    try {
      v = Vec.makeFromFunction(1 << 20, new Op());
      int random = 44444;
      Assert.assertEquals(Math.sin(random * 0.0001), v.at(random), 0.000001);
//      AstRoot ast = Rapids.parse("{ x . (* 1 x) }");
//      Vec iv = new AstVec(v, ast);
//      new MRTask() {
//        @Override
//        public void map(Chunk c) {
//          for (int i = 0; i < c._len; ++i) {
//            double x = c.atd(i);
//            if (Math.abs(x) > 1.0)
//              throw new RuntimeException("moo");
//          }
//        }
//      }.doAll(iv);
//      iv.remove();
    } catch(Exception x) {
      x.printStackTrace();
      Assert.fail("Oops, exception " + x);
    } finally {
      if (v != null) v.remove();
    }
  }
}
