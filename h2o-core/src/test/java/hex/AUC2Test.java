package hex;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import water.MemoryManager;
import water.exceptions.H2OIllegalArgumentException;

import java.util.Random;

import static org.junit.Assert.*;

public class AUC2Test {

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

  @Test
  public void testAUC2_reduce(){
    AUC2.AUCBuilder aucBuilder1 = new AUC2.AUCBuilder(AUC2.NBINS);
    AUC2.AUCBuilder aucBuilder2 = new AUC2.AUCBuilder(AUC2.NBINS);

    double probs[] = generateRandomProbs(10000);
    int actls[] = generateRandomActuals(probs.length);

    for (int i = 0; i < probs.length; i++) {
      aucBuilder1.perRow(probs[i], actls[i], 1.0D);
      aucBuilder2.perRow(probs[i], actls[i], 1.0D);
    }

    aucBuilder1.reduce(aucBuilder2);
    AUC2 auc2 = new AUC2(aucBuilder1);
    assertNotNull(auc2);
    assertEquals(AUC2.NBINS, aucBuilder1._nBins);
    assertEquals(AUC2.NBINS, aucBuilder1._n);
  }

  private double[] generateRandomProbs(int count){
    double[] probs = new double[count];
    Random random = new Random();

    for (int i = 0; i < probs.length; i++) {
      probs[i] = random.nextDouble();
    }

    return probs;
  }

  private int[] generateRandomActuals(int count){
    int[] actuals = new int[count];
    Random random = new Random();
    for (int i = 0; i < actuals.length; i++) {
      actuals[i] = random.nextBoolean() ? 1 : 0;
    }
    return actuals;
  }


  @Test
  public void testAUC2_emptyBins(){
    AUC2.AUCBuilder aucBuilder1 = new AUC2.AUCBuilder(AUC2.NBINS);
    AUC2.AUCBuilder aucBuilder2 = new AUC2.AUCBuilder(AUC2.NBINS);

    aucBuilder1.perRow(0.5, 1, 1.0D);

    aucBuilder1.reduce(aucBuilder2);
    AUC2 auc2 = new AUC2(aucBuilder1);
    Assert.assertNotNull(auc2);
    Assert.assertEquals(1, auc2._nBins);
  }

}