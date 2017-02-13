package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.stall_till_cloudsize;

/**
 * Created by tomas on 2/9/17.
 */
public class CXCChunkTest {

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testRead(){
    double [] vals = new double[256];
    Random rnd = new Random(54321);
    double con = rnd.nextInt()*rnd.nextDouble();
    int [] ids = new int[vals.length>>4];
    for(int i = 0; i < vals.length >> 4; ++i) {
      int x = rnd.nextInt(vals.length);
      while(vals[x] != 0)
        x = rnd.nextInt(vals.length);
      vals[ids[i] = x] = con;
    }
    Arrays.sort(ids);
    NewChunk nc = new NewChunk(Vec.T_NUM);
    for (double d : vals) nc.addNum(d);
    CXCChunk cc = (CXCChunk) nc.compress();
    assertEquals(vals.length>>4,cc.len());
    for(int i = 0; i < vals.length; ++i){
      assertEquals(vals[i],cc.atd(i),0);
      assertEquals((long)vals[i],cc.at8(i),0);
      assertTrue(!cc.isNA(i));
    }
    int j = 0;
    ChunkAry ca = new ChunkAry(vals.length,cc);
    for(Chunk.SparseNum sv = ca.sparseNum(0); sv.rowId() < vals.length; sv = sv.nextNZ()){
      assertEquals(ids[j++],sv.rowId());
      assertEquals(con,sv.dval(),0);
    }
    assertEquals(ids.length,j);
    // test bulk sparse double access
    int [] ids2 = new int[vals.length>>4];
    double [] vals2 = new double[vals.length];
    int x = ca.getSparseDoubles(0,vals2,ids2,Double.NaN);
    assertEquals(x,ids2.length);
    assertTrue(Arrays.equals(ids,ids2));
    for(int i = 0; i < x; ++i)
      assertEquals(con,vals2[i],0);
    vals2 = ca.getDoubles(0,vals2,Double.NaN);
    assertTrue(Arrays.equals(vals,vals2));
  }

  @Test
  public void testAdd2Chunk(){
    double [] vals = new double[256];
    Random rnd = new Random(54321);
    double con = rnd.nextInt()*rnd.nextDouble();
    int [] ids = new int[vals.length>>4];
    for(int i = 0; i < vals.length >> 4; ++i) {
      int x = rnd.nextInt(vals.length);
      while(vals[x] != 0)
        x = rnd.nextInt(vals.length);
      vals[ids[i] = x] = con;
    }
    Arrays.sort(ids);
    NewChunk nc = new NewChunk(Vec.T_NUM);
    for (double d : vals) nc.addNum(d);
    CX0Chunk cc = (CX0Chunk) nc.compress();
    nc = new NewChunk(Vec.T_NUM);
    int from = rnd.nextInt(vals.length-1);
    int to = from + rnd.nextInt(vals.length-from);
    cc.add2Chunk(nc,from,to);
    Chunk c = cc.compress();
    for(int i = from; i < to; ++i){
      assertEquals(vals[i],c.atd(i),0);
      assertEquals((long)vals[i],c.at8(i),0);
      assertTrue(!c.isNA(i));
    }
    HashSet<Integer> selectedRows = new HashSet<>();
    for(int i = 0; i < vals.length>>2; ++i){
      int x = rnd.nextInt(vals.length);
      while(selectedRows.contains(x))x = rnd.nextInt(vals.length);
      selectedRows.add(x);
    }
    int [] selectedAry = new int[selectedRows.size()];
    int j = 0;
    for(int i:selectedRows)
      selectedAry[j++] = i;
    Arrays.sort(selectedAry);
    nc = new NewChunk(Vec.T_NUM);
    cc.add2Chunk(nc,selectedAry);
    c = nc.compress();
    j = 0;
    for(int id:selectedAry)
      assertEquals(cc.atd(id), c.atd(j++), 0);
    // inflate and compress again, should be identical
    nc = cc.add2Chunk(new NewChunk(Vec.T_NUM),0,vals.length);
    c = nc.compress();
    assertArrayEquals(cc.asBytes(),c.asBytes());
  }
}
