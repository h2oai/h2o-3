package water;

import static org.junit.Assert.*;
import java.io.File;
import org.junit.*;
import water.fvec.*;

public class TestUtil {
  private static int _initial_keycnt = 0;

  // Stall test until we see at least X members of the Cloud
  public static void stall_till_cloudsize(int x) {
    H2O.waitForCloudSize(x, 100000);
  }

  @BeforeClass public static void setupCloud() {
    H2O.main(new String[] {});
    stall_till_cloudsize(1);
    _initial_keycnt = H2O.store_size();
  }

  @AfterClass public static void checkLeakedKeys() {
    int leaked_keys = H2O.store_size() - _initial_keycnt;
    if( leaked_keys > 0 ) {
      for( Key k : H2O.localKeySet() ) {
        Value value = H2O.raw_get(k);
        // Ok to leak VectorGroups
        if( value.isVecGroup() ) leaked_keys--;
        else System.err.println("Leaked key: " + k + " = " + TypeMap.className(value.type()));
      }
    }
    assertTrue("No keys leaked", leaked_keys <= 0);
    _initial_keycnt = H2O.store_size();
  }

  // Hunt for test files in likely places.  Null if cannot find.
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


  public static Vec vec(int...rows) { return vec(null, rows); }
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

  public static String[]   ar (String ...a)   { return a; }
  public static long  []   ar (long   ...a)   { return a; }
  public static long[][]   ar (long[] ...a)   { return a; }
  public static int   []   ari(int    ...a)   { return a; }
  public static int [][]   ar (int[]  ...a)   { return a; }
  public static float []   arf(float  ...a)   { return a; }
  public static double[]   ard(double ...a)   { return a; }
  public static double[][] ard(double[] ...a) { return a; }
}

