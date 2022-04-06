package hex.genmodel.algos.isoforextended;

import com.google.common.io.ByteStreams;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.AnomalyDetectionPrediction;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class ExtendedIsolationForestMojoModelTest {

    private ExtendedIsolationForestMojoModel mojo;

    @Before
    public void setup() throws Exception {
        mojo = (ExtendedIsolationForestMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());
        assertNotNull(mojo);
    }

    @Test
    public void testBasic() throws Exception {
        assertNotNull(mojo._compressedTrees);
        assertEquals(mojo._ntrees, mojo._compressedTrees.length);

        double[] row = new double[]{3.0, 3.0};

        RowData data = new RowData();
        for (int i = 0; i< row.length; i++) {
            data.put(mojo._names[i], row[i]);
        }

        double[] rawPrediction = new double[2];
        mojo.score0(row, rawPrediction);

        EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(
                new EasyPredictModelWrapper
                        .Config()
                        .setModel(mojo)
        );

        AnomalyDetectionPrediction prediction = (AnomalyDetectionPrediction) wrapper.predict(data);
        assertNull(prediction.isAnomaly);
        assertEquals(rawPrediction[1], prediction.score, 0);
        assertEquals(rawPrediction[0], prediction.normalizedScore, 0);
        assertNull(prediction.leafNodeAssignments);
        assertNull(prediction.leafNodeAssignmentIds);
        assertNull(prediction.stageProbabilities);
        assertArrayEquals("Outputs of the model is differ from output of wrapper", rawPrediction, prediction.toPreds(), 0);
    }


    private static class ClasspathReaderBackend implements MojoReaderBackend {
        @Override
        public BufferedReader getTextFile(String filename) {
            InputStream is = ExtendedIsolationForestMojoModelTest.class.getResourceAsStream(filename);
            return new BufferedReader(new InputStreamReader(is));
        }

        @Override
        public byte[] getBinaryFile(String filename) throws IOException {
            InputStream is = ExtendedIsolationForestMojoModelTest.class.getResourceAsStream(filename);
            return ByteStreams.toByteArray(is);
        }

        @Override
        public boolean exists(String name) {
            return true;
        }
    }

}
