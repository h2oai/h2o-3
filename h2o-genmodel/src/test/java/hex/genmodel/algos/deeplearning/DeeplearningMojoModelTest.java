package hex.genmodel.algos.deeplearning;

import com.google.common.io.ByteStreams;
import hex.genmodel.MojoReaderBackend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DeeplearningMojoModelTest {

  public void testPredict() throws Exception {
    System.out.println("Need to get data, predicted output, model and compare the two....");
  }

  private static class ClasspathReaderBackend implements MojoReaderBackend {
    @Override
    public BufferedReader getTextFile(String filename) throws IOException {
      InputStream is = DeeplearningMojoModelTest.class.getResourceAsStream("calibrated/" + filename);
      return new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
      InputStream is = DeeplearningMojoModelTest.class.getResourceAsStream("calibrated/" + filename);
      return ByteStreams.toByteArray(is);
    }

    @Override
    public boolean exists(String name) {
      return true;
    }
  }

}
