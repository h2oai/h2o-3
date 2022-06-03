package hex.tree;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
      double[][] splitPoints = GlobalQuantilesCalc.splitPoints(fr, null, null, 5, 5);
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

  @Test
  public void testGlobalQuantilesWithGivenSplitPoints() {
    try {
      Scope.enter();
      Frame fr = Scope.track(parseTestFile("smalldata/logreg/prostate.csv"));
      // first without prior split points
      final double[][] splitPointsFull = GlobalQuantilesCalc.splitPoints(fr, null, null, 5, 5);
      Set<Integer> expectedSkipped = new HashSet<>(Arrays.asList(1, 5));
      assertEquals(splitPointsFull.length, fr.numCols());
      for (int i = 0; i < splitPointsFull.length; i++) {
        final double[] spI = splitPointsFull[i]; 
        if (expectedSkipped.contains(i)) {
          assertNull(spI);
        } else {
          assertNotNull(spI);
          // expecting quantiles to span from (inclusive) min...maxEx (exclusive)
          assertEquals(spI[0], fr.vec(i).min(), 0); // first element is minimum
          assertTrue(spI[spI.length - 1] < fr.vec(i).max() - 1e-3); // last element is not maximum by a good margin
        }
      }
      // now define prior split points and check the columns were skipped
      final double[][] priorSplitPoints = {
              {}, null, {}, null, null, {}, {}, null, null
      };
      final double[][] splitPoints = GlobalQuantilesCalc.splitPoints(fr, null, priorSplitPoints, 5, 5);
      assertEquals(splitPoints.length, splitPointsFull.length);
      for (int i = 0; i < splitPoints.length; i++) {
        if (priorSplitPoints[i] != null) {
          assertNull(splitPoints[i]);
        } else {
          if (splitPointsFull[i] == null) {
            assertNull(splitPoints[i]);
          } else {
            assertArrayEquals(splitPoints[i], splitPointsFull[i], 0);
          }
        }
      }
    } finally {
      Scope.exit();
    }
  }


}
