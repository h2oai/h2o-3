package hex.word2vec;

import org.junit.Test;
import water.TestBase;

import static org.junit.Assert.*;

public class HBWTreeTest extends TestBase {

  @Test
  public void buildHuffmanBinaryWordTree() throws Exception {
    HBWTree t = HBWTree.buildHuffmanBinaryWordTree(new long[] {1, 2, 3});
    assertNotNull(t);
  }

}
