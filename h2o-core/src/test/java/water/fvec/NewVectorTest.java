package water.fvec;

import static org.junit.Assert.*;
import org.junit.*;

import water.*;

public class NewVectorTest extends TestUtil {
  public NewVectorTest() { super(3); }

  static final double EPSILON = 1e-6;

  private void testImpl( long[] ls, int[] xs, Class C, boolean hasFloat ) {
    int [] id = new int[xs.length];
    for(int i = 0; i < xs.length; ++i)id[i] = i;
    testImpl(ls,xs,id,C,hasFloat);
  }
  private void testImpl( long[] ls, int[] xs, int [] id, Class C, boolean hasFloat ) {
    Vec vec = null;
    try {
      AppendableVec av = new AppendableVec(Vec.newKey());
      NewChunk nv = new NewChunk(av,0, ls, xs, id, null);
      Chunk bv = nv.compress();
      Futures fs = new Futures();
      vec = bv._vec = av.close(fs);
      fs.blockForPending();
      // Compression returns the expected compressed-type:
      assertTrue( "Found chunk class "+bv.getClass()+" but expected "+C, C.isInstance(bv) );
      // Also, we can decompress correctly
      for( int i=0; i<ls.length; i++ )
        assertEquals(bv.atd(i), ls[i]*water.util.PrettyPrint.pow10(xs[i]), Math.abs(bv.atd(i))*EPSILON);
    } finally {
      if( vec != null ) vec.remove();
    }
  }
  // Test that various collections of parsed numbers compress as expected.
  @Test public void testCompression() {
    // A simple no-compress
    testImpl(new long[] {120, 12,120},
             new int [] {  0,  1,  0},
             C0LChunk.class,false);
    // A simple no-compress
    testImpl(new long[] {122, 3,44},
             new int [] {  0, 0, 0},
             C1NChunk.class,false);
    // A simple compressed boolean vector
    testImpl(new long[] {1, 0, 1},
             new int [] {0, 0, 0},
             CBSChunk.class,false);
    // Scaled-byte compression
    testImpl(new long[] {122,-3,44}, // 12.2, -3.0, 4.4 ==> 122e-1, -30e-1, 44e-1
             new int [] { -1, 0,-1},
             C1SChunk.class, true);
    // Positive-scale byte compression
    testImpl(new long[] {1000,200,30}, // 1000, 2000, 3000 ==> 1e3, 2e3, 3e3
             new int [] {   0,  1, 2},
             C1SChunk.class,false);
    // A simple no-compress short
    testImpl(new long[] {1000,200,32767, -32767,32},
             new int [] {   0,  1,    0,      0, 3},
             C2Chunk.class,false);
    // Scaled-byte compression
    testImpl(new long[] {50100,50101,50123,49999}, // 50100, 50101, 50123, 49999
             new int [] {    0,    0,    0,    0},
             C1SChunk.class,false);
    // Scaled-byte compression
    testImpl(new long[] {51000,50101,50123,49999}, // 51000, 50101, 50123, 49999
             new int [] {    0,    0,    0,    0},
             C2SChunk.class,false);
    // Scaled-short compression
    testImpl(new long[] {501000,501001,50123,49999}, // 50100.0, 50100.1, 50123, 49999
             new int [] {    -1,    -1,    0,    0},
             C2SChunk.class, true);
    // Integers
    testImpl(new long[] {123456,2345678,34567890},
             new int [] {     0,      0,       0},
             C4Chunk.class,false);
//    // Floats
    testImpl(new long[] {1234,2345,314},
             new int [] {  -1,  -5, -2},
             C4SChunk.class, true);
    // Doubles
    testImpl(new long[] {1234,2345678,31415},
             new int [] {  40,     10,  -40},
             C8DChunk.class, true);
    testImpl(new long[] {-581504,-477862,342349},
             new int[]  {-5,-18,-5},
             C8DChunk.class,true);
  }

  // Testing writes to an existing Chunk causing inflation
  @Test public void testWrites() {
    Vec vec = null;
    try {
      Futures fs = new Futures();
      AppendableVec av = new AppendableVec(Vec.newKey());
      long ls[] = new long[]{0,0,0,0}; // A 4-row chunk
      int xs[] = new int[]{0,0,0,0}; // A 4-row chunk
      NewChunk nv = new NewChunk(av,0,ls,xs,null,null);
      nv.close(0,fs);
      vec = av.close(fs);
      fs.blockForPending();
      assertEquals(nv._len, vec.length());
      // Compression returns the expected constant-compression-type:
      Chunk c0 = vec.chunkForChunkIdx(0);
      assertTrue( "Found chunk class "+c0.getClass()+" but expected C0LChunk", c0 instanceof C0LChunk );
      // Also, we can decompress correctly
      for( int i=0; i<ls.length; i++ )
        assertEquals(0, c0.atd(i), c0.atd(i)*EPSILON);

      // Now write a zero into slot 0
      vec.set(0,0);
      assertEquals(vec.at8(0),0);
      Chunk c1 = vec.chunkForChunkIdx(0);
      assertTrue( "Found chunk class "+c1.getClass()+" but expected C0LChunk", c1 instanceof C0LChunk );

      // Now write a one into slot 1; chunk should inflate into boolean vector.
      vec.set(1, 1);
      assertEquals(vec.at8(1),1); // Immediate visibility in current thread
      Chunk c2 = vec.chunkForChunkIdx(0);  // Look again at the installed chunk
      assertTrue( "Found chunk class "+c2.getClass()+" but expected CBSChunk", c2 instanceof CBSChunk );

      // Now write a two into slot 2; chunk should inflate into byte vector
      vec.set(2, 2);
      assertEquals(vec.at8(2),2); // Immediate visibility in current thread
      Chunk c3 = vec.chunkForChunkIdx(0);  // Look again at the installed chunk
      assertTrue( "Found chunk class "+c3.getClass()+" but expected C1NChunk", c3 instanceof C1NChunk );

      vec.set(3, 3);
      assertEquals(vec.at8(3),3); // Immediate visibility in current thread
      Chunk c4 = vec.chunkForChunkIdx(0);  // Look again at the installed chunk
      assertTrue("Found chunk class " + c4.getClass() + " but expected C1NChunk", c4 instanceof C1NChunk);

      // Now doing the same for multiple writes, close() only at the end for better speed
      try (Vec.Writer vw = vec.open()) {
        vw.set(1, 4);
        vw.set(2, 5);
        vw.set(3, 6);
        // Updates will be immediately visible on the writing node
      }
      // now, after vw.close(), numbers are consistent across the H2O cloud
      assertEquals(vec.at8(1),4);
      assertEquals(vec.at8(2),5);
      assertEquals(vec.at8(3),6);
    } finally {
      if( vec != null ) vec.remove();
    }
  }
}
