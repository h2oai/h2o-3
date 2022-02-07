package hex.genmodel.algos.isoforextended;

import com.google.common.io.ByteStreams;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.algos.isofor.IsolationForestMojoModel;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.AnomalyDetectionPrediction;
import hex.genmodel.utils.ArrayUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

public class ExtendedIsolationForestMojoModelTest {

    private ExtendedIsolationForestMojoModel mojo;

    @Before
    public void setup() throws Exception {
        mojo = (ExtendedIsolationForestMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());
        assertNotNull(mojo);
    }


    @Test
    public void testBasic() throws Exception {
        System.out.println("mojo._algoName = " + mojo._algoName);
        System.out.println("mojo._ntrees = " + mojo._ntrees);
        System.out.println("mojo score = " + Arrays.toString(mojo.score0(new double[]{3.0, 3.0}, ArrayUtils.nanArray(2))));
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
