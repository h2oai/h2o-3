package hex.genmodel.algos.ensemble;

import com.google.common.io.ByteStreams;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.RegressionModelPrediction;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class StackedEnsembleRegressionMojoTest {

    @Test
    public void testPredictRegressionProstate() throws Exception {

        StackedEnsembleMojoModel mojo = (StackedEnsembleMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());
        assertNotNull(mojo);

        EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(mojo);

        RegressionModelPrediction pred = (RegressionModelPrediction) wrapper.predict(new RowData() {{
            put("CAPSULE", "0");
            put("RACE", "1");
            put("DPROS", "2");
            put("DCAPS", "1");
            put("PSA", "1.4");
            put("VOL", "0");
            put("GLEASON", "6");
        }});

        assertEquals(66.29695, pred.value, 1e-5);

        System.out.println("Predicted AGE: " + pred.value);
    }

    private static class ClasspathReaderBackend implements MojoReaderBackend {
        @Override
        public BufferedReader getTextFile(String filename) throws IOException {
            InputStream is = StackedEnsembleRegressionMojoTest.class.getResourceAsStream("regression/" + filename);
            return new BufferedReader(new InputStreamReader(is));
        }

        @Override
        public byte[] getBinaryFile(String filename) throws IOException {
            InputStream is = StackedEnsembleRegressionMojoTest.class.getResourceAsStream("regression/" + filename);
            return ByteStreams.toByteArray(is);
        }

        @Override
        public boolean exists(String name) {
            return true;
        }
    }

}