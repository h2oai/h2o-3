package hex.genmodel.algos.isofor;

import com.google.common.io.ByteStreams;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.AnomalyDetectionPrediction;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

public class IsolationForestMojoModelTest {

    private IsolationForestMojoModel mojo;

    @Before
    public void setup() throws Exception {
        mojo = (IsolationForestMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());
        assertNotNull(mojo);
    }


    @Test
    public void testLeafNodeAssignments() throws Exception {
        double[] doubles = new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
        SharedTreeMojoModel.LeafNodeAssignments res = mojo.getLeafNodeAssignments(doubles);
        assertNotNull(res._nodeIds);
        assertNotNull(res._paths);
        String[] paths = mojo.getDecisionPath(doubles);
        assertArrayEquals(paths, res._paths);
        RowData data = new RowData();
        for (int i = 0; i< doubles.length; i++) data.put(mojo._names[i], doubles[i]);
        EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(
            new EasyPredictModelWrapper.Config().setModel(mojo).setEnableLeafAssignment(true)
        );
        AnomalyDetectionPrediction res2 = (AnomalyDetectionPrediction) wrapper.predict(data);
        assertNotNull(res2.leafNodeAssignmentIds);
        assertNotNull(res2.leafNodeAssignments);
        assertArrayEquals(res._nodeIds, res2.leafNodeAssignmentIds);
        assertArrayEquals(res._paths, res2.leafNodeAssignments);
    }


    private static class ClasspathReaderBackend implements MojoReaderBackend {
        @Override
        public BufferedReader getTextFile(String filename) {
            InputStream is = IsolationForestMojoModelTest.class.getResourceAsStream(filename);
            return new BufferedReader(new InputStreamReader(is));
        }

        @Override
        public byte[] getBinaryFile(String filename) throws IOException {
            InputStream is = IsolationForestMojoModelTest.class.getResourceAsStream(filename);
            return ByteStreams.toByteArray(is);
        }

        @Override
        public boolean exists(String name) {
            return true;
        }
    }

}
