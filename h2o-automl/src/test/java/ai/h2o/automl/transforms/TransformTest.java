package ai.h2o.automl.transforms;

import ai.h2o.automl.TestUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;
import water.fvec.TransformWrappedVec;

public class TransformTest extends TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void testTransformReflection() {
    Transform.printOps();
  }

  @Test public void testExpr() {
    Frame fr=null;
    try {
      fr = parse_test_file(Key.make("a.hex"), "smalldata/iris.csv");

      Expr x = Expr.unOp("log", fr.vec(0));
      System.out.println(x.toRapids());

      TransformWrappedVec twv = x.toWrappedVec();
      System.out.println(twv.at(0));
      twv.remove();

      x.bop("-", x, 1);
      System.out.println(x.toRapids());
      System.out.println((twv = x.toWrappedVec()).at(0));
      twv.remove();

      x.bop("+", fr.vec(0), x);
      System.out.println(x.toRapids());
      System.out.println((twv=x.toWrappedVec()).at(0));
      twv.remove();

      x.bop("*", fr.vec(2), x);
      System.out.println(x.toRapids());
      System.out.println((twv=x.toWrappedVec()).at(0));
      twv.remove();

      x.uop("sqrt");
      System.out.println(x.toRapids());
      System.out.println((twv=x.toWrappedVec()).at(0));
      twv.remove();

//      new MRTask() {
//        @Override public void map(Chunk[] c) {
//          TODO: fill in
//        }
//      }.doAll(x.toWrappedVec(), fr.vec(0), fr.vec(2));

    } finally {
      if( null!=fr ) fr.delete();
    }

  }
}
