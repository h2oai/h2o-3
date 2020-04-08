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

public class StackedEnsembleBinomialWithoutUselessModelsMojoTest {

    @Test
    public void testPredictBinomialProstateWithoutUselessBaseModels() throws Exception {
        URL mojoSource = StackedEnsembleRegressionMojoTest.class.getResource("binomial_without_useless_models.zip");
        assertNotNull(mojoSource);
        System.out.println(mojoSource);
        MojoReaderBackend reader = MojoReaderBackendFactory.createReaderBackend(mojoSource, MojoReaderBackendFactory.CachingStrategy.DISK);
        StackedEnsembleMojoModel model = (StackedEnsembleMojoModel)ModelMojoReader.readFrom(reader);
        int usefulBaseModel = 6;

        for (int i = 0; i < model._baseModelNum; i++) {
            if (i == usefulBaseModel) {
                assertNotNull(model._baseModels[i]);
            } else {
                assertNull(model._baseModels[i]);
            }
        }

        EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);
        BinomialModelPrediction pred = (BinomialModelPrediction) modelWrapper.predict(new RowData() {{
            put("AGE", "65");
        }});

        assertEquals(1, pred.labelIndex);
        assertEquals("1", pred.label);
    }

}
