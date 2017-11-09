package hex.genmodel.algos.ensemble;

import com.google.common.io.ByteStreams;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import hex.genmodel.easy.prediction.RegressionModelPrediction;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class StackedEnsembleBinomialMojoTest {

    @Test
    public void testPredictBinomialProstate() throws Exception {

        StackedEnsembleMojoModel mojo = (StackedEnsembleMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());
        assertNotNull(mojo);

        EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(mojo);

        BinomialModelPrediction pred = (BinomialModelPrediction) wrapper.predict(new RowData() {{
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

        System.out.println("Has penetrated the prostatic capsule (1=yes; 0=no): " + pred.label);
        System.out.print("Class probabilities: ");
        for (int i = 0; i < pred.classProbabilities.length; i++) {
            if (i > 0) {
                System.out.print(",");
            }
            System.out.print(pred.classProbabilities[i]);
        }
        System.out.println("");
    }

    private static class ClasspathReaderBackend implements MojoReaderBackend {
        @Override
        public BufferedReader getTextFile(String filename) throws IOException {
            InputStream is = StackedEnsembleBinomialMojoTest.class.getResourceAsStream("binomial/" + filename);
            return new BufferedReader(new InputStreamReader(is));
        }

        @Override
        public byte[] getBinaryFile(String filename) throws IOException {
            InputStream is = StackedEnsembleBinomialMojoTest.class.getResourceAsStream("binomial/" + filename);
            return ByteStreams.toByteArray(is);
        }

        @Override
        public boolean exists(String name) {
            return true;
        }
    }

}