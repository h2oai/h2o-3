package hex.genmodel.algos.gbm;

import com.google.common.io.ByteStreams;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.algos.word2vec.Word2VecMojoModelTest;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import static org.junit.Assert.*;

public class GbmMojoModelTest {

  @Test
  public void testPredict() throws Exception {

    GbmMojoModel mojo = (GbmMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());
    assertNotNull(mojo);

    EasyPredictModelWrapper wrapper = new EasyPredictModelWrapper(mojo);

    BinomialModelPrediction pred = (BinomialModelPrediction) wrapper.predict(new RowData() {{
      put("SegSumT", 18.7);
      put("SegTSeas", 1.51);
      put("SegLowFlow", 1.003);
      put("DSDist", 132.53);
      put("DSMaxSlope", 1.15);
      put("USAvgT", 0.2);
      put("USRainDays", 1.153);
      put("USSlope", 8.3);
      put("USNative", 0.34);
      put("DSDam", 0.0);
      put("Method", "electric");
    }});

    assertEquals(1, pred.labelIndex);
    assertEquals("1", pred.label);
    assertArrayEquals(new double[]{0.5416688, 0.4583312}, pred.classProbabilities, 1e-5);
    assertArrayEquals(new double[]{0.3920402, 0.6079598}, pred.calibratedClassProbabilities, 1e-5);
  }

  private static class ClasspathReaderBackend implements MojoReaderBackend {
    @Override
    public BufferedReader getTextFile(String filename) throws IOException {
      InputStream is = GbmMojoModelTest.class.getResourceAsStream("calibrated/" + filename);
      return new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
      InputStream is = GbmMojoModelTest.class.getResourceAsStream("calibrated/" + filename);
      return ByteStreams.toByteArray(is);
    }

    @Override
    public boolean exists(String name) {
      return true;
    }
  }

}