package hex;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

public class AUCBuilderTest {

  @Test
  public void testPerRow() {
    AUC2.AUCBuilder ab = new AUC2.AUCBuilder(10);
    for (int i = 0; i < 100; i ++)
      ab.perRow(i/100d, 1, 1.0);

    double[] actual_ths = new double[10];
    System.arraycopy(ab._ths, 0, actual_ths, 0, actual_ths.length);
    double[] expected_ths = {0.05, 0.16, 0.25, 0.335, 0.445, 0.555, 0.655, 0.76, 0.875, 0.965};
    assertArrayEquals(expected_ths, actual_ths, 1e-5);
  }

  @Test // this tests make sure that the fast path execution of "perRow" is consistent with "mergeOneBin"
  public void testPerRow_compat() throws Exception {
    AUC2.AUCBuilder ab = new AUC2.AUCBuilder(400);
    AUC2.AUCBuilder ab_slow = new AUC2.AUCBuilder(400, false);

    long t = 0, t_old = 0;

    try (GZIPInputStream gz = new GZIPInputStream(AUCBuilderTest.class.getResourceAsStream("aucbuilder.csv.gz"))) {
      BufferedReader br = new BufferedReader(new InputStreamReader(gz));
      String line;
      int i = 0;
      while ((line = br.readLine()) != null) {
        String[] cols = line.split(",");
        double p1 = Double.parseDouble(cols[0]);
        int act = Integer.parseInt(cols[1]);
        long st = System.currentTimeMillis();
        ab.perRow(p1, act, 1.0);
        t += System.currentTimeMillis() - st;
        long st_old = System.currentTimeMillis();
        ab_slow.perRow(p1, act, 1.0);
        t_old += System.currentTimeMillis() - st_old;

        for (int j = 0; j < 400; j++) {
          assertEquals("Error in ths, line: " + i, ab._ths[j], ab_slow._ths[j], 0);
          assertEquals("Error in tps, line: " + i, ab._tps[j], ab_slow._tps[j], 0);
          assertEquals("Error in tps, line: " + i, ab._fps[j], ab_slow._fps[j], 0);
          assertEquals("Error in sqe, line: " + i, ab._sqe[j], ab_slow._sqe[j], 0);
        }

        i++;
      }
    }

    System.out.println("Total time with speedup: " + t + "ms; orginal time: " + t_old + "ms.");
  }

  @Test
  public void testPubDev6399ReduceDoesntUsePreviousKnownSSX() {
    AUC2.AUCBuilder abL = new AUC2.AUCBuilder(10);
    for (int i = 0; i < abL._nBins; i++) {
      abL.perRow(i, 1, 1);
    }
    abL.perRow(5.5, 1, 1); // insert something in the middle and cause a bin merge
    assertEquals(0, abL._ssx); // bin with smallest error is known but shouldn't be used!
    
    AUC2.AUCBuilder abR = new AUC2.AUCBuilder(10);
    abR.perRow(9, 1, 1); // single element, should be appended at the end when merging and then de-duped

    double[] expected = Arrays.copyOf(abL._ths, abL._n);

    // reduce!
    abL.reduce(abR);

    double[] ths = Arrays.copyOf(abL._ths, abL._n);
    assertArrayEquals(expected, ths, 0);
  }

  @Test
  public void testLargeWeights() {
    AUC2.AUCBuilder ab = new AUC2.AUCBuilder(2);

    double w = Double.MAX_VALUE;
    ab.perRow(0, 1, w);
    ab.perRow(0.3, 1, w);
    ab.perRow(0.7, 1, w);
    ab.perRow(1, 1, w);

    assertArrayEquals(new double[]{0.0, 0.85}, Arrays.copyOf(ab._ths, 2), 0);
  }

  @Test
  public void testCombineCenters() {
    double sqrtDMax = Math.sqrt(Double.MAX_VALUE); 
    assertEquals(sqrtDMax, AUC2.AUCBuilder.combine_centers(sqrtDMax, sqrtDMax, sqrtDMax, sqrtDMax), 0);
  }
  
}
