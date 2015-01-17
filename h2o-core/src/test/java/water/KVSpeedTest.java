package water;

import org.junit.*;

public class KVSpeedTest extends TestUtil {
  @Ignore
  @Test
  public void testDoesNothing() {
  }
/*
  static final int NCLOUD=10;
  static final int NKEYS=1000000;
  @BeforeClass static public void setup() { stall_till_cloudsize(NCLOUD); }

  // Make a million keys-per-node.  Make sure they are all cached/shared on at
  // least one other node.  Time removing them all.  Can be network bound as
  // the million read/write/put/invalidates hit the wires.
  @Test @Ignore
  public void testMillionRemoveKeys() {
    long start = System.currentTimeMillis();

    // Compute home keys
    byte[] homes = new byte[NKEYS*NCLOUD];
    for( int i=0; i<homes.length; i++ )
      homes[i] = (byte)Key.make("Q"+i).home(H2O.CLOUD);
    final Key k = Key.make("homes");
    DKV.put(k,new Value(k,homes));
    start = logTime(start,"HOMEALL",NCLOUD);

    // Populate NKEYS locally
    new MRTask() {
      @Override protected void setupLocal() {
        byte[] homes = DKV.get(k).rawMem();
        final int sidx = H2O.SELF.index();
        long start = System.currentTimeMillis();
        for( int i=0; i<homes.length; i++ ) {
          if( homes[i]==sidx ) {
            String s = "Q"+i;
            Key k = Key.make(s);
            DKV.put(k,new Value(k,s),_fs);
          }
        }
        logTime(start, "PUT1 "+H2O.SELF, 1);
      }
    }.doAllNodes();
    start = logTime(start,"PUTALL",NCLOUD);

    // Force sharing at least once
    new MRTask() {
      @Override protected void setupLocal() {
        byte[] homes = DKV.get(k).rawMem();
        final int sidx = H2O.SELF.index();
        long start = System.currentTimeMillis();
        for( int i=0; i<homes.length; i++ )
          if( homes[i]==sidx )
            DKV.prefetch(Key.make("Q"+i+1));
        start = logTime(start, "PREFETCH1 "+H2O.SELF, 1);
        for( int i=0; i<homes.length; i++ )
          if( homes[i]==sidx )
            DKV.get(Key.make("Q"+i+1));
        logTime(start, "GET1 "+H2O.SELF, 1);
      }
    }.doAllNodes();
    start = logTime(start,"GETALL",NCLOUD);

    Futures fs = new Futures();
    for( int i=0; i<homes.length; i++ )
      DKV.remove(Key.make("Q"+i), fs);
    start = logTime(start,"REMALL_START",NCLOUD);
    fs.blockForPending();
    logTime(start,"REMALL_DONE",NCLOUD);

    DKV.remove(k);
  }

  private long logTime( long start, String msg, int ncloud ) {
    long now = System.currentTimeMillis();
    double d = (double)(now-start)/NKEYS/ncloud;
    System.out.println(msg+" "+d+" msec/op");
    return now;
  }
*/
}
