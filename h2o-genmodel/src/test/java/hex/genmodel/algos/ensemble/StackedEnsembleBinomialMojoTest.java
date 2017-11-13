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
}