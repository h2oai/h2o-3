package hex.tree;

import hex.tree.gbm.GBMModel;
import org.junit.Test;

import static org.junit.Assert.*;

public class SharedTreeModelTest {

    @Test
    public void testUseRowSampling() {
        // disabled by default
        assertFalse(new TestParameters().useRowSampling());
        // enabled by user
        {
            TestParameters tp = new TestParameters();
            tp._sample_rate = 1 - 1e-9;
            assertTrue(tp.useRowSampling());
        }
        {
            TestParameters tp = new TestParameters();
            tp._sample_rate_per_class = new double[0];
            assertTrue(tp.useRowSampling());
        }
    }

    @Test
    public void testUseColSampling() {
        // disabled by default
        assertFalse(new TestParameters().useColSampling());
        // enabled by user
        {
            TestParameters tp = new TestParameters();
            tp._col_sample_rate_change_per_level = 1 - 1e-9;
            assertTrue(tp.useColSampling());
        }
        {
            TestParameters tp = new TestParameters();
            tp._col_sample_rate_per_tree = 1 - 1e-9;
            assertTrue(tp.useColSampling());
        }
    }

    @Test
    public void testIsStochastic() {
        // false by default
        assertFalse(new TestParameters().isStochastic());
        // true if col/row sampling is enabled
        {
            TestParameters tp = new TestParameters();
            tp._force_col_sampling = true;
            assertTrue(tp.isStochastic());
        }
        {
            TestParameters tp = new TestParameters();
            tp._force_row_sampling = true;
            assertTrue(tp.isStochastic());
        }
    }
    
    private static class TestParameters extends GBMModel.GBMParameters {
        boolean _force_row_sampling;
        boolean _force_col_sampling;

        @Override
        public boolean useColSampling() {
            return _force_col_sampling || super.useColSampling();
        }

        @Override
        public boolean useRowSampling() {
            return _force_row_sampling || super.useRowSampling();
        }
    }
    
}
