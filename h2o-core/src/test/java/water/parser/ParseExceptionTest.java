package water.parser;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import java.io.File;
import water.*;
import water.fvec.*;

public class ParseExceptionTest extends TestUtil {
  ParseExceptionTest() { super(3); }

  @Test public void testParserRecoversFromException() {
    Throwable ex = null;
    Key fkey0=null,fkey1=null,fkey2=null,okey=null;
    try {
      okey = Key.make("junk.hex");

      fkey0 = NFSFileVec.make(new File("smalldata/parse_folder_test/prostate_0.csv"))._key;
      fkey1 = NFSFileVec.make(new File("smalldata/parse_folder_test/prostate_1.csv"))._key;
      fkey2 = NFSFileVec.make(new File("smalldata/parse_folder_test/prostate_2.csv"))._key;
      // Now "break" one of the files.  Globally
      new Break(fkey1).doAllNodes();

      water.parser.ParseDataset2.parse(okey, fkey0,fkey1,fkey2);

    } catch( Throwable e2 ) {
      ex = e2; // Record expected exception
    }
    assertTrue( "Parse should throw an NPE",ex!=null);
    assertTrue( "All input & output keys not removed", DKV.get(fkey0)==null );
    assertTrue( "All input & output keys not removed", DKV.get(fkey1)==null );
    assertTrue( "All input & output keys not removed", DKV.get(fkey2)==null );
    assertTrue( "All input & output keys not removed", DKV.get(okey )==null );

    // Try again, in the same test, same inputs & outputs but not broken.
    // Should recover completely.
    okey = Key.make("junk.hex");
    fkey0 = NFSFileVec.make(new File("smalldata/parse_folder_test/prostate_0.csv"))._key;
    fkey1 = NFSFileVec.make(new File("smalldata/parse_folder_test/prostate_1.csv"))._key;
    fkey2 = NFSFileVec.make(new File("smalldata/parse_folder_test/prostate_2.csv"))._key;
    Frame fr = water.parser.ParseDataset2.parse(okey, fkey0,fkey1,fkey2);
    fr.delete();

    assertTrue( "All input & output keys not removed", DKV.get(fkey0)==null );
    assertTrue( "All input & output keys not removed", DKV.get(fkey1)==null );
    assertTrue( "All input & output keys not removed", DKV.get(fkey2)==null );
    assertTrue( "All input & output keys not removed", DKV.get(okey )==null );
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
