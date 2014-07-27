package water;

import static org.testng.Assert.*;
import org.testng.annotations.*;

import java.io.File;
import java.util.ArrayList;
import java.lang.reflect.*;
import water.fvec.*;
import testframework.multinode.MultiNodeSetup;

public class TestNGUtil {
  private static boolean _stall_called_before = false;
  protected static int _initial_keycnt = 0;
  protected final int _minCloudSize;

  public TestNGUtil() {
    _minCloudSize = 1;
  }

  public TestNGUtil(int minCloudSize) {
    _minCloudSize = minCloudSize;
  }

  public int getMinCloudSize() {
    return _minCloudSize;
  }

  // ==== Test Setup & Teardown Utilities ====
  // Stall test until we see at least X members of the Cloud
  public static void stall_till_cloudsize(int x) {
    if (! _stall_called_before) {
      if (H2O.getCloudSize() < x) {
        MultiNodeSetup mns = new MultiNodeSetup(x);
        mns.setupCloud();
        _stall_called_before = true;
      }
    }

    H2O.waitForCloudSize(x, 10000);
  }

  @BeforeClass()
  public void setupCloud() {
    stall_till_cloudsize(_minCloudSize);
    _initial_keycnt = H2O.store_size();
  }

  @AfterClass
  public static void checkLeakedKeys() {
    int leaked_keys = H2O.store_size() - _initial_keycnt;
    if( leaked_keys > 0 ) {
      for( Key k : H2O.localKeySet() ) {
        Value value = H2O.raw_get(k);
        // Ok to leak VectorGroups
        if( value.isVecGroup() || k == Job.LIST ) leaked_keys--;
        else System.err.println("Leaked key: " + k + " = " + TypeMap.className(value.type()));
      }
    }
    assertTrue(leaked_keys <= 0, "No keys leaked");
    _initial_keycnt = H2O.store_size();
  }


  // ==== Data Frame Creation Utilities ====

  /** Hunt for test files in likely places.  Null if cannot find.
   *  @param fname Test filename
   *  @return      Found file or null */
  protected File find_test_file( String fname ) {
    // When run from eclipse, the working directory is different.
    // Try pointing at another likely place
    File file = new File(fname);
    if( !file.exists() )
      file = new File("target/" + fname);
    if( !file.exists() )
      file = new File("../" + fname);
    if( !file.exists() )
      file = new File("../target/" + fname);
    if( !file.exists() )
      file = null;
    return file;
  }

  /** Find & parse a CSV file.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  protected Frame parse_test_file( String fname ) {
    NFSFileVec nfs = NFSFileVec.make(find_test_file(fname));
    return water.parser.ParseDataset2.parse(Key.make(),nfs._key);
  }

  /** Find & parse a folder of CSV files.  NPE if file not found.
   *  @param fname Test filename
   *  @return      Frame or NPE */
  protected Frame parse_test_folder( String fname ) {
    File folder = find_test_file(fname);
    assert folder.isDirectory();
    ArrayList<Key> keys = new ArrayList<>();
    for( File f : folder.listFiles() )
      if( f.isFile() )
        keys.add(NFSFileVec.make(f)._key);
    Key[] res = new Key[keys.size()];
    keys.toArray(res);
    return water.parser.ParseDataset2.parse(Key.make(),res);
  }

  /** A Numeric Vec from an array of ints
   *  @param rows Data
   *  @return The Vec  */
  public static Vec vec(int...rows) { return vec(null, rows); }
  /** A Enum/Factor Vec from an array of ints - with enum/domain mapping
   *  @param domain Enum/Factor names, mapped by the data values
   *  @param rows Data
   *  @return The Vec  */
  public static Vec vec(String[] domain, int ...rows) { 
    Key k = Vec.VectorGroup.VG_LEN1.addVec();
    Futures fs = new Futures();
    AppendableVec avec = new AppendableVec(k);
    avec.setDomain(domain);
    NewChunk chunk = new NewChunk(avec, 0);
    for( int r : rows ) chunk.addNum(r);
    chunk.close(0, fs);
    Vec vec = avec.close(fs);
    fs.blockForPending();
    return vec;
  }

  /** Create a new frame based on given row data.
   *  @param names  names of frame columns
   *  @param rows  data given in the form of rows
   *  @return new frame which contains columns named according given names and including given data */
  public static Frame frame(String[] names, double[]... rows) {
    assert names == null || names.length == rows[0].length;
    Futures fs = new Futures();
    Vec[] vecs = new Vec[rows[0].length];
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(vecs.length);
    for( int c = 0; c < vecs.length; c++ ) {
      AppendableVec vec = new AppendableVec(keys[c]);
      NewChunk chunk = new NewChunk(vec, 0);
      for (double[] row : rows) chunk.addNum(row[c]);
      chunk.close(0, fs);
      vecs[c] = vec.close(fs);
    }
    fs.blockForPending();
    return new Frame(names, vecs);
  }

  // Shortcuts for initializing constant arrays
  public static String[]   ar (String ...a)   { return a; }
  public static long  []   ar (long   ...a)   { return a; }
  public static long[][]   ar (long[] ...a)   { return a; }
  public static int   []   ari(int    ...a)   { return a; }
  public static int [][]   ar (int[]  ...a)   { return a; }
  public static float []   arf(float  ...a)   { return a; }
  public static double[]   ard(double ...a)   { return a; }
  public static double[][] ard(double[] ...a) { return a; }


  // ==== Comparing Results ====

  /** Compare 2 doubles within a tolerance
   *  @param a double 
   *  @param b double
   *  @param abseps - Absolute allowed tolerance
   *  @param releps - Relative allowed tolerance
   *  @return true if equal within tolerances  */
  protected boolean compare(double a, double b, double abseps, double releps) {
    return
      Double.compare(a, b) == 0 || // check for equality
      Math.abs(a-b)/Math.max(a,b) < releps ||  // check for small relative error
      Math.abs(a - b) <= abseps; // check for small absolute error
  }

  /** Compare 2 doubles within a tolerance
   *  @param fr1 Frame
   *  @param fr2 Frame
   *  @return true if equal  */
  protected static boolean isBitIdentical( Frame fr1, Frame fr2 ) {
    if( fr1.numCols() != fr2.numCols() ) return false;
    if( fr1.numRows() != fr2.numRows() ) return false;
    if( fr1.checkCompatible(fr2) )
      return !(new Cmp1().doAll(new Frame(fr1).add(fr2))._unequal);
    // Else do it the slow hard way
    return !(new Cmp2(fr2).doAll(fr1)._unequal);
  }
  // Fast compatible Frames
  private static class Cmp1 extends MRTask<Cmp1> {
    boolean _unequal;
    @Override public void map( Chunk chks[] ) {
      for( int cols=0; cols<chks.length>>1; cols++ ) {
        Chunk c0 = chks[cols                 ];
        Chunk c1 = chks[cols+(chks.length>>1)];
        for( int rows = 0; rows < chks[0].len(); rows++ ) {
          double d0 = c0.at0(rows), d1 = c1.at0(rows);
          if( !(Double.isNaN(d0) && Double.isNaN(d1)) && (d0 != d1) ) {
            _unequal = true; return;
          }
        }
      }
    }
    @Override public void reduce( Cmp1 cmp ) { _unequal |= cmp._unequal; }
  }
  // Slow incompatible frames
  private static class Cmp2 extends MRTask<Cmp2> {
    final Frame _fr;
    Cmp2( Frame fr ) { _fr = fr; }
    boolean _unequal;
    @Override public void map( Chunk chks[] ) {
      for( int cols=0; cols<chks.length>>1; cols++ ) {
        if( _unequal ) return;
        Chunk c0 = chks[cols];
        Vec v1 = _fr.vecs()[cols];
        for( int rows = 0; rows < chks[0].len(); rows++ ) {
          double d0 = c0.at0(rows), d1 = v1.at(c0.start() + rows);
          if( !(Double.isNaN(d0) && Double.isNaN(d1)) && (d0 != d1) ) {
            _unequal = true; return;
          }
        }
      }
    }
    @Override public void reduce( Cmp2 cmp ) { _unequal |= cmp._unequal; }
  }
}
