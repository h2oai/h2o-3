package hex;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.exceptions.H2OIllegalArgumentException;



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
    assert mcc == 1: "It should return MCC = 1, but it returns "+ mcc;
    assert mcc != exact_mcc;
    System.out.println("Returned MCC: "+ mcc +", exact MCC: "+ exact_mcc);
  }
}
