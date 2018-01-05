package hex;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

}