package hex;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.exceptions.H2OIllegalArgumentException;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class AUC2Test {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void checkRecallValidity() throws Exception {
    double[] tps = {1.0,0.0,1.0,1.0,1.0,0.0,1.0,1.0};
    double[] fps = {0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0};
    AUC2.AUCBuilder bldr = new AUC2.AUCBuilder(10);
    bldr._n = 8;
    System.arraycopy(tps, 0, bldr._tps, 0, tps.length);
    System.arraycopy(fps, 0, bldr._fps, 0, tps.length);
    AUC2 auc2 = new AUC2(bldr);
    auc2.checkRecallValidity(); // expect no failure

    // now corrupt the data
    ArrayUtils.reverse(auc2._tps);
    thrown.expect(H2OIllegalArgumentException.class);
    thrown.expectMessage("Illegal argument: 1 of function: recall: 1.0 > 0.8333333333333334");
    auc2.checkRecallValidity();
  }

  @Test
  /*
   * Test the absolute MCC is calculated correctly with imprecision caused by double values
   * PUBDEV-7426
   */
  public void checkAbsoluteMCC() {
    double tp = 1005.1403389830544;
    double tn = 4794.8034643570945;
    double fp = 0;
    double fn = 0;
    double[] tps = {0, tp};
    double[] fps = {tn, 0};
    AUC2.AUCBuilder bldr = new AUC2.AUCBuilder(2);
    bldr._n = 2;
    System.arraycopy(tps, 0, bldr._tps, 0, tps.length);
    System.arraycopy(fps, 0, bldr._fps, 0, fps.length);
    AUC2 auc2 = new AUC2(bldr);
    // index = 0, because AUCBuilder switch the order of array
    double mcc = AUC2.ThresholdCriterion.absolute_mcc.exec(auc2, 0);
    double exact_mcc = (tp*tn - fp*fn);
    exact_mcc /= Math.sqrt((tp+fp)*(tp+fn)*(tn+fp)*(tn+fn));
    System.out.println("Returned MCC: "+ mcc +", exact MCC: "+ exact_mcc);
    Assert.assertEquals("It should return MCC = 1, but it returns "+ mcc, mcc, 1, 0);
    Assert.assertNotEquals("Exact mcc should not be the same as returned mcc due to double precision.", mcc, exact_mcc);
  }


  @Test
  public void perfectAUC() {
    checkAUC(new double[0], new double[0]);
    checkAUC(new double[]{0, 1}, new double[]{0, 1});
    checkAUC(new double[]{0, 1, 0}, new double[]{1, 0, 1});
    checkAUC(new double[]{0.1, 0.2, 0.2, 0.9}, new double[]{0, 1, 1, 1});
    checkAUC(new double[]{0.1, 0.2, 0.2, 0.9}, new double[]{0, 1, 0, 1});
    checkAUCRandomInput((int) 1e6);
  }

  @Test @Ignore // too large & slow to run by default
  public void perfectAUC_large() {
    checkAUCRandomInput((int) 1e8);
  }
  
  private static void checkAUCRandomInput(int n) {
    Random r = new Random(42);
    double[] probs = new double[n];
    double[] acts = new double[probs.length];
    for (int i = 0; i < probs.length; i++) {
      probs[i] = Math.abs(r.nextDouble());
      acts[i] = r.nextBoolean() ? 0 : 1;
    }
    checkAUC(probs, acts);
  }
  
  private static void checkAUC(double ds[], double[] acts) {
    long start = System.currentTimeMillis();
    double auc = AUC2.perfectAUC(ds, acts);
    long t1 = System.currentTimeMillis();
    double expected = slow_perfectAUC(ds, acts);
    long end = System.currentTimeMillis();
    System.out.println("Timing: perfectAUC=" + (t1 - start) + "ms, slow_perfectAUC: " + (end - t1) + "ms");
    assertEquals(expected, auc, 0);
  }
  
  // Original Cliff's perfectAUC that allocates an object per observation
  // testing shows the current method is 5-10x faster than the original one
  private static double slow_perfectAUC(double ds[], double[] acts) {
    Pair[] ps = new Pair[ds.length];
    for( int i=0; i<ps.length; i++ )
      ps[i] = new Pair(ds[i],(byte)acts[i]);
    return slow_perfectAUC(ps);
  }

  private static double slow_perfectAUC( Pair[] ps ) {
    // Sort by probs, then actuals - so tied probs have the 0 actuals before
    // the 1 actuals.  Sort probs from largest to smallest - so both the True
    // and False Positives are zero to start.
    Arrays.sort(ps,new java.util.Comparator<Pair>() {
      @Override public int compare(Pair a, Pair b ) {
        return a._prob<b._prob ? 1 : (a._prob==b._prob ? (b._act-a._act) : -1);
      }
    });

    // Compute Area Under Curve.  
    // All math is computed scaled by TP and FP.  We'll descale once at the
    // end.  Trapezoids from (tps[i-1],fps[i-1]) to (tps[i],fps[i])
    int tp0=0, fp0=0, tp1=0, fp1=0;
    double prob = 1.0;
    double area = 0;
    for( Pair p : ps ) {
      if( p._prob!=prob ) { // Tied probabilities: build a diagonal line
        area += (fp1-fp0)*(tp1+tp0)/2.0; // Trapezoid
        tp0 = tp1; fp0 = fp1;
        prob = p._prob;
      }
      if( p._act==1 ) tp1++; else fp1++;
    }
    area += (double)tp0*(fp1-fp0); // Trapezoid: Rectangle + 
    area += (double)(tp1-tp0)*(fp1-fp0)/2.0; // Right Triangle

    // Descale
    return area/tp1/fp1;
  }

  private static class Pair {
    final double _prob; final byte _act;
    Pair( double prob, byte act ) { _prob = prob; _act = act; }
  }

}
