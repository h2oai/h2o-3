package hex.tree;

import hex.ModelBuilder;
import hex.isotonic.IsotonicRegression;
import hex.isotonic.IsotonicRegressionModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.fvec.Frame;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
public class CalibrationHelperTest {

    @Test
    public void testIsotonicRegressionModelBuilderUsesClipping() {
        ModelBuilder<?, ?, ?> mb = CalibrationHelper.makeIsotonicRegressionModelBuilder(new Frame(), false);
        assertTrue(mb instanceof IsotonicRegression);
        assertEquals(
                IsotonicRegressionModel.OutOfBoundsHandling.Clip, 
                ((IsotonicRegressionModel.IsotonicRegressionParameters) mb._parms)._out_of_bounds
        );
    }

}
