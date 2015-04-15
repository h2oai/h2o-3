package water;

import org.junit.*;

import water.fvec.Vec;

public class OOMTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testClean() {
    final int log_chks = 4;
    final int nchks = 1<<4;
    Vec vcon = Vec.makeCon(0,1024,log_chks); // New vector, 1024/(1<<4) = 64 chunk
    Vec vrnd1 = vcon.makeRand(0x123456L); // Same shape as above, but rand fill
    Vec vrnd2 = vcon.makeRand(0x123456L); // Same shape as above, but rand fill
    byte[] buf = new byte[1000000];
    Key key = Key.make("test");
    Value val = new Value(key,buf);
    DKV.put(key,val);

    // Flag as "last touched a long time ago"
    long ago = System.currentTimeMillis()-1000L*1000L;
    val.touchAt(ago);
    for( int i=0; i<nchks; i++ )
      DKV.get(vrnd1.chunkKey(i)).touchAt(ago);

    Cleaner.dirty_store(ago);
    Cleaner.kick_store_cleaner();
    Cleaner.block_store_cleaner();
    Assert.assertTrue(Cleaner.dirty() != ago);
    

  }

}
