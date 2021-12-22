package hex.tree;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class GlobalQuantilesCalcTest extends TestUtil {

  @Test
  public void testGlobalQuantiles() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile("smalldata/logreg/prostate.csv"));
      fr.remove("CAPSULE");
      fr.remove("RACE");
      fr.remove("DCAPS");
      DKV.put(fr);
      double[][] splitPoints = GlobalQuantilesCalc.splitPoints(fr, null, 5, 5);
      for (int i = 0; i < fr.numCols(); i++) {
        double[] spI = splitPoints[i];
        assertNotNull(spI);
        // expecting quantiles to span from (inclusive) min...maxEx (exclusive)
        assertEquals(spI[0], fr.vec(i).min(), 0); // first element is minimum
        assertTrue(spI[spI.length - 1] < fr.vec(i).max() - 1e-3); // last element is not maximum by a good margin
      }
    } finally {
      Scope.exit(); 
    }
  }
}
