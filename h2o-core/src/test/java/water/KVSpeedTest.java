package water;

import static org.junit.Assert.*;
import org.junit.*;

import java.io.File;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.util.UnsafeUtils;

public class KVSpeedTest extends TestUtil {
  static final int NCLOUD=5;
  static final int NKEYS=10;
  @BeforeClass static public void setup() { stall_till_cloudsize(NCLOUD); }

  // Make a million keys-per-node.  Make sure they are all cached/shared on at
  // least one other node.  Time removing them all.  Can be network bound as
  // the million read/write/put/invalidates hit the wires.
  @Test @Ignore
  public void testMillionRemoveKeys() {
    long start;

    // Populate NKEYS locally
    start = System.currentTimeMillis();
    new MRTask() {
      @Override protected void setupLocal() {
        long start = System.currentTimeMillis();
        for( int i=0; i<NKEYS; i++ ) {
          String s = "Q"+i;
          Key k = Key.make(s);
          if( k.home() ) DKV.put(k,new Value(k,s),_fs);
          else i--;
        }
        System.out.println("PUT1 "+H2O.SELF+" "+NKEYS/(System.currentTimeMillis()-start));
      }
    }.doAllNodes();
    System.out.println("PUTALL "+NCLOUD*NKEYS/(System.currentTimeMillis()-start));

    // Force sharing at least once
    start = System.currentTimeMillis();
    new MRTask() {
      @Override protected void setupLocal() {
        long start = System.currentTimeMillis();
        for( int i=0; i<NKEYS; i++ )  DKV.prefetch(Key.make("Q"+i+1));
        for( int i=0; i<NKEYS; i++ )  DKV.get     (Key.make("Q"+i+1));
        System.out.println("GET1 "+H2O.SELF+" "+NKEYS/(System.currentTimeMillis()-start));
      }
    }.doAllNodes();
    System.out.println("GETALL "+NCLOUD*NKEYS/(System.currentTimeMillis()-start));

  }

}
