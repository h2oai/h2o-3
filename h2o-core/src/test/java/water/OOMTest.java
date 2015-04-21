package water;

import org.junit.*;

import water.fvec.Frame;
import water.fvec.Vec;

@Ignore
public class OOMTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void testClean() throws InterruptedException {
    final int log_rows_per_chk = 6;
    final int nchks = 1024/(1<<log_rows_per_chk); // 1024/(1<<4) = 64 chunks
    Vec vcon = Vec.makeCon(0,1024,log_rows_per_chk); // New vector,
    Vec vrnd1 = vcon.makeRand(0x123456L); // Same shape as above, but rand fill
    Vec vrnd2 = vcon.makeRand(0x123456L); // Same shape as above, but rand fill
    vcon.remove();

    // Fast access to all Values, outside of the DKV
    Value val1s[] = new Value[nchks];
    Value val2s[] = new Value[nchks];
    for( int i=0; i<nchks; i++ ) {
      val1s[i] = vrnd1.chunkIdx(i);
      val2s[i] = vrnd2.chunkIdx(i);
    }

    // Flag val1s as "last touched a long time ago"
    long ago = System.currentTimeMillis()-1000L*1000L;
    for( int i=0; i<nchks; i++ )
      val1s[i].touchAt(ago);

    // Block until the Cleaner sleeps & take lock
    synchronized(Cleaner.THE_CLEANER) {

      // Sweep DKV, Cleaner should attempt to write out old chunks
      Cleaner.dirty_store(ago);
      Cleaner.kick_store_cleaner();
      Cleaner.block_for_test();

      Assert.assertTrue(Cleaner.dirty() != ago); // Cleaner is updated
      for( int i=0; i<nchks; i++ )
        Assert.assertTrue(val1s[i].isPersisted()); // Chunks all hit disk

      // Tell the Cleaner the "DESIRED" cache level is ZERO.  Then trigger
      // another Cleaner pass ... should write all out to disk.
      //long old_DESIRED = Cleaner.DESIRED;
      Cleaner.DESIRED = -1; // Flag: MemoryManager setGoals expects to force all out
      ago = System.currentTimeMillis()-1000L*1000L;
      // Sweep DKV, Cleaner should attempt to write out old chunks
      Cleaner.dirty_store(ago);
      Cleaner.kick_store_cleaner();
      Cleaner.block_for_test();

      Assert.assertTrue(Cleaner.dirty() != ago); // Cleaner is updated
      for( int i=0; i<nchks; i++ ) {
        Assert.assertTrue(val1s[i].isPersisted()); // Chunks all hit disk
        Assert.assertTrue(val1s[i].rawMem()==null);// Chunks all hit disk
        // Cannot assert on val2s - unless 5sec has past, they are too new to be written out.
        // So OS-schedluing specific on whether or not they hit disk
        Assert.assertTrue(val2s[i].isPersisted()); // Chunks all hit disk
        Assert.assertTrue(val2s[i].rawMem()==null);// Chunks all hit disk
      }
    }

    // Now touch all the data, forcing a reload.  Confirm all reads the same.
    boolean id = isBitIdentical(new Frame(new String[]{"C1"}, new Vec[]{vrnd1}),
                                new Frame(new String[]{"C1"}, new Vec[]{vrnd2}));
    Assert.assertTrue("Frames loaded from disk are equal", id);
    // All Chunks are recorded as being back-in-memory
    for( int i=0; i<nchks; i++ ) {
      Value v1 = vrnd1.chunkIdx(i);
      Value v2 = vrnd2.chunkIdx(i);
      Assert.assertTrue(v1.isPersisted());
      Assert.assertTrue(v2.isPersisted());
      Assert.assertTrue(v1.rawMem() != null || !v1._key.home());
      Assert.assertTrue(v2.rawMem() != null || !v2._key.home());
    }

    // Cleanup
    vrnd1.remove();
    vrnd2.remove();
  }
}
