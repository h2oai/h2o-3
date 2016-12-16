package hex.word2vec;

import org.junit.Test;

import static org.junit.Assert.*;

public class HBWTreeTest {

  @Test
  public void buildHuffmanBinaryWordTree() throws Exception {
    HBWTree t = HBWTree.buildHuffmanBinaryWordTree(new long[] {1, 2, 3});
    assertNotNull(t);
  }

}