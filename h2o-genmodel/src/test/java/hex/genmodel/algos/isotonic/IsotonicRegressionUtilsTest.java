package hex.genmodel.algos.isotonic;

import org.junit.Test;

import static org.junit.Assert.*;

public class IsotonicRegressionUtilsTest {

    @Test
    public void testClip() {
        assertEquals(0.1, IsotonicRegressionUtils.clip(Double.NEGATIVE_INFINITY, 0.1, 0.2), 0);
        assertEquals(0.1, IsotonicRegressionUtils.clip(0.1 - Double.MIN_VALUE, 0.1, 0.2), 0);
        assertEquals(0.2, IsotonicRegressionUtils.clip(0.2 + Double.MIN_VALUE, 0.1, 0.2), 0);
        assertEquals(0.2, IsotonicRegressionUtils.clip(Double.POSITIVE_INFINITY, 0.1, 0.2), 0);
        assertEquals(Double.NaN, IsotonicRegressionUtils.clip(Double.NaN, 0.1, 0.2), 0);
        assertEquals(0.15, IsotonicRegressionUtils.clip(0.15, 0.1, 0.2), 0);
    }

}
