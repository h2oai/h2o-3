package hex.genmodel.algos.ensemble;

import hex.genmodel.*;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.RegressionModelPrediction;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.*;

public class StackedEnsembleRegressionMojoTest {

    @Test
    public void testPredictRegressionProstate() throws Exception {
        URL mojoSource = StackedEnsembleRegressionMojoTest.class.getResource("regression.zip");
        assertNotNull(mojoSource);
        System.out.println(mojoSource);
        MojoReaderBackend reader = MojoReaderBackendFactory.createReaderBackend(mojoSource, MojoReaderBackendFactory.CachingStrategy.DISK);
        MojoModel model = ModelMojoReader.readFrom(reader);
        EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(model);

        RegressionModelPrediction pred = (RegressionModelPrediction) modelWrapper.predict(new RowData() {{
            put("CAPSULE", "0");
            put("RACE", "1");
            put("DPROS", "2");
            put("DCAPS", "1");
            put("PSA", "1.4");
            put("VOL", "0");
            put("GLEASON", "6");
        }});

        assertEquals(66.29695, pred.value, 1e-5);
    }

}