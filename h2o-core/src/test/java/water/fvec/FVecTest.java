package water.fvec;

import static org.junit.Assert.*;

import java.io.File;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.util.ArrayUtils;

public class FVecTest extends TestUtil {
  static final double EPSILON = 1e-6;
  @BeforeClass public static void stall() { stall_till_cloudsize(3); }

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
      for( int i=0; i< bv.len(); i++ )
        _x[(int)bv.at0(i)]++;
    }
    // ADD together all results
    @Override public void reduce( ByteHisto bh ) { ArrayUtils.add(_x,bh._x); }
  }

  // ==========================================================================
  @Test public void testSet() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      // Scribble into a freshly parsed frame
      new SetDoubleInt().doAll(fr);
    } finally {
      if( fr != null ) fr.delete();
    }
  }

  static class SetDoubleInt extends MRTask {
    @Override public void map( Chunk chks[] ) {
      Chunk c=null;
      for( Chunk x : chks )
        if( x.getClass()==water.fvec.C2Chunk.class )
          { c=x; break; }
      assertNotNull("Expect to find a C2Chunk",c);
      assertTrue(c._vec.writable());

      double d=c._vec.min();
      for( int i=0; i< c.len(); i++ ) {
        double e = c.at0(i);
        c.set0(i,d);
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
    Vec res = new TestNewVec().doAll(1,nfs).outputFrame(new String[]{"v"},new String[][]{null}).anyVec();
    assertEquals(nfs.at8(0)+1,res.at8(0));
    assertEquals(nfs.at8(1)+1,res.at8(1));
    assertEquals(nfs.at8(2)+1,res.at8(2));
    nfs.remove();
    res.remove();
  }

  private static class TestNewVec extends MRTask<TestNewVec> {
    @Override public void map( Chunk in, NewChunk out ) {
      for( int i=0; i< in.len(); i++ )
        out.append2( in.at8(i)+(in.at8(i) >= ' ' ? 1 : 0),0);
    }
  }

  // ==========================================================================
  @Test public void testParse2() {
    Frame fr = null;
    Vec vz = null;
    try {
      fr = parse_test_file("../smalldata/junit/syn_2659x1049.csv.gz");
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
      fr = new Frame(Key.make(),new String[]{"tmp"},new Vec[]{vz}); // Add just this one
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
      int len = bvs[0].len();
      for( int i=0; i<len; i++ )
        for( int j=0; j<bvs.length; j++ )
          _sums[j] += bvs[j].at0(i);
    }
    @Override public void reduce( Sum mrt ) { ArrayUtils.add(_sums, mrt._sums);  } 
  }

  // Simple vector sum C=A+B
  private static class PairSum extends MRTask<Sum> {
    @Override public void map( Chunk out, Chunk in1, Chunk in2 ) {
      for( int i=0; i< out.len(); i++ )
        out.set0(i,in1.at80(i)+in2.at80(i));
    }
  }

  // ==========================================================================
  @Test public void testLargeCats() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/junit/40k_categoricals.csv.gz");
      assertEquals(fr.numRows(),40000); // Count of rows
      assertEquals(fr.vecs()[0].domain().length,40000);

    } finally {
      if( fr != null ) fr.delete();
    }
  }
}
