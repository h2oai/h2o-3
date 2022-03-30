package hex.genmodel.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MathUtilsTest {

    @Test
    public void testHarmonicNumberEstimation() {
        assertEquals("Result is not correct", 0, MathUtils.harmonicNumberEstimation(-20), 1e-3);
        assertEquals("Result is not correct", 0, MathUtils.harmonicNumberEstimation(0), 1e-3);
        assertEquals("Result is not correct", 1.270362, MathUtils.harmonicNumberEstimation(2), 1e-3);
    }
}
