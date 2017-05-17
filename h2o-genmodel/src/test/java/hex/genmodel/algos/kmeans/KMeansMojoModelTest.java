package hex.genmodel.algos.kmeans;

import com.google.common.io.ByteStreams;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.ClusteringModelPrediction;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class KMeansMojoModelTest {

  private MojoModel _mojo;
  private double[][] _rows;
  private RowData[] _rowData;

  @Before
  public void setup() throws IOException {
    _mojo = KMeansMojoReader.readFrom(new KMeansMojoModelTest.ClasspathReaderBackend());
    _rows = new double[][] {
            new double[]{2.0, 1.0, 22.0, 1.0, 0.0},
            new double[]{2.0, 1.0, 2.0, 3.0, 1.0},
            new double[]{2.0, 0.0, 27.0, 0.0, 2.0}
    };
    _rowData = new RowData[_rows.length];
    for (int i = 0; i < _rows.length; i++)
      _rowData[i] = toRowData(_mojo, _rows[i]);
  }

  @Test
  public void testPredict() throws Exception {
    EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(_mojo);
    for (int i = 0; i < 3; i++) {
      // test easy-predict
      ClusteringModelPrediction p = (ClusteringModelPrediction) wrapper.predict(_rowData[i]);
      assertEquals(i, p.cluster);
      // test score0
      double[] preds = new double[1];
      _mojo.score0(_rows[i], preds);
      assertEquals(i, preds[0], 0.0);
    }
  }

  private static RowData toRowData(MojoModel mojo, double[] row) {
    RowData rowData = new RowData();
    for (String name : mojo._names) {
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
      InputStream is = KMeansMojoModelTest.class.getResourceAsStream(filename);
      return new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
      InputStream is = KMeansMojoModelTest.class.getResourceAsStream(filename);
      return ByteStreams.toByteArray(is);
    }

    @Override
    public boolean exists(String filename) {
      return true;
    }
  }

}