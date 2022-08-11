package hex.isotonic;

import org.junit.Test;

import static org.junit.Assert.*;

public class IsotonicRegressionModelTest {

    @Test
    public void clip() {
        assertEquals(0.1, IsotonicRegressionModel.clip(Double.NEGATIVE_INFINITY, 0.1, 0.2), 0);
        assertEquals(0.1, IsotonicRegressionModel.clip(0.1 - Double.MIN_VALUE, 0.1, 0.2), 0);
        assertEquals(0.2, IsotonicRegressionModel.clip(0.2 + Double.MIN_VALUE, 0.1, 0.2), 0);
        assertEquals(0.2, IsotonicRegressionModel.clip(Double.POSITIVE_INFINITY, 0.1, 0.2), 0);
        assertEquals(Double.NaN, IsotonicRegressionModel.clip(Double.NaN, 0.1, 0.2), 0);
        assertEquals(0.15, IsotonicRegressionModel.clip(0.15, 0.1, 0.2), 0);
    }

}
