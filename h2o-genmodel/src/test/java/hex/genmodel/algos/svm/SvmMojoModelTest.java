package hex.genmodel.algos.svm;

import com.google.common.io.ByteStreams;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;

public class SvmMojoModelTest {

    private MojoModel _mojo;
    private double[][] _rows;
    private RowData[] _rowData;

    private double[] expectedPreds;

    @Before
    public void setup() throws IOException {
        _mojo = SvmMojoReader.readFrom(new SvmMojoModelTest.ClasspathReaderBackend());

        _rows = new double[][] {
                new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                new double[]{1.0, 1.0, 1.0, 1.0, 1.0, 1.0}
        };

        expectedPreds = new double[] {1.0, 0.0};

        _rowData = new RowData[_rows.length];
        for (int i = 0; i < _rows.length; i++)
            _rowData[i] = toRowData(_mojo, _rows[i]);
    }

    @Test
    public void testPredict() throws Exception {
        EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(_mojo);
        for (int i = 0; i < _rows.length; i++) {
            // test easy-predict
            BinomialModelPrediction p = (BinomialModelPrediction) wrapper.predict(_rowData[i]);
            assertEquals((int)expectedPreds[i], p.labelIndex);
            // test score0
            double[] preds = new double[3];
            _mojo.score0(_rows[i], preds);
            assertEquals(expectedPreds[i], preds[0], 0.0);
        }
    }

    private static RowData toRowData(MojoModel mojo, double[] row) {
        RowData rowData = new RowData();
        for (int i = 0; i < mojo._names.length - 1; i++) {
            String name = mojo._names[i];
            int idx = mojo.getColIdx(name);
            String[] domain = mojo.getDomainValues(idx);
            if (domain != null)
                rowData.put(name, domain[(int) row[idx]]);
            else
                rowData.put(name, row[idx]);
        }
        return rowData;
    }

    private static class ClasspathReaderBackend implements MojoReaderBackend {
        @Override
        public BufferedReader getTextFile(String filename) throws IOException {
            InputStream is = SvmMojoModelTest.class.getResourceAsStream(filename);
            return new BufferedReader(new InputStreamReader(is));
        }

        @Override
        public byte[] getBinaryFile(String filename) throws IOException {
            InputStream is = SvmMojoModelTest.class.getResourceAsStream(filename);
            return ByteStreams.toByteArray(is);
        }

        @Override
        public boolean exists(String filename) {
            return true;
        }
    }
}