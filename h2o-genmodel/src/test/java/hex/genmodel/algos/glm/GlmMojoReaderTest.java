package hex.genmodel.algos.glm;

import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoReaderBackend;
import org.junit.Test;

import java.io.*;

import static org.junit.Assert.*;

public class GlmMojoReaderTest {

  @Test
  public void readModelData() throws Exception {
    GlmMojoModel model = (GlmMojoModel) ModelMojoReader.readFrom(new MojoReaderBackend() {
      @Override public BufferedReader getTextFile(String filename) throws IOException {
        if ("model.ini".equals(filename)) {
          InputStream is = GlmMojoReaderTest.class.getResourceAsStream("model.ini");
          return new BufferedReader(new InputStreamReader(is));
        } else {
          return new BufferedReader(new StringReader("d1\nd2\nd3\n"));
        }
      }
      @Override public byte[] getBinaryFile(String filename) throws IOException {
        throw new UnsupportedOperationException("Not expected");
      }

      @Override
      public boolean exists(String name) {
        throw new UnsupportedOperationException("Not expected");
      }
    });
    assertTrue(model._useAllFactorLevels);
    assertEquals(1, model._cats);
    assertArrayEquals(new int[]{2}, model._catModes);
    assertArrayEquals(new int[]{0, 3}, model._catOffsets);
    assertEquals(7, model._nums);
    assertArrayEquals(new double[]{0.5239, 0.4078, 0.1395, 0.8287, 0.35936, 0.1805, 0.2388}, model._numMeans, 0.001);
    assertTrue(model._meanImputation);
    assertArrayEquals(new double[]{0.1165, -0.7141, 0.1690, 0.0, 10.3898, 10.8657, 7.2552, -17.9726, -8.7421, 10.6995, 3.7805}, model._beta, 0.001);
    assertEquals("gaussian", model._family);
    assertEquals("identity", model._link);
  }

  @Test
  public void makeMultinomialModel() throws Exception {
    final String[] cols = new String[] {"a", "b"};
    final String[][] domain = new String[][]{};
    GlmMojoModelBase model = new GlmMojoReader() {
      @Override @SuppressWarnings("unchecked")
      protected String readkv(String key) {
        if (! "family".equals(key)) throw new UnsupportedOperationException("Unexpected property: " + key);
        return "multinomial";
      }
    }.makeModel(cols, domain, "c");
    assertTrue(model instanceof GlmMultinomialMojoModel);
    assertTrue(cols == model._names);
    assertTrue(domain == model._domains);
  }

}
