package hex;

import org.junit.Test;
import water.TestBase;
import water.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

public class AUCBuilderTest extends TestBase {

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
  public void testBinningQuality() {
    int N = 10;
    // rmse of the histogram when data are generated form interval [0, 1]
    double uniformRMSE = testHisto(
            new RandArrayGen(42) {
              @Override 
              void fillRandomVals(double[] values) { 
                for (int i = 0; i < values.length; i++) { 
                  values[i] = _r.nextDouble(); 
                }
              }
            }, N);
    // rmse of the histogram when the first half of the input is generated from [0, 0.5] and second half from [0.5, 1]
    // first half builds the bins to accommodate for [0, 0.5] and this structure needs to be adapted for the second half
    double splitUniformRMSE = testHisto(
            new RandArrayGen(42) {
              @Override 
              void fillRandomVals(double[] values) {
                for (int i = 0; i < values.length / 2; i++) { 
                  values[i] = _r.nextDouble() / 2; 
                }
                for (int i = values.length / 2; i < values.length; i++) { 
                  values[i] = 0.5 + (_r.nextDouble() / 2); 
                } 
              }
            }, N);
    // shows that quality of the histogram can be affected by ordering of the data
    assertEquals(splitUniformRMSE, 5*uniformRMSE, 1);
  }

  private double testHisto(RandArrayGen rag, int n) {
    double sum = 0;
    for (int i = 0; i < n; i++) {
      sum += testHisto(rag);
    }
    return sum / n;
  }
  
  private double testHisto(RandArrayGen rag) {
    AUC2.AUCBuilder ab = new AUC2.AUCBuilder(11);
    double[] values = new double[1000];
    rag.fillRandomVals(values);
    for (double v : values) {
      ab.perRow(v, 1, 1);
    }
    return histoRMSE(ab, values);
  }
  
  private double histoRMSE(AUC2.AUCBuilder ab, double[] values) {
    double[] ths = Arrays.copyOf(ab._ths, ab._nBins);
    int[] actual = new int[ths.length];
    for (int i = 0; i < ths.length; i++) {
      actual[i] = (int) ab._tps[i];
    }

    int[] expected = new int[ths.length];
    for (double v : values) {
      int idx = Arrays.binarySearch(ths, v);
      if (idx >= 0) {
        expected[idx]++;
      } else {
        idx = -idx - 1;
        if (idx == expected.length)
          idx = expected.length - 1;
        else if (idx > 0) {
          double dist_left = v - ths[idx-1];
          double dist_rght = ths[idx] - v;
          if (dist_left < dist_rght) {
            idx--;
          }
        }
        expected[idx]++;
      }
    }

    double tse = 0;
    for (int i = 0; i < actual.length; i++) {
      tse += Math.pow(actual[i] - expected[i], 2);
    }
    System.out.println("Actual  : " + Arrays.toString(actual));
    System.out.println("Expected: " + Arrays.toString(expected));
    double rmse = Math.sqrt(tse) / actual.length;
    System.out.println("RMSE    : " + rmse);
    return rmse;
  }

  private static abstract class RandArrayGen {
    Random _r;

    private RandArrayGen(long seed) {
      _r = new Random(seed);
    }

    abstract void fillRandomVals(double[] values);
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

  @Test
  public void testCMfor01() {
    String[] domain = new String[]{"0", "1"};
    
    AUC2.AUCBuilder abFull = new AUC2.AUCBuilder(2);
    abFull.perRow(0, 0, 1);
    abFull.perRow(0, 1, 2);
    abFull.perRow(1, 0, 3);
    abFull.perRow(1, 1, 4);
    AUC2 auc = AUC2.make01AUC(abFull);
    double[][] cmData = auc.defaultCM();
    Log.debug(new ConfusionMatrix(cmData, domain).toASCII());
    assertArrayEquals(new double[]{1, 3}, cmData[0], 0);
    assertArrayEquals(new double[]{2, 4}, cmData[1], 0);

    // check CM when classifier predicts only one class
    checkCMOneObs(0, 0);
    checkCMOneObs(0, 1);
    checkCMOneObs(1, 0);
    checkCMOneObs(1, 1);
  }

  private static void checkCMOneObs(int pred, int act) {
    AUC2.AUCBuilder ab = new AUC2.AUCBuilder(2);
    ab.perRow(pred, act, 1);
    double[][] cmData = AUC2.make01AUC(ab).defaultCM();
    Log.debug("pred = " + pred + "; act = " + act);
    Log.debug(new ConfusionMatrix(cmData, new String[]{"0", "1"}).toASCII());
    assertEquals(1, cmData[act][pred], 0);
  }

  @Test
  public void testRestrictToMaxCriterion() {
    AUC2.AUCBuilder ab = new AUC2.AUCBuilder(10);

    ab.perRow(0.0, 1, 1);
    ab.perRow(0.2, 0, 1);
    ab.perRow(0.4, 0, 1);
    ab.perRow(0.6, 1, 1);
    ab.perRow(0.8, 1, 1);
    ab.perRow(1.0, 1, 1);
    
    AUC2 auc = new AUC2(ab);
    // sanity check
    assertEquals(2, auc._max_idx);
    assertEquals(0.6, auc.defaultThreshold(), 0);
    assertEquals(0.17, auc.defaultErr(), 0.01);
    
    AUC2 auc1Bin = auc.restrictToMaxCriterion();

    // sanity check of 1-bin AUC2
    assertEquals(0, auc1Bin._max_idx);
    assertEquals(0.6, auc1Bin.defaultThreshold(), 0);
    assertEquals(0.17, auc1Bin.defaultErr(), 0.01);

    // check we propagated everything
    assertEquals(auc._auc, auc1Bin._auc, 0);
    assertEquals(auc._pr_auc, auc1Bin._pr_auc, 0);
    assertEquals(auc._gini, auc1Bin._gini, 0);
    for (AUC2.ThresholdCriterion tc : AUC2.ThresholdCriterion.values()) {
      double expected = tc.exec(auc, auc._max_idx);
      assertEquals("Value of metric " + tc + " expected to be the same", expected, tc.exec(auc1Bin, 0), 0);
    }
  }
  
}
