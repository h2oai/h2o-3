package hex.genmodel.algos.coxph;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoxPHMojoModelTest {

    @Test
    public void testFeatureValue() {
        final double[] row = {0, 1, 2, 3, 4, 5, 6};
        final CoxPHMojoModel mojo = new CoxPHMojoModel(null, null, null);

        mojo._strata_len = 0;
        assertEquals(1.0, mojo.featureValue(row, 1), 0);
        assertEquals(4.0, mojo.featureValue(row, 4), 0);

        mojo._strata_len = 2;
        assertEquals(3.0, mojo.featureValue(row, 1), 0);
        assertEquals(6.0, mojo.featureValue(row, 4), 0);
    }

    @Test
    public void testForOneCategory() {
        final double[] row = {0, 1, 2, 3, 4, 5, 6};
        final CoxPHMojoModel mojo = new CoxPHMojoModel(null, null, null);
        mojo._cat_offsets = new int[]{0, 2, 8, 11, 15};
        mojo._coef = new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6};

        mojo._strata_len = 0;
        assertEquals(0.4, mojo.forOneCategory(row, 1, 0), 0);

        mojo._strata_len = 2;
        assertEquals(0.6, mojo.forOneCategory(row, 1, 0), 0);
    }

}
