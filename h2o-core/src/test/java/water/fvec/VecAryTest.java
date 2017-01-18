package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.parser.ParseDataset;
import water.util.ArrayUtils;

import java.lang.reflect.Array;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by tomas on 12/5/16.
 */
public class VecAryTest extends TestUtil {
  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  double [][] tiny_data = new double[][] {
      {1.1,1.2,1.3,1.4,1.5},
      {2.1,2.2,2.3,2.4,2.5},
      {3.1,3.2,3.3,3.4,3.5},
      {4.1,4.2,4.3,4.4,4.5},
      {5.1,5.2,5.3,5.4,5.5}
  };
  double [][] tiny_data_2 = new double[][] {
      {1,2,3,4,5},
      {2.1,2.2,2.3,2.4,2.5},
      {3.1,3.2,3.3,3.4,3.5},
      {4.1,4.2,4.3,4.4,4.5},
      {5.1,5.2,5.3,5.4,5.5}
  };
  @Test
  public void tinyTest() {
    Vec v0 = TestUtil.vec(tiny_data);
    VecAry vecs = new VecAry(v0);
    assertArrayEquals(new double[]{3.1,3.2,3.3,3.4,3.5},vecs.means(),0);
    assertArrayEquals(ArrayUtils.makeConst(5,1.581139),vecs.sds(),1e-5);
    for(int r = 0; r < tiny_data.length; ++r){
      for(int c = 0; c < tiny_data[r].length; ++c){
        assertEquals(tiny_data[r][c],vecs.at(r,c),0);
      }
    }
    VecAry vecs2 = vecs.removeVecs(0,2,4);
    assertArrayEquals(new double[]{3.2,3.4},vecs.means(),0);
    for(int r = 0; r < tiny_data.length; ++r){
      assertEquals(tiny_data[r][1],vecs.at(r,0),0);
      assertEquals(tiny_data[r][3],vecs.at(r,1),0);
    }
    vecs.remove();
    assertArrayEquals(new double[]{3.1,3.3,3.5},vecs2.means(),0);
    for(int r = 0; r < tiny_data.length; ++r){
      assertEquals(tiny_data[r][0],vecs2.at(r,0),0);
      assertEquals(tiny_data[r][2],vecs2.at(r,1),0);
      assertEquals(tiny_data[r][4],vecs2.at(r,2),0);
    }
    vecs2.remove();
  }

  private static class SumTsk extends MRTask<SumTsk>{
    public double [] _sums;
    @Override public void map(ChunkAry cs) {
      _sums = new double[cs._numCols];
      for (int c = 0; c < cs._numCols; ++c) {
        double s = 0;
        for (int r = 0; r < cs._len; ++r)
          s += cs.atd(r, c);
        _sums[c] = s;
      }
    }
    @Override public void reduce(SumTsk t){
      ArrayUtils.add(_sums,t._sums);
    }
  }

  @Test
  public void testRebalance(){
    VecAry vecs = null;
    try {
      Vec v0 = TestUtil.vec(tiny_data);
      vecs = new VecAry(v0).rebalance(3,true);
      for(int r = 0; r < tiny_data.length; ++r){
        for(int c = 0; c < tiny_data[r].length; ++c){
          assertEquals(tiny_data[r][c],vecs.at(r,c),0);
        }
      }
    } finally {
      if(vecs != null)vecs.remove();
    }
  }

  @Test
  public void testCopy() {
    Vec v0 = TestUtil.vec(tiny_data);
    VecAry vecs0 = new VecAry(v0).rebalance(3,true);
    Vec v1 = TestUtil.vec(tiny_data_2);
    VecAry vecs1 = new VecAry(v1).rebalance(3,true);
    VecAry vecs3 = vecs1.select(1,3);
    vecs3.append(vecs0.select(1,3));
    vecs3 = vecs3.select(1,3,0,2);
    vecs3 = vecs3.makeCopy();
    System.out.println(Arrays.toString(vecs0.means()));
    System.out.println(Arrays.toString(vecs1.means()));
    System.out.println(Arrays.toString(vecs3.means()));
    vecs0.remove();
    vecs1.remove();
    vecs3.remove();
  }

  @Test
  public void testRemove(){
    Vec v0 = TestUtil.vec(tiny_data);
    VecAry vecs0 = new VecAry(v0).rebalance(3,true);
    VecAry vecs1 = new VecAry(vecs0);
    double [] mus = vecs0.means();
    double [] sums = new SumTsk().doAll(vecs0)._sums;
    // test basic remove a column
    vecs0.removeVecs(1,3).remove();
    assertEquals(3,vecs0.numCols());
    assertArrayEquals(ArrayUtils.select(mus,0,2,4),vecs0.means(),0);
    assertArrayEquals(ArrayUtils.select(sums,0,2,4),new SumTsk().doAll(vecs0)._sums,0);
    Vec v = vecs1.vecs()[0];
    DBlock.MultiChunkBlock mdb = (DBlock.MultiChunkBlock) v.chunkIdx(0);
    assertTrue(mdb._cs[1] == null);
    assertTrue(mdb._cs[3] == null);
    assertTrue(mdb._cs[0] != null);
    assertTrue(mdb._cs[2] != null);
    assertTrue(mdb._cs[4] != null);
    try {
      vecs1.mean(1); // should throw
      assertFalse("should've thrown",true);
    } catch(IllegalArgumentException iae){
      assertTrue(iae.getMessage().contains("removed vec"));
    }
    vecs0.removeVecs(0,1,2).remove(); // should not be any leaks after this!
  }
  @Test
  public void testAppend() {
    Vec v0 = TestUtil.vec(tiny_data);
    VecAry vecs0 = new VecAry(v0).rebalance(3,true);
    Vec v1 = TestUtil.vec(tiny_data_2);
    VecAry vecs1 = new VecAry(v1).align(vecs0,true);
    double [] mus0 = vecs0.means();
    double [] mus1 = vecs1.means();
    double [] sums0 = new SumTsk().doAll(vecs0)._sums;
    double [] sums1 = new SumTsk().doAll(vecs1)._sums;
    // Test basic append
    VecAry x = vecs0.select(0,1);
    VecAry y = vecs0.select(2,3,4);
    x.append(y);
    assertTrue(vecs0.equals(x));
    assertTrue(vecs0._colFilter == null);
    VecAry vecs2 = new VecAry(vecs0);
    vecs2.append(vecs1);
    System.out.println("expected means = " + Arrays.toString(ArrayUtils.append(mus0,mus1)));
    System.out.println("actual   means = " + Arrays.toString(vecs2.means()));
    assertArrayEquals(ArrayUtils.append(mus0,mus1),vecs2.means(),0);
    double [] sums2 = new SumTsk().doAll(vecs2)._sums;
    System.out.println("sums0 = " + Arrays.toString(sums0) + ", sums1 = " + Arrays.toString(sums1));
    System.out.println("sums = " + Arrays.toString(sums2));
    assertArrayEquals(ArrayUtils.append(sums0,sums1),sums2,0);
    // Test append with both filters
    VecAry vecs3 = vecs1.select(1,3);
    vecs3.append(vecs0.select(1,3));
    System.out.println(Arrays.toString(vecs3.means()));
    vecs3 = vecs3.select(1,3,0,2);
    double [] sums3 = new SumTsk().doAll(vecs3)._sums;
    double [] mus3 = vecs3.means();
    assertEquals(sums1[3],sums3[0],0);
    assertEquals(mus1[3],mus3[0],0);
    assertEquals(sums0[3],sums3[1],0);
    assertEquals(mus0[3],mus3[1],0);
    assertEquals(sums1[1],sums3[2],0);
    assertEquals(mus1[1],mus3[2],0);
    assertEquals(sums0[1],sums3[3],0);
    assertEquals(mus0[1],mus3[3],0);
    // test filter X no filter append
    vecs3 = vecs0.select(4,3,2,1,0);
    vecs3.append(vecs1);
    sums3 = new SumTsk().doAll(vecs3)._sums;
    mus3 = vecs3.means();
    for(int i = 0; i < vecs0.numCols(); ++i) {
      assertEquals(sums0[i],sums3[4-i],0);
      assertEquals(mus0[i],mus3[4-i],0);
      assertEquals(sums1[i],sums3[5+i],0);
      assertEquals(mus1[i],mus3[5+i],0);
    }
    // test no filter X filter append
    vecs3 = new VecAry(vecs0);
    vecs3.append(vecs1.select(4,3,2,1,0));
    sums3 = new SumTsk().doAll(vecs3)._sums;
    mus3 = vecs3.means();
    for(int i = 0; i < vecs0.numCols(); ++i) {
      assertEquals(sums0[i],sums3[i],0);
      assertEquals(mus0[i],mus3[i],0);
      assertEquals(sums1[i],sums3[9-i],0);
      assertEquals(mus1[i],mus3[9-i],0);
    }
    vecs2.remove();
  }

  @Test
  public void testBasics() {
    NFSFileVec[] nfs = new NFSFileVec[]{
    NFSFileVec.make(find_test_file("smalldata/logreg/prostate.csv")),
    NFSFileVec.make(find_test_file("smalldata/covtype/covtype.20k.data"))};
    System.out.println("made vecs " + nfs[0]._key + ", " + nfs[1]._key);
    //NFSFileVec.make(find_test_file("bigdata/laptop/usecases/cup98VAL_z.csv"))};
    for (NFSFileVec fv : nfs) {
      Frame fr = ParseDataset.parse(Key.make(), fv._key);
      VecAry vecs = fr.vecs();
      for(int i = 0; i < vecs.numCols(); ++i){
        VecAry vi = vecs.select(i);
        assertEquals(vecs.mean(i),vi.mean(),0.0);
      }
      double [] mus = vecs.means();
      double [] sds = vecs.sds();
      for(int i = 0; i < vecs.numCols(); ++i){
        double mu = vecs.mean(i);
        VecAry vi = vecs.removeVecs(i);
        assertEquals(mu,vi.mean(),0.0);
        vecs.insertVec(i,vi);
        assertArrayEquals(mus,vecs.means(),0);
        assertArrayEquals(sds,vecs.sds(),0);
      }

      System.out.println("parsed data into " + fr.numRows() + " x " + fr.numCols() + ", into " + fr.anyVec().nChunks() + " chunks and " + fr._vecs._vecIds.length + " vecs");
      fr.delete();
    }
  }
}
