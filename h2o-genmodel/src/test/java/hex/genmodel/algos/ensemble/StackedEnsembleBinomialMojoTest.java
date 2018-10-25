package hex.genmodel.algos.ensemble;

import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.BinomialModelPrediction;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.*;

public class StackedEnsembleBinomialMojoTest {

    @Test
    public void testPredictBinomialProstate() throws Exception {
        URL mojoSource = StackedEnsembleRegressionMojoTest.class.getResource("binomial.zip");
        assertNotNull(mojoSource);
        System.out.println(mojoSource);
        MojoReaderBackend reader = MojoReaderBackendFactory.createReaderBackend(mojoSource, MojoReaderBackendFactory.CachingStrategy.DISK);
        MojoModel model = ModelMojoReader.readFrom(reader);
        EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);

        BinomialModelPrediction pred = (BinomialModelPrediction) modelWrapper.predict(new RowData() {{
            put("AGE", "65");
            put("RACE", "1");
            put("DPROS", "2");
            put("DCAPS", "1");
            put("PSA", "1.4");
            put("VOL", "0");
            put("GLEASON", "6");
        }});

        assertEquals(0, pred.labelIndex);
        assertEquals("0", pred.label);
        assertArrayEquals(new double[]{0.8222695, 0.1777305}, pred.classProbabilities, 1e-5);
    }

    @Test
    public void testPredictWithRowReordering() throws Exception {
        URL mojoSource = StackedEnsembleRegressionMojoTest.class.getResource("binomial_titanic.zip");
        assertNotNull(mojoSource);
        MojoReaderBackend reader = MojoReaderBackendFactory.createReaderBackend(mojoSource, MojoReaderBackendFactory.CachingStrategy.DISK);
        MojoModel model = ModelMojoReader.readFrom(reader);
        assertTrue(model instanceof StackedEnsembleMojoModel);
        EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);

        final RowData rowData = new RowData() {{
            put("pclass", 1D);
            put("survived", 1D);
            put("name", "Allison, Master. Hudson Trevor");
            put("sex", "male");
            put("age", 0.9167);
            put("sibsp", 1D);
            put("parch", 2D);
            put("ticket", 113781D);
            put("fare", 151.5500D);
            put("cabin", "C22 C26");
            put("embarked", "S");
            put("boat", 11D);
            put("body", Double.NaN);
            put("home.dest", "Montreal, PQ / Chesterville, ON");
        }};

        BinomialModelPrediction pred = (BinomialModelPrediction) modelWrapper.predict(rowData);
        assertNotNull(pred);
        assertFalse(pred.label.isEmpty());
    }

    @Test
    public void testStackedEnsembleMojoSubModel() throws Exception {
        URL mojoSource = StackedEnsembleRegressionMojoTest.class.getResource("binomial_titanic.zip");
        assertNotNull(mojoSource);
        MojoReaderBackend reader = MojoReaderBackendFactory.createReaderBackend(mojoSource, MojoReaderBackendFactory.CachingStrategy.DISK);
        StackedEnsembleMojoModel model = (StackedEnsembleMojoModel) ModelMojoReader.readFrom(reader);


        final StackedEnsembleMojoModel.StackedEnsembleMojoSubModel subModel =
                new StackedEnsembleMojoModel.StackedEnsembleMojoSubModel(model._baseModels[0]._mojoModel,
                        null);
        assertNull(subModel._mapping);
        assertNotNull(subModel._mojoModel);
        double[] originalRow = new double[]{1, 2, 3};
        final double[] remappedRow = subModel.remapRow(originalRow);
        
        assertNotEquals(originalRow, remappedRow); // Resulting remapped row should not be a reference to the same array
        assertEquals(originalRow.length, remappedRow.length);
        for (int i = 0; i < originalRow.length; i++) {
            assertEquals(originalRow[i], remappedRow[i], 0);
        }
    }
}
