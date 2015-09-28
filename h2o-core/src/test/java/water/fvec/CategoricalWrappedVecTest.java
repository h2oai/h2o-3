package water.fvec;

import org.junit.*;

import water.Scope;
import water.TestUtil;
import water.util.Log;
import water.util.PrettyPrint;
import water.util.RandomUtils;

import java.util.Random;

public class CategoricalWrappedVecTest extends TestUtil {
  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }

  // Need to move test files over
  @Test public void testAdaptTo() {
    Scope.enter();
    Frame v1=null, v2=null;
    try {
      v1 = parse_test_file("smalldata/junit/mixcat_train.csv");
      v2 = parse_test_file("smalldata/junit/mixcat_test.csv");
      CategoricalWrappedVec vv = v2.vecs()[0].adaptTo(v1.vecs()[0].domain());
      Assert.assertArrayEquals("Mapping differs",new int[]{0,1,3},vv._map);
      Assert.assertArrayEquals("Mapping differs",new String[]{"A","B","C","D"},vv.domain());
      vv.remove();
    } finally {
      if( v1!=null ) v1.delete();
      if( v2!=null ) v2.delete();
      Scope.exit();
    }
  }

  /** Verifies that {@link CategoricalWrappedVec#computeMap(String[], String[])} returns
   *  correct values. */
  @Test public void testModelMappingCall() {
    Scope.enter();
    testModelMapping(ar("A", "B", "C"), ar("A", "B", "C"), ari(0,1,2));
    testModelMapping(ar("A", "B", "C"), ar(     "B", "C"), ari(  1,2));
    testModelMapping(ar("A", "B", "C"), ar(     "B"     ), ari(  1  ));

    testModelMapping(ar("A", "B", "C"), ar("A", "B", "C", "D"), ari(0,1,2,3));
    testModelMapping(ar("A", "B", "C"), ar(     "B", "C", "D"), ari(  1,2,3));
    testModelMapping(ar("A", "B", "C"), ar("B", "D"), ari(1, 3));
    Scope.exit();
  }

  @Ignore @Test public void testPerformance() {
    for (int N : new int[]{9999,100,1000,10000,20000,40000,80000}) {
      Scope.enter();
      String[] fromDomain = new String[N];
      String[] toDomain = new String[N];
      Random rng = RandomUtils.getRNG(0xDECAF);
      for (int i = 0; i < N; ++i) {
        byte[] b = new byte[(int)(Math.log10(N))];
        rng.nextBytes(b);
        fromDomain[i] = new String(b);
        rng.nextBytes(b);
        toDomain[i] = new String(b);
      }
      long start = System.currentTimeMillis();
      new CategoricalWrappedVec(fromDomain, toDomain);
      long duration = System.currentTimeMillis() - start;
      if (N==9999)
        Log.info("Warming up.");
      else
        Log.info("Time for categorical unification of two maps with each " + N + " factors (only partially overlapping): " + PrettyPrint.msecs(duration, true));
      Scope.exit();
    }
  }

  private static void testModelMapping(String[] modelDomain, String[] colDomain, int[] expectedMapping) {
    int[] mapping = new CategoricalWrappedVec(colDomain, modelDomain)._map;
    Assert.assertArrayEquals("Mapping differs",  expectedMapping, mapping);
  }
}
