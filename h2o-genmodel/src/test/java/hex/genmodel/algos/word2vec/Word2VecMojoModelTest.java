package hex.genmodel.algos.word2vec;

import com.google.common.io.ByteStreams;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.junit.Assert.*;

public class Word2VecMojoModelTest {

  @Test
  public void testTransform0() throws Exception {
    MojoModel mojo = Word2VecMojoReader.readFrom(new Word2VecMojoModelTest.ClasspathReaderBackend());

    assertTrue(mojo instanceof WordEmbeddingModel);
    WordEmbeddingModel m = (WordEmbeddingModel) mojo;

    assertEquals(3, m.getVecSize());
    assertArrayEquals(new float[]{0.0f,1.0f,0.2f}, m.transform0("a", new float[3]), 0.0001f);
    assertArrayEquals(new float[]{1.0f,0.0f,0.8f}, m.transform0("b", new float[3]), 0.0001f);
    assertNull(m.transform0("c", new float[3])); // out-of-dictionary word
  }

  private static class ClasspathReaderBackend implements MojoReaderBackend {
    @Override
    public BufferedReader getTextFile(String filename) throws IOException {
      InputStream is = Word2VecMojoModelTest.class.getResourceAsStream(filename);
      return new BufferedReader(new InputStreamReader(is));
    }

    @Override
    public byte[] getBinaryFile(String filename) throws IOException {
      InputStream is = Word2VecMojoModelTest.class.getResourceAsStream(filename);
      return ByteStreams.toByteArray(is);
    }

    @Override
    public boolean exists(String filename) {
      return true;
    }
  }

}
