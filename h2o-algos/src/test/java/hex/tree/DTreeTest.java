package hex.tree;

import hex.tree.DHistogram.NASplitDir;
import hex.genmodel.utils.DistributionFamily;
import org.junit.Test;
import water.TestBase;

import static org.junit.Assert.*;

public class DTreeTest extends TestBase {

  @Test
  public void testFindBestSplitPoint_pubdev6495() {

    double[] ws = new double[]{10,         10,         100, 1  };
    double[] cs = new double[]{Double.NaN, Double.NaN, 0.5, 1.5};
    double[] ys = new double[]{0,          1,          0,   1  };
    int[] rows  = new int[]   {0,          1,          2,   3  };

    DHistogram hs = new DHistogram("test_hs", 2, 2, (byte) 0, 0, 2, 0.01,
            SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 123, null, null);
    hs.init();
    hs.updateHisto(ws, null, cs, ys,rows, rows.length, 0);

    // 1. min_rows = #NAs
    DTree.Split s1 = DTree.findBestSplitPoint(hs, 0, 20, 0, Double.NaN, Double.NaN, false);
    assertNotNull(s1);

    // 2. min_rows = #NAs + 1
    DTree.Split s2 = DTree.findBestSplitPoint(hs, 0, 21, 0, Double.NaN, Double.NaN, false);
    assertNull(s2); // not enough improvement => no split

    // 3. allow negative (!!!) split improvement, min_rows = #NAs + 1
    DHistogram hsN = new DHistogram("test_hs", 2, 2, (byte) 0, 0, 2, -9,
            SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 123, null, null);
    hsN.init();
    hsN.updateHisto(ws, null, cs, ys,rows, rows.length, 0);
    DTree.Split s3 = DTree.findBestSplitPoint(hsN, 0, 21, 0, Double.NaN, Double.NaN, false);
    assertNotNull(s3); // split was made thanks to the negative split improvement
    assertEquals(s3._se, seNonNA(ws, cs, ys), 0); // SE is actually the non-NA SE
    assertTrue(s3._se < s1._se); // SE is different because the SE of NAs is not accounted for
    // case (2) shows that we would abandon the split if it was not for the negative minimum split improvement 
  }

  @Test // check that bounds constrained "NA vs REST" splits have correct Square Errors
  public void testFindBestSplitPoint_bounds_NAvsREST() {
    final double min_pred = 0.3;
    final double max_pred = 0.4;

    DHistogram hs = makeHisto(1, min_pred, max_pred);
    ExpectedSplitInfo expectedSplitInfo = updateHisto(hs, min_pred, max_pred, 1.0);
    
    DTree.Split split = DTree.findBestSplitPoint(hs, 0, 0, 0, min_pred, max_pred, true);
    
    expectedSplitInfo.checkSplit(split);
  }

  @Test // check that bounds constrained splits include the error of NAs in the Square Error
  public void testFindBestSplitPoint_bounds_NAs() {
    final double min_pred = 0.3;
    final double max_pred = 0.4;

    DHistogram hs = makeHisto(100, min_pred, max_pred);
    ExpectedSplitInfo expectedSplitInfo = updateHisto(hs, min_pred, max_pred,0.1);

    DTree.Split split = DTree.findBestSplitPoint(hs, 0, 100, 0, min_pred, max_pred, true);

    expectedSplitInfo.checkSplit(split);
  }

  private static DHistogram makeHisto(int nbins, double min_pred, double max_pred) {
    Constraints c = new Constraints(new int[]{1}, DistributionFamily.gaussian, true)
            .withNewConstraint(0, 1, min_pred)
            .withNewConstraint(0, 0, max_pred);
    assertEquals(min_pred, c._min, 0);
    assertEquals(max_pred, c._max, 0);
    return new DHistogram("test_hs", nbins, 2, (byte) 0, 0, 10, 0.01,
            SharedTreeModel.SharedTreeParameters.HistogramType.UniformAdaptive, 123, null, c);
  }
  
  private static ExpectedSplitInfo updateHisto(DHistogram hs, final double min_pred, final double max_pred, double na_percent) {
    hs.init();
    final int N = 1000;
    final int S = 600;
    final int NA = (int) (S * na_percent);
    double[] ws = new double[N];
    double[] cs = new double[N];
    double[] ys = new double[N];
    int[] rows = new int[N];
    double p0 = 0.25; // < min_pred
    double p1 = 0.45; // > max_pred 
    double ys_mean = (S * p0 + (N - S) * p1) / N;
    double se = 0;
    double se_min_pred = 0;
    double se_max_pred = 0;
    for (int i = 0; i < N; i++) {
      ws[i] = 1.0;
      rows[i] = i;
      cs[i] = i < NA ? Double.NaN : i / (N / 10.0);
      ys[i] = i < S ? p0 : p1;
      se += (ys_mean - ys[i]) * (ys_mean - ys[i]);
      if (i < S) {
        se_min_pred += (min_pred - ys[i]) * (min_pred - ys[i]);
      } else {
        se_max_pred += (max_pred - ys[i]) * (max_pred - ys[i]);
      }
    }
    hs.updateHisto(ws, null, cs, ys, rows, N, 0);
    
    if (na_percent == 1.0) {
      return new ExpectedSplitInfo(NASplitDir.NAvsREST, se, se_max_pred, se_min_pred);
    } else {
      // ignore actual SE and use non-NA SE
      double nna_se = seNonNA(ws, cs, ys); 
      return new ExpectedSplitInfo(NASplitDir.NALeft, nna_se, se_min_pred, se_max_pred);
    }
  }
  
  private static double seNonNA(double ws[], double[] cs, double[] ys) {
    double nna_y_sum = 0;
    double nna_y_weight = 0;
    for (int i = 0; i < ys.length; i++) {
      if (Double.isNaN(cs[i]))
        continue;
      nna_y_weight += ws[i];
      nna_y_sum += ws[i] * ys[i];
    }
    double nna_y_mean = nna_y_sum / nna_y_weight;
    double nna_se = 0;
    for (int i = 0; i < ys.length; i++) {
      if (Double.isNaN(cs[i]))
        continue;
      nna_se += ws[i] * (ys[i] - nna_y_mean) * (ys[i] - nna_y_mean);
    }
    return nna_se;
  }
  
  private static class ExpectedSplitInfo {
    NASplitDir _nasplit;
    double _se, _se0, _se1;

    ExpectedSplitInfo(NASplitDir nasplit, double se, double se0, double se1) {
      _nasplit = nasplit;
      _se = se;
      _se0 = se0;
      _se1 = se1;
    }

    void checkSplit(DTree.Split split) {
      assertNotNull(split);
      assertEquals(_nasplit, split._nasplit);
      assertEquals(_se, split._se, 1e-8);
      assertEquals(_se0, split._se0, 1e-8);
      assertEquals(_se1, split._se1, 1e-8);
    }
  }
  
}
