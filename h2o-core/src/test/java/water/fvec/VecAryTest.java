package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.parser.ParseDataset;
import water.util.ArrayUtils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by tomas on 12/5/16.
 */
public class VecAryTest extends TestUtil {
  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }


  @Test
  public void tinyTest() {
    double [][] data = new double[][] {
        {1.1,1.2,1.3,1.4,1.5},
        {2.1,2.2,2.3,2.4,2.5},
        {3.1,3.2,3.3,3.4,3.5},
        {4.1,4.2,4.3,4.4,4.5},
        {5.1,5.2,5.3,5.4,5.5}
    };
    Vec v0 = TestUtil.vec(data);
    VecAry vecs = new VecAry(v0);
    assertArrayEquals(new double[]{3.1,3.2,3.3,3.4,3.5},vecs.means(),0);
    assertArrayEquals(ArrayUtils.makeConst(5,1.581139),vecs.sds(),1e-5);
    for(int r = 0; r < data.length; ++r){
      for(int c = 0; c < data[r].length; ++c){
        assertEquals(data[r][c],vecs.at(r,c),0);
      }
    }
    VecAry vecs2 = vecs.remove(0,2,4);
    assertArrayEquals(new double[]{3.2,3.4},vecs.means(),0);
    for(int r = 0; r < data.length; ++r){
      assertEquals(data[r][1],vecs.at(r,0),0);
      assertEquals(data[r][3],vecs.at(r,1),0);
    }
    vecs.remove();
    assertArrayEquals(new double[]{3.1,3.3,3.5},vecs2.means(),0);
    for(int r = 0; r < data.length; ++r){
      assertEquals(data[r][0],vecs2.at(r,0),0);
      assertEquals(data[r][2],vecs2.at(r,1),0);
      assertEquals(data[r][4],vecs2.at(r,2),0);
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
        Vec vi = vecs.select(i);
        assertEquals(vecs.mean(i),vi.mean(),0.0);
      }
      double [] mus = vecs.means();
      double [] sds = vecs.sds();
      for(int i = 0; i < vecs.numCols(); ++i){
        double mu = vecs.mean(i);
        VecAry vi = vecs.remove(i);
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
