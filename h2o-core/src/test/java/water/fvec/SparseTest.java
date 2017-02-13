package water.fvec;

import static org.junit.Assert.*;
import org.junit.*;

import java.util.Iterator;
import water.Futures;
import water.Key;
import water.TestUtil;

/**
 * Created by tomasnykodym on 3/28/14.
 */
public class SparseTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  private ChunkAry makeChunk(double [] vals, Futures fs) {
    int nzs = 0;
    int [] nonzeros = new int[vals.length];
    int j = 0;
    for(double d:vals)if(d != 0)nonzeros[nzs++] = j++;
    Key key = Vec.newKey();
    AppendableVec av = new AppendableVec(key, Vec.T_NUM);
    NewChunkAry nva = av.chunkForChunkIdx(0);
    for(double d:vals){
      if(Double.isNaN(d))nva.addNA(0);
      else if((long)d == d) nva.addNum((long)d,0);
      else nva.addNum(d);
    }
    Vec v = av.close(nva.close(new Futures()));
    fs.blockForPending();
    return v.chunkForChunkIdx(0);
  }

  private ChunkAry setAndClose(double val, int id, ChunkAry c, Futures fs){return setAndClose(new double[]{val},new int[]{id},c,fs);}
  private ChunkAry setAndClose(double [] vals, int [] ids, ChunkAry c, Futures fs) {
    final int cidx = c.cidx();
    final VecAry vec = c._vec;
    for(int i = 0; i < vals.length; ++i)
      c.set(ids[i], vals[i]);
    c.close(fs);
    return vec.chunkForChunkIdx(cidx);
  }

  private void runTest(double [] vs, double v1, double v2, Class class0, Class class1, Class class2) {
    Futures fs = new Futures();
    int length = 4*NewChunk.MIN_SPARSE_RATIO + 1;
    double [] vals = new double[length];
    int [] nzs = new int[]{length/4,length/2,(3*length)/4};
    // test sparse double
    vals[nzs[0]] = vs[0];
    vals[nzs[1]] = vs[1];
    vals[nzs[2]] = vs[2];
    ChunkAry c0 = makeChunk(vals,fs);
    assertTrue(class0.isAssignableFrom(c0.getClass()));
    try{
      assertTrue(class0.isAssignableFrom(c0.getClass()));
      assertEquals(3,c0.sparseLenZero());
      for(int i = 0; i < vals.length; ++i){
        assertEquals(Double.isNaN(vals[i]), c0.isNA(i));
        assertTrue(Double.isNaN(vals[i]) || vals[i] == c0.atd(i));
      }
      Chunk.SparseNum sv = c0.sparseNum(0);
      // test skip cnt iteration
      for(int nz:nzs){
        int j = sv.rowId();
        assertEquals(nz,sv.rowId());
        assertEquals(Double.isNaN(vals[nz]),c0.isNA(j));
        assertTrue(Double.isNaN(vals[nz]) || vals[nz] == c0.atd(j));
        sv.nextNZ();
      }

      ChunkAry c1 = setAndClose(vals[length-1] = v1,length-1,c0,fs);
      assertTrue(class1.isAssignableFrom(c1.getClass()));
      // test sparse set
      assertEquals(4,c1.sparseLenZero());
      assertEquals(Double.isNaN(v1),c1.isNA(length - 1));
      assertTrue(Double.isNaN(v1) || v1 == c1.atd(length - 1));
      ChunkAry c2 = setAndClose(vals[0] = v2,0,c1,fs);
      assertTrue(class2.isAssignableFrom(c2.getClass()));
      sv = c2.sparseNum(0);
      assertTrue(sv.rowId() == 0);
      assertEquals(vals.length,c2.sparseLenZero());
      for(int i = 0; i < vals.length; ++i){
        assertEquals(Double.isNaN(vals[i]),c2.isNA(i));
        assertTrue(Double.isNaN(vals[i]) || vals[i] == c2.atd(i));
        assertTrue(sv.nextNZ().rowId() == i+1);
      }
      fs.blockForPending();
    } finally {
      c0._vec.removeVecs();
    }
  }


  @Test public void testDouble() {runTest(new double [] {2.7182,3.14,42},Double.NaN,123.45,CX8Chunk.class,CX8Chunk.class,CUDChunk.class);}

  @Test public void testBinary() {
    runTest(new double [] {1,1,1},1,1,CX0Chunk.class,CX0Chunk.class,CBSChunk.class);
    runTest(new double [] {1,1,1},Double.NaN,1,CX0Chunk.class,CXIChunk.class,CBSChunk.class);
  }
  @Test public void testInt() {
    runTest(new double [] {1,2,Double.NaN},4,5,CXIChunk.class,CXIChunk.class,C1Chunk.class);
    runTest(new double [] {1,2000,Double.NaN,3},4,5,CXIChunk.class,CXIChunk.class,C2Chunk.class);
    runTest(new double [] {Double.NaN,2000,3},400000,5,CXIChunk.class,CXIChunk.class,C4Chunk.class);
    runTest(new double [] {1,Double.NaN,2000,3},Double.NaN,1e10,CXIChunk.class,CXIChunk.class,C8Chunk.class);
  }
}
