package hex.tree;

import hex.DistributionFactory;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBMModel;
import org.junit.Test;

import static org.junit.Assert.*;

public class ConstraintsTest {

  @Test
  public void needsGammaNom() {
    Constraints csF = new Constraints(new int[0], DistributionFactory.getDistribution(DistributionFamily.bernoulli), true);
    assertFalse("Bernoulli distribution doesn't need Gamma nominator", csF.needsGammaNom());

    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._distribution = DistributionFamily.tweedie;
    Constraints csT = new Constraints(new int[0], DistributionFactory.getDistribution(parms), true);
    assertTrue("Tweedie distribution needs Gamma nominator", csT.needsGammaNom());
  }

}
