package hex.genmodel.algos.ensemble;

import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.MultinomialModelPrediction;
import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.*;

public class StackedEnsembleMultinomialMojoTest {

    @Test
    public void testPredictMultinomialProstate() throws Exception {
        URL mojoSource = StackedEnsembleRegressionMojoTest.class.getResource("multinomial.zip");
        assertNotNull(mojoSource);
        System.out.println(mojoSource);
        MojoReaderBackend reader = MojoReaderBackendFactory.createReaderBackend(mojoSource, MojoReaderBackendFactory.CachingStrategy.DISK);
        MojoModel model = ModelMojoReader.readFrom(reader);
        EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);

       MultinomialModelPrediction pred = (MultinomialModelPrediction) modelWrapper.predict(new RowData() {{
            put("CAPSULE", "0");
            put("AGE", "65");
            put("DPROS", "2");
            put("DCAPS", "1");
            put("PSA", "1.4");
            put("VOL", "0");
            put("GLEASON", "6");
        }});

        assertEquals(1, pred.labelIndex);
        assertEquals("1", pred.label);
        assertArrayEquals(new double[]{0.006592327, 0.901237, 0.09217069}, pred.classProbabilities, 1e-5);
    }
}