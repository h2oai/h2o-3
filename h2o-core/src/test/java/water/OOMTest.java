package water;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import org.junit.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FileUtils;
import water.util.Log;

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


  // too slow for standard junit
  // repeatedly throws OOM exception purpose, which breaks many things.
  // tested now in gradle via the custom main() below
  @Test @Ignore
  public void testParseMemoryStress() {
    // "bigdata directory is not always available"
    if( find_test_file_static("bigdata/laptop/usecases/cup98LRN_z.csv") == null ) return;
    if( find_test_file_static("bigdata/laptop/usecases/cup98VAL_z.csv") == null ) return;
    ArrayList<Frame> frames = new ArrayList<>();
    File ice = new File(water.H2O.ICE_ROOT.toString(),"ice" + water.H2O.API_PORT);
    String[] dirs = ice.list();
    Assert.assertTrue(dirs == null || dirs.length==0); // ICE empty before we start
    Assert.assertTrue(MemoryManager.MEM_MAX <= 1536L*1024L*1024L); // No more than 1.5Gig of heap; forces swapping
    try {
      // Force much swap-to-disk
      for( int i=0; i<4; i++ ) {
        frames.add(parse_test_file(Key.make("F" + frames.size()), "bigdata/laptop/usecases/cup98LRN_z.csv"));
        frames.add(parse_test_file(Key.make("F" + frames.size()), "bigdata/laptop/usecases/cup98VAL_z.csv"));
      }
    } finally {
      dirs = ice.list();
      Assert.assertNotNull("Swap directory not created; no swapping happened; test failed to stresss enough",dirs);
      Assert.assertTrue(dirs.length>0); // Much got swapped to disk
      Log.info("Deleting swap files at test end");
      for( Frame fr : frames )
        fr.delete();            // Cleanup swap-to-disk
    }
    // Assert nothing remains
    dirs = ice.list();
    if (dirs.length > 0) {
      Log.info("Remaining swap files at test end:");
      for (String dir : dirs) { Log.info(dir); }
    }
    Assert.assertTrue(dirs.length==0);
  }

  public static void main(String[] args) {
    stall_till_cloudsize(args, 1);
    try {
      new OOMTest().testParseMemoryStress();    // Throws on assertion error
    } catch( Throwable e ) {
      Log.err(e);
      StringWriter sw = new StringWriter();
      e.printStackTrace(new PrintWriter(sw));
      Log.err(sw);
      System.exit(-1);
    }
    System.exit(0);
  }
}
