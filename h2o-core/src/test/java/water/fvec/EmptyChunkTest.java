package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

import static water.fvec.FrameTestUtil.createFrame;

/**
 * Testing empty chunks.
 *
 * This test simulates workflow used from Sparkling Water.
 * But it also serves to test chunk layouts with 0-length chunks.
 *
 */
public class EmptyChunkTest extends TestUtil {
  @BeforeClass
  static public void setup() {  stall_till_cloudsize(1); }

  /**
   * Scenario: chunk(2-rows)|chunk(0 rows)|chunk(2-rows)|chunk(0 rows)
   */
  @Test
  public void testEmptyChunk0() {
    String fname = "test0.hex";
    long[] chunkLayout = ar(2L, 0L, 2L, 0L);
    testScenario(fname, chunkLayout);
  }

  /**
   * Scenario: chunk(0-rows)|chunk(0 rows)|chunk(0-rows)|chunk(2 rows)
   */
  @Test
  public void testEmptyChunk1() {
    String fname = "test1.hex";
    long[] chunkLayout = ar(0L, 0L, 0L, 2L);
    testScenario(fname, chunkLayout);
  }

  /**
   * Scenario: chunk(2-rows)|chunk(0 rows)|chunk(0-rows)|chunk(2 rows)
   */
  @Test
  public void testEmptyChunk2() {
    String fname = "test2.hex";
    long[] chunkLayout = ar(2L, 0L, 0L, 2L);
    testScenario(fname, chunkLayout);
  }

  private void testScenario(String fname, long[] chunkLayout) {
    int numberOfChunks = chunkLayout.length;
    // Frame for testing
    Frame f = createFrame(fname, chunkLayout);

    try {
      Vec vec = f.vec(0);
      long[] chunkLens = chunkLayout;
      assertChunkInvariants(vec, numberOfChunks, chunkLens);
    } finally {
      // Cleanup
      f.delete();
    }
  }

  private void assertChunkInvariants(Vec vec, int numOfChunks, long[] chunkLens) {
    assert numOfChunks == chunkLens.length : "ups wrong test setup";
    long[] espc = new long[numOfChunks+1];
    for (int i=0; i<numOfChunks; i++) {
      espc[i+1] = espc[i] + chunkLens[i];
    }
    Assert.assertArrayEquals("Vector espc is wrong!", espc, vec.espc());
    Assert.assertEquals("Number of chunks in vec is wrong!", numOfChunks, vec.nChunks());

    for (int i=0; i<numOfChunks; i++) {
      Chunk c = vec.chunkForChunkIdx(i);
      Assert.assertEquals("Chunk index is wrong!", i, c.cidx());
      Assert.assertEquals("Chunk len is wrong!", chunkLens[i], c.len());
      Assert.assertEquals("Chunk start is wrong!", espc[i], c.start());
    }
  }
}
