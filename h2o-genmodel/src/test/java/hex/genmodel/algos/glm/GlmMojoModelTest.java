package hex.genmodel.algos.glm;

import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class GlmMojoModelTest {

  @Test
  public void testScore0() throws Exception {
    double[][] data = new double[][]{
            new double[]{2,73,2,1,7.9,18,6},
            new double[]{1,51,3,1,8.9,0,6},
            new double[]{2,57,3,1,3.4,30.8,6},
            new double[]{1,65,4,1,6.3,0,6},
            new double[]{1,61,3,1,1.5,0,5},
            new double[]{1,56,2,2,58,0,6},
            new double[]{1,72,2,1,1.4,24.2,6},
            new double[]{1,54,2,1,18,43,9},
            new double[]{1,62,2,1,7.3,0,7},
            new double[]{2,63,3,1,14.3,16,7},
            new double[]{1,68,1,1,5.4,34,5},
            new double[]{1,Double.NaN,1,1,5.4,34,5} // value should be imputed
    };

    double[][] expPreds = new double[][]{
            new double[]{0.0, 0.883740206424754, 0.11625979357524593},
            new double[]{1.0, 0.5591006829867439, 0.44089931701325613},
            new double[]{0.0, 0.8200793110208472, 0.1799206889791528},
            new double[]{1.0, 0.4855023555733662, 0.5144976444266338},
            new double[]{0.0, 0.8260781970262484, 0.17392180297375157},
            new double[]{1.0, 0.2685796973779421, 0.7314203026220579},
            new double[]{0.0, 0.8265057623033865, 0.1734942376966135},
            new double[]{1.0, 0.1332488800455477, 0.8667511199544523},
            new double[]{1.0, 0.5038183003787983, 0.49618169962120173},
            new double[]{1.0, 0.5384202639029669, 0.46157973609703307},
            new double[]{0.0, 0.9543248143434919, 0.04567518565650803},
            new double[]{0.0, 0.9531416700165544, 0.046858329983445586}
    };

    GlmMojoModel mojo = (GlmMojoModel) ModelMojoReader.readFrom(new ClasspathReaderBackend());

    for (int i = 0; i < data.length; i++) {
      double preds[] = mojo.score0(data[i], new double[3]);
      assertArrayEquals("Predictions for row #" + i, expPreds[i], preds, 0.0000001);
    }
  }

  private static class ClasspathReaderBackend implements MojoReaderBackend {
    @Override
    public BufferedReader getTextFile(String filename) throws IOException {
      InputStream is = GlmMojoModelTest.class.getResourceAsStream("prostate/" + filename);
      return new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
      throw new UnsupportedOperationException("Unexpected call to getBinaryFile()");
    }

    @Override
    public boolean exists(String name) {
      throw new UnsupportedOperationException("Unexpected call to exists()");
    }
  }

}