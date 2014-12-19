package water.parser;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.Ignore;
import java.io.File;
import water.*;
import water.fvec.*;

public class ParseExceptionTest extends TestUtil {
  static public void setup() { stall_till_cloudsize(1); }

  @Test @Ignore public void testParserRecoversFromException() {
    Throwable ex = null;
    Key fkey0=null,fkey1=null,fkey2=null,okey=null;
    try {
      okey = Key.make("junk.hex");

      fkey0 = NFSFileVec.make(new File("smalldata/junit/parse_folder/prostate_0.csv"))._key;
      fkey1 = NFSFileVec.make(new File("smalldata/junit/parse_folder/prostate_1.csv"))._key;
      fkey2 = NFSFileVec.make(new File("smalldata/junit/parse_folder/prostate_2.csv"))._key;
      // Now "break" one of the files.  Globally
      new Break(fkey1).doAllNodes();

      ParseDataset.parse(okey, fkey0, fkey1, fkey2);

    } catch( Throwable e2 ) {
      ex = e2; // Record expected exception
    }
    try {
      // Cleanup is buggy, in the other JVMs run-on, and produce more output
      // keys even after the job is canceled.  Sleep till they hopefully
      // shutdown, then remove keys.
      System.out.print(H2O.STOREtoString());
      try { Thread.sleep(5000); } catch( InterruptedException ignore ) { }
      Value v = DKV.get(fkey0);
      if( v != null ) {
        NFSFileVec nfs = v.get();
        System.out.println(nfs.toString());
      }
      System.out.print(H2O.STOREtoString());
      assertTrue( "Parse should throw an NPE",ex!=null);
      assertTrue( "All input & output keys not removed", DKV.get(fkey0)==null );
      assertTrue( "All input & output keys not removed", DKV.get(fkey1)==null );
      assertTrue( "All input & output keys not removed", DKV.get(fkey2)==null );
      assertTrue( "All input & output keys not removed", DKV.get(okey )==null );
      // Try again, in the same test, same inputs & outputs but not broken.
      // Should recover completely.
      okey = Key.make("junk.hex");
      fkey0 = NFSFileVec.make(new File("smalldata/junit/parse_folder/prostate_0.csv"))._key;
      fkey1 = NFSFileVec.make(new File("smalldata/junit/parse_folder/prostate_1.csv"))._key;
      fkey2 = NFSFileVec.make(new File("smalldata/junit/parse_folder/prostate_2.csv"))._key;
      Frame fr = ParseDataset.parse(okey, fkey0, fkey1, fkey2);
      fr.delete();
      
      assertTrue( "All input & output keys not removed", DKV.get(fkey0)==null );
      assertTrue( "All input & output keys not removed", DKV.get(fkey1)==null );
      assertTrue( "All input & output keys not removed", DKV.get(fkey2)==null );
      assertTrue( "All input & output keys not removed", DKV.get(okey )==null );
    } finally {
      Keyed.remove(fkey0);
      Keyed.remove(fkey1);
      Keyed.remove(fkey2);
    }
  }

  private static class Break extends MRTask<Break> {
    final Key _key;
    Break(Key key ) { _key = key; }
    @Override public void setupLocal() {
      Vec vec = DKV.get(_key).get();
      Chunk chk = vec.chunkForChunkIdx(0); // Load the chunk (which otherwise loads only lazily)
      chk.crushBytes(); // Illegal setup: Chunk _mem should never be null; will trigger NPE
      tryComplete();
    }
  }

}
