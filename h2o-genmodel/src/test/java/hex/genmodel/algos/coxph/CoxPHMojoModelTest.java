package hex.genmodel.algos.coxph;

import org.junit.Test;
import java.nio.file.Paths;

import hex.genmodel.MojoModel;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.EasyPredictModelWrapper;

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

    /*
        Test backward compatibility of CoxPH mojo model trained in 3.32.1.3.
        Only using all features (both categorical and numeric). 
    */
    @Test
    public void testCoxPHBackwardCompatibilityAll332() throws Exception {
        String mojofile = String.valueOf(
            Paths.get(
                CoxPHMojoModelTest.class.getClassLoader().getResource("hex/genmodel/algos/coxph/CoxPH_bc_all_3_32.zip").toURI()
            ).toFile()
        );

        EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
                .setModel(MojoModel.load(mojofile));
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);

        String [][] inputs = new String[][] {
            {"0", "50", "1", "-17.1553730321697", "0.123203285420945","0",
                "0", "1", "-2.3004572363122033", "-23.840464872142775", "c0.l2", "c1.l2"},
            {"0", "6", "1", "3.83572895277207", "0.254620123203285", "0", 
                "0", "2", "33.01605679101838", "-12.944002705270874" ,"c0.l1","c1.l2"}, 
            {"0", "1", "0", "6.29705681040383", "0.265571526351814", "0", 
                "0", "3", "-22.54601829241052", "77.61631885563669", "c0.l2", "c1.l0"}
        };
        double [] expected = {0.0902431, 0.422815, 0.663896};
        double[] preds = new double[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            RowData row = new RowData();
            row.put("start", inputs[i][0]);
            row.put("stop", inputs[i][1]);
            row.put("event", inputs[i][2]);
            row.put("age", inputs[i][3]);
            row.put("year", inputs[i][4]);
            row.put("surgery", inputs[i][5]);
            row.put("transplant", inputs[i][6]);
            row.put("id", inputs[i][7]);
            row.put("C1", inputs[i][8]);
            row.put("C2", inputs[i][9]);
            row.put("C3", inputs[i][10]);
            row.put("C4", inputs[i][11]);
            preds[i] = model.predictCoxPH(row).value;
        }

        assertArrayEquals(expected, preds, 0.000001);
    }

    /*
        Test backward compatibility of CoxPH mojo model trained in 3.42.0.4.
        Only using all features (both categorical and numeric). 
    */
    @Test
    public void testCoxPHBackwardCompatibilityAll342() throws Exception {
        String mojofile = String.valueOf(
            Paths.get(
                CoxPHMojoModelTest.class.getClassLoader().getResource("hex/genmodel/algos/coxph/CoxPH_bc_all_3_42.zip").toURI()
            ).toFile()
        );

        EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
                .setModel(MojoModel.load(mojofile));
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);

        String [][] inputs = new String[][] {
            {"0", "50", "1", "-17.1553730321697", "0.123203285420945","0",
                    "0", "1", "-2.3004572363122033", "-23.840464872142775", "c0.l2", "c1.l2"},
            {"0", "6", "1", "3.83572895277207", "0.254620123203285", "0",
                    "0", "2", "33.01605679101838", "-12.944002705270874" ,"c0.l1","c1.l2"},
            {"0", "1", "0", "6.29705681040383", "0.265571526351814", "0",
                    "0", "3", "-22.54601829241052", "77.61631885563669", "c0.l2", "c1.l0"}
        };
        double [] expected = {0.0902431, 0.422815, 0.663896};
        double[] preds = new double[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            RowData row = new RowData();
            row.put("start", inputs[i][0]);
            row.put("stop", inputs[i][1]);
            row.put("event", inputs[i][2]);
            row.put("age", inputs[i][3]);
            row.put("year", inputs[i][4]);
            row.put("surgery", inputs[i][5]);
            row.put("transplant", inputs[i][6]);
            row.put("id", inputs[i][7]);
            row.put("C1", inputs[i][8]);
            row.put("C2", inputs[i][9]);
            row.put("C3", inputs[i][10]);
            row.put("C4", inputs[i][11]);
            preds[i] = model.predictCoxPH(row).value;
        }

        assertArrayEquals(expected, preds, 0.000001);
    }

    /*
        Test backward compatibility of CoxPH mojo model trained in 3.32.1.3.
        Only using categorical features. 
    */
    @Test
    public void testCoxPHBackwardCompatibilityCatOnly332() throws Exception {
        String mojofile = String.valueOf(
            Paths.get(
                CoxPHMojoModelTest.class.getClassLoader().getResource("hex/genmodel/algos/coxph/CoxPH_bc_catOnly_3_32.zip").toURI()
            ).toFile()
        );

        EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
                .setModel(MojoModel.load(mojofile));
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);

        String [][] inputs = new String[][] {
            {"0", "0"},
            {"0", "1"},
            {"1", "0"},
            {"1", "1"},
        };
        double [] expected = {0.0628001, 0.221134, -0.686394, -0.528061};
        double[] preds = new double[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            RowData row = new RowData();
            row.put("surgery", inputs[i][0]);
            row.put("transplant", inputs[i][1]);
            preds[i] = model.predictCoxPH(row).value;
        }

        assertArrayEquals(expected, preds, 0.000001);
    }
    
    /*
        Test backward compatibility of CoxPH mojo model trained in 3.42.0.4.
        Only using categorical features. 
    */
    @Test
    public void testCoxPHBackwardCompatibilityCatOnly342() throws Exception {
        String mojofile = String.valueOf(
            Paths.get(
                CoxPHMojoModelTest.class.getClassLoader().getResource("hex/genmodel/algos/coxph/CoxPH_bc_catOnly_3_42.zip").toURI()
            ).toFile()
        );

        EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
                .setModel(MojoModel.load(mojofile));
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(config);

        String [][] inputs = new String[][] {
            {"0", "0"},
            {"0", "1"},
            {"1", "0"},
            {"1", "1"},
        };
        double [] expected = {0.0628001, 0.221134, -0.686394, -0.528061};
        double[] preds = new double[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            RowData row = new RowData();
            row.put("surgery", inputs[i][0]);
            row.put("transplant", inputs[i][1]);
            preds[i] = model.predictCoxPH(row).value;
        }

        assertArrayEquals(expected, preds, 0.000001);
    }
}
