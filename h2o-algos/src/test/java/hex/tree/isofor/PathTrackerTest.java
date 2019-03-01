package hex.tree.isofor;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Vec;

import static org.junit.Assert.*;

public class PathTrackerTest extends TestUtil {

  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void encodeNewPathLength() {
    try {
      Scope.enter();
      Vec treeVec = Scope.track(Vec.makeZero(2)).makeVolatileDoubles(1)[0];
      assertEquals(1, treeVec.nChunks());
      Chunk treeChunk = treeVec.chunkForChunkIdx(0);

      assertEquals(0L, PathTracker.encodeNewPathLength(treeChunk, 0,  0, true));
      assertEquals(0L, PathTracker.decodeOOBPathLength(treeChunk, 0));
      assertEquals(7L, PathTracker.encodeNewPathLength(treeChunk, 0,  7, true));
      assertEquals(7L, PathTracker.decodeOOBPathLength(treeChunk, 0));
      assertEquals(49L, PathTracker.encodeNewPathLength(treeChunk, 0,  42, true));
      assertEquals(49L, PathTracker.decodeOOBPathLength(treeChunk, 0));
      assertEquals(63L, PathTracker.encodeNewPathLength(treeChunk, 0,  14, false));
      assertEquals(49L, PathTracker.decodeOOBPathLength(treeChunk, 0));

      assertEquals(12345L+63L, PathTracker.encodeNewPathLength(treeChunk, 0,  12345, false));
      assertEquals(49L, PathTracker.decodeOOBPathLength(treeChunk, 0));

      assertEquals(54321L+12345L+63L, PathTracker.encodeNewPathLength(treeChunk, 0,  54321, true));
      assertEquals(54321L+49L, PathTracker.decodeOOBPathLength(treeChunk, 0));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void encodeNewPathLength_large() {
    try {
      Scope.enter();
      Vec treeVec = Scope.track(Vec.makeZero(1)).makeVolatileDoubles(1)[0];
      assertEquals(1, treeVec.nChunks());
      Chunk treeChunk = treeVec.chunkForChunkIdx(0);

      int total = 0;
      int total_oob = 0;
      for (int i = 0; i < 10000; i++) {
        final boolean wasOOB = i % 3 == 0;
        final int depth = 50 + (i % 50);
        total += depth;
        if (wasOOB)
          total_oob += depth;
        assertEquals(total, PathTracker.encodeNewPathLength(treeChunk, 0, depth, wasOOB));
        assertEquals(total_oob, PathTracker.decodeOOBPathLength(treeChunk, 0));
      }
      assertEquals(745000, total);
      assertEquals(248383, total_oob);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void addNewPathLength() {
    assertEquals(0, PathTracker.addNewPathLength(0, 0, true));
    assertEquals(7L << 31, PathTracker.addNewPathLength(0, 7, true));
    assertEquals((7L << 31) + (42L << 31), PathTracker.addNewPathLength(7L << 31, 42, true));
    assertEquals((7L << 31) + (42L << 31) + 14, PathTracker.addNewPathLength((7L << 31) + (42L << 31), 14, false));
    
    assertEquals(0, PathTracker.addNewPathLength(0, 0, false));
    assertEquals(7, PathTracker.addNewPathLength(0, 7, false));
    assertEquals(49, PathTracker.addNewPathLength(7, 42, false));
    assertEquals((3L << 31) + 49, PathTracker.addNewPathLength(49, 3, true));
  }

  @Test
  public void decodeOOBPathLength() {
    assertEquals(0, PathTracker.decodeOOBPathLength(0));
    assertEquals(7L, PathTracker.decodeOOBPathLength(7L << 31));
    assertEquals(49, PathTracker.decodeOOBPathLength((7L << 31) + (42L << 31)));
    assertEquals(49, PathTracker.decodeOOBPathLength((7L << 31) + (42L << 31) + 14));
    assertEquals(0, PathTracker.decodeOOBPathLength(14));
    assertEquals(0, PathTracker.decodeOOBPathLength(Integer.MAX_VALUE));
    assertEquals(1, PathTracker.decodeOOBPathLength(Integer.MAX_VALUE + 1L));
  }
}
