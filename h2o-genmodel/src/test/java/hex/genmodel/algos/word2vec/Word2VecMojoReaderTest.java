package hex.genmodel.algos.word2vec;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.*;

public class Word2VecMojoReaderTest {

  @Test
  public void readModelData() throws Exception {
    TestedWord2VecMojoReader reader = new TestedWord2VecMojoReader();

    reader.readModelData(false);
    Word2VecMojoModel model = reader.getModel();

    assertArrayEquals(new float[]{0.0f, 1.0f}, model.transform0("A", new float[2]), 0.0001f);
    assertArrayEquals(new float[]{2.0f, 3.0f}, model.transform0("B", new float[2]), 0.0001f);
    assertArrayEquals(new float[]{4.0f, 5.0f}, model.transform0("C", new float[2]), 0.0001f);
  }

  private static class TestedWord2VecMojoReader extends Word2VecMojoReader {

    private TestedWord2VecMojoReader() {
      _model = new Word2VecMojoModel(new String[0], new String[0][], null);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T readkv(String key, T defVal) {
      Object result = null;
      if ("vocab_size".equals(key))
        result = 3;
      else if ("vec_size".equals(key))
        result = 2;
      return (T) result;
    }

    @Override
    protected byte[] readblob(String name) throws IOException {
      byte[] data = new byte[3 * 2 * 4];
      ByteBuffer bb = ByteBuffer.wrap(data);
      for (int i = 0; i < 6; i++)
        bb.putFloat(i);
      return bb.array();
    }

    @Override
    protected boolean exists(String name) {
      return true;
    }

    @Override
    protected Iterable<String> readtext(String name, boolean unescapeNewlines) throws IOException {
      assertTrue(unescapeNewlines);
      return Arrays.asList("A", "B", "C");
    }

    private Word2VecMojoModel getModel() {
      return _model;
    }
  }

}
