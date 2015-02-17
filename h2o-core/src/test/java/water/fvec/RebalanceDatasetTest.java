package water.fvec;

import static org.junit.Assert.*;
import org.junit.*;

import water.*;
import water.parser.ParseDataset;
import water.util.FrameUtils;
import water.util.Log;

/** Created by tomasnykodym on 4/1/14. */
public class RebalanceDatasetTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(5); }
  @Test public void testProstate(){
    Key rebalancedKey = Key.make("rebalanced");
    for (int i=1; i<381; ++i) {
      Frame fr = null, rebalanced = null;
      try {
        NFSFileVec nfs = NFSFileVec.make(find_test_file("smalldata/logreg/prostate.csv"));
        fr = ParseDataset.parse(Key.make(), nfs._key);
        RebalanceDataSet rb = new RebalanceDataSet(fr, rebalancedKey, i);
        H2O.submitTask(rb);
        rb.join();
        rebalanced = DKV.get(rebalancedKey).get();
        assertEquals(rebalanced.numRows(), fr.numRows());
        assertEquals(rebalanced.anyVec().nChunks(), i);
        assertTrue(isBitIdentical(fr, rebalanced));
        Log.info("Rebalanced into " + i + " chunks:");
        Log.info(FrameUtils.chunkSummary(rebalanced).toString());
      }
      catch(Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      }
      finally {
        if (fr != null) fr.delete();
        if (rebalanced != null) rebalanced.delete();
      }
    }
  }
}
