package hex.genmodel.algos.klime;

import com.google.common.io.ByteStreams;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.algos.glm.GlmMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.KLimeModelPrediction;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class KLimeMojoModelTest {

  private KLimeMojoModel _mojo;
  private double[][] _rows;
  private RowData[] _rowData;

  @Before
  public void setup() throws IOException {
    _mojo = (KLimeMojoModel) KLimeMojoReader.readFrom(new KLimeMojoModelTest.ClasspathReaderBackend());
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
  public void testScore0() throws Exception {
    EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(_mojo);

    double[] preds = new double[7];
    KLimeModelPrediction p;

    // prediction is made by a cluster-local GLM model
    p = wrapper.predictKLime(_rowData[0]);
    checkPrediction(p, _mojo);
    _mojo.score0(_rows[0], preds);
    assertEquals(preds[0], 0.127, 0.001);
    assertEquals(preds[1], 0, 0.0);
    checkPrediction(preds, _mojo);

    // data point belongs to cluster 1 but prediction is made by a global model
    p = wrapper.predictKLime(_rowData[1]);
    checkPrediction(p, _mojo);
    _mojo.score0(_rows[1], preds);
    assertEquals(preds[0], 0.141, 0.001);
    assertEquals(preds[1], 1, 0.0);
    checkPrediction(preds, _mojo);

    // data point belongs to cluster 2 but prediction is made by a global model
    p = wrapper.predictKLime(_rowData[2]);
    checkPrediction(p, _mojo);
    _mojo.score0(_rows[2], preds);
    assertEquals(preds[0], 0.596, 0.001);
    assertEquals(preds[1], 2, 0.0);
    checkPrediction(preds, _mojo);
  }

  private void checkPrediction(double[] preds, KLimeMojoModel mojo) {
    GlmMojoModel m = mojo.getRegressionModel((int) preds[1]);
    double p = m.getIntercept();
    for (int i = 2; i < preds.length; i++)
      p += preds[i];
    assertEquals(preds[0], p, 1e-6);
  }

  private void checkPrediction(KLimeModelPrediction pred, KLimeMojoModel mojo) {
    GlmMojoModel m = mojo.getRegressionModel(pred.cluster);
    double p = m.getIntercept();
    for (int i = 0; i < pred.reasonCodes.length; i++)
      p += pred.reasonCodes[i];
    assertEquals(pred.value, p, 1e-6);
  }

  private static RowData toRowData(MojoModel mojo, double[] row) {
    RowData rowData = new RowData();
    for (String name : mojo._names) {
      int idx = mojo.getColIdx(name);
      if (idx >= row.length)
        continue;
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
      InputStream is = KLimeMojoModelTest.class.getResourceAsStream(filename);
      return new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
      InputStream is = KLimeMojoModelTest.class.getResourceAsStream(filename);
      return ByteStreams.toByteArray(is);
    }

    @Override
    public boolean exists(String filename) {
      return true;
    }
  }

}
