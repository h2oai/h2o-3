package hex;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.TestBase;
import water.exceptions.H2OIllegalArgumentException;



public class AUC2Test extends TestBase {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void checkRecallValidity() throws Exception {
    double[] tps = {1.0,0.0,1.0,1.0,1.0,0.0,1.0,1.0};
    double[] fns = {0.0,1.0,0.0,0.0,0.0,0.0,0.0,0.0};
    AUC2.AUCBuilder bldr = new AUC2.AUCBuilder(10);
    bldr._n = 8;
    System.arraycopy(tps, 0, bldr._tps, 0, tps.length);
    System.arraycopy(fns, 0, bldr._fps, 0, tps.length);
    AUC2 auc2 = new AUC2(bldr);
    auc2.checkRecallValidity(); // expect no failure

    // now corrupt the data
    ArrayUtils.reverse(auc2._tps);
    thrown.expect(H2OIllegalArgumentException.class);
    thrown.expectMessage("Illegal argument: 1 of function: recall: 1.0 > 0.8333333333333334");
    auc2.checkRecallValidity();
  }

}
