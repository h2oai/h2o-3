package hex.genmodel.algos.ensemble;

import com.google.common.io.ByteStreams;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.MultinomialModelPrediction;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class StackedEnsembleMultinomialMojoTest {

    @Test
    public void testPredictMultinomialProstate() throws Exception {

        StackedEnsembleMojoModel mojo = (StackedEnsembleMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());
        assertNotNull(mojo);

        EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(mojo);

       MultinomialModelPrediction pred = (MultinomialModelPrediction) wrapper.predict(new RowData() {{
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

        System.out.println("Has RACE: " + pred.label);
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
            InputStream is = StackedEnsembleMultinomialMojoTest.class.getResourceAsStream("multinomial/" + filename);
            return new BufferedReader(new InputStreamReader(is));
        }

        @Override
        public byte[] getBinaryFile(String filename) throws IOException {
            InputStream is = StackedEnsembleMultinomialMojoTest.class.getResourceAsStream("multinomial/" + filename);
            return ByteStreams.toByteArray(is);
        }

        @Override
        public boolean exists(String name) {
            return true;
        }
    }

}