package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.util.ArrayUtils;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class FVecTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }
  static final double EPSILON = 1e-6;

  public static  Key makeByteVec(Key k, String... data) {
    byte [][] chunks = new byte[data.length][];
    long [] espc = new long[data.length+1];
    for(int i = 0; i < chunks.length; ++i){
      chunks[i] = data[i].getBytes();
      espc[i+1] = espc[i] + data[i].length();
    }
    Futures fs = new Futures();
    Key key = Vec.newKey();
    ByteVec bv = new ByteVec(key,Vec.ESPC.rowLayout(key,espc));
    for(int i = 0; i < chunks.length; ++i){
      Key chunkKey = bv.chunkKey(i);
      DKV.put(chunkKey, new Value(chunkKey,chunks[i].length,chunks[i],TypeMap.C1NCHUNK,Value.ICE),fs);
    }
    DKV.put(bv._key,bv,fs);
    Frame fr = new Frame(k,new String[]{"makeByteVec"},new Vec[]{bv});
    DKV.put(k, fr, fs);
    fs.blockForPending();
    return k;
  }

/*
  Test that we actually fail on failures.  :-)
  @Test public void testBlammo() {
    assertEquals(1, 2);
  }
*/

  // ==========================================================================
  @Test public void testBasicCRUD() {
    // Make and insert a FileVec to the global store
    File file = find_test_file("./smalldata/junit/cars.csv");
    NFSFileVec nfs = NFSFileVec.make(file);
    int sum = ArrayUtils.sum(new ByteHisto().doAll(nfs)._x);
    assertEquals(file.length(),sum);
    nfs.remove();
  }

  private static class ByteHisto extends MRTask<ByteHisto> {
    public int[] _x;
    // Count occurrences of bytes
    @Override public void map( Chunk bv ) {
      _x = new int[256];        // One-time set histogram array
      for( int i=0; i< bv._len; i++ )
        _x[(int)bv.atd(i)]++;
    }
    // ADD together all results
    @Override public void reduce( ByteHisto bh ) { ArrayUtils.add(_x,bh._x); }
  }

  // ==========================================================================
  @Test public void testSet() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      double[] mins =new double[fr.numCols()];
      for (int i=0; i < mins.length; i++)
        mins[i] = fr.vecs()[i].min();
      // Scribble into a freshly parsed frame
      new SetDoubleInt(mins).doAll(fr);
    } finally {
      if( fr != null ) fr.delete();
    }
  }

  static class SetDoubleInt extends MRTask {
    final double _mins[];
    public SetDoubleInt(double [] mins) {_mins = mins;}
    @Override public void map( Chunk chks[] ) {
      Chunk c=null;
      int i;
      for(i=0; i < chks.length; i++) {
        if( chks[i].getClass()==water.fvec.C2Chunk.class )
        { c=chks[i]; break; }
      }
      Assert.assertNotNull("Expect to find a C2Chunk", c);
      assertTrue(c._vec.writable());

      double d=_mins[i];
      for(i=0; i< c._len; i++ ) {
        double e = c.atd(i);
        c.set(i, d);
        d=e;
      }
    }
  }

  // ==========================================================================
  // Test making a appendable vector from a plain vector
  @Test public void testNewVec() {
    // Make and insert a File8Vec to the global store
    File file = find_test_file("./smalldata/junit/cars.csv");
    NFSFileVec nfs = NFSFileVec.make(file);
    Vec res = new TestNewVec().doAll(new byte[]{Vec.T_NUM},nfs).outputFrame(new String[]{"v"},new String[][]{null}).anyVec();
    assertEquals(nfs.at8(0)+1,res.at8(0));
    assertEquals(nfs.at8(1)+1,res.at8(1));
    assertEquals(nfs.at8(2)+1,res.at8(2));
    nfs.remove();
    res.remove();
  }

  private static class TestNewVec extends MRTask<TestNewVec> {
    @Override public void map( Chunk in, NewChunk out ) {
      for( int i=0; i< in._len; i++ )
        out.addNum( in.at8_abs(i)+(in.at8_abs(i) >= ' ' ? 1 : 0),0);
    }
  }

  // ==========================================================================
  @Test public void testParse2() {
    Frame fr = null;
    Vec vz = null;
    try {
      fr = parse_test_file("smalldata/junit/syn_2659x1049.csv.gz");
      assertEquals(fr.numCols(),1050); // Count of columns
      assertEquals(fr.numRows(),2659); // Count of rows

      double[] sums = new Sum().doAll(fr)._sums;
      assertEquals(3949,sums[0],EPSILON);
      assertEquals(3986,sums[1],EPSILON);
      assertEquals(3993,sums[2],EPSILON);

      // Create a temp column of zeros
      Vec v0 = fr.vecs()[0];
      Vec v1 = fr.vecs()[1];
      vz = v0.makeZero();
      // Add column 0 & 1 into the temp column
      new PairSum().doAll(vz,v0,v1);
      // Add the temp to frame
      // Now total the temp col
      fr.delete();              // Remove all other columns
      fr = new Frame(Key.<Frame>make(), new String[]{"tmp"}, new Vec[]{vz}); // Add just this one
      sums = new Sum().doAll(fr)._sums;
      assertEquals(3949+3986,sums[0],EPSILON);

    } finally {
      if( vz != null ) vz.remove();
      if( fr != null ) fr.delete();
    }
  }

  // Sum each column independently
  private static class Sum extends MRTask<Sum> {
    double _sums[];
    @Override public void map( Chunk[] bvs ) {
      _sums = new double[bvs.length];
      int len = bvs[0]._len;
      for( int i=0; i<len; i++ )
        for( int j=0; j<bvs.length; j++ )
          _sums[j] += bvs[j].atd(i);
    }
    @Override public void reduce( Sum mrt ) { ArrayUtils.add(_sums, mrt._sums);  }
  }

  // Simple vector sum C=A+B
  private static class PairSum extends MRTask<Sum> {
    @Override public void map( Chunk out, Chunk in1, Chunk in2 ) {
      for( int i=0; i< out._len; i++ )
        out.set(i, in1.at8(i) + in2.at8(i));
    }
  }

  @Test public void testRollups() {
//    Frame fr = null;
//    try {
    Key rebalanced = Key.make("rebalanced");
    Vec v = null;
    Frame fr = null;
    try {
      v = Vec.makeVec(new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, Vec.newKey());
      Futures fs = new Futures();
      assertEquals(0, v.min(), 0);
      assertEquals(9, v.max(), 0);
      assertEquals(4.5,v.mean(),1e-8);
      H2O.submitTask(new RebalanceDataSet(new Frame(v), rebalanced, 10)).join();
      fr = DKV.get(rebalanced).get();
      Vec v2 = fr.anyVec();
      assertEquals(0, v2.min(), 0);
      assertEquals(9, v2.max(), 0);
      assertEquals(4.5, v.mean(), 1e-8);
      v2.set(5, -100);
      assertEquals(-100, v2.min(), 0);
      v2.set(5, 5);
      // make several rollups requests in parallel with and without histo and then get histo
      v2.startRollupStats(fs);
      v2.startRollupStats(fs);
      v2.startRollupStats(fs,true);
      assertEquals(0, v2.min(), 0);
      long [] bins = v2.bins();
      assertEquals(10,bins.length);
      // TODO: should test percentiles?
      for(long l:bins) assertEquals(1,l);
      Vec.Writer w = v2.open();
      try {
        v2.min();
        assertTrue("should have thrown IAE since we're requesting rollups while changing the Vec (got Vec.Writer)",false); // fail - should've thrown
      } catch( IllegalArgumentException ie ) {
        // if on local node can get iae directly
      } catch( RuntimeException re ) {
        assertTrue(re.getCause() instanceof IllegalArgumentException);
        // expect to get IAE since we're requesting rollups while also changing the vec
      }
      w.close(fs);
      fs.blockForPending();
      assertEquals(0,v2.min(),0);
      fr.delete();
      v.remove();
      fr = null;
    } finally {
      if( v != null)v.remove();
      if(fr != null)fr.delete();
    }
  }

  // The rollups only compute approximate quantiles, not exact.
  @Test public void test50pct() {
    Vec vec = null;
    try {
      double[] d = new double[]{0.812834256224, 1.56386606237, 3.12702210880, 3.68417563302, 5.51277746586};
      vec = Vec.makeVec(d,Vec.newKey());
      double pct[] = vec.pctiles();
      double eps = (vec.max()-vec.min())/1e-3;
      Assert.assertEquals(pct[0],d[0],eps); // 0.01
      Assert.assertEquals(pct[1],d[0],eps); // 0.1
      Assert.assertEquals(pct[2],d[0],eps); // 0.25
      Assert.assertEquals(pct[3],d[1],eps); // 1/3
      Assert.assertEquals(pct[4],d[2],eps); // 0.5
      Assert.assertEquals(pct[5],d[2],eps); // 2/3
      Assert.assertEquals(pct[6],d[3],eps); // 0.75
      Assert.assertEquals(pct[7],d[4],eps); // 0.9
      Assert.assertEquals(pct[8],d[4],eps); // 0.99
      vec.remove();

      d = new double[]{490,492,494,496,498};
      vec = Vec.makeVec(d,Vec.newKey());
      pct = vec.pctiles();
      eps = (vec.max()-vec.min())/1e-3;
      System.out.println(java.util.Arrays.toString(pct));
      Assert.assertEquals(pct[0],d[0],eps); // 0.01

    } finally {
      if( vec != null ) vec.remove();
    }
  }
}
