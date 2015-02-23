package water.fvec;

import static org.junit.Assert.*;
import org.junit.*;

import water.*;
import water.parser.ParseDataset;
import water.util.FrameUtils;
import water.util.Log;

/** Created by tomasnykodym on 4/1/14. */
public class RebalanceDatasetTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(10); }
  @Test public void testProstate(){
    Key rebalancedKey = Key.make("rebalanced");
    int [] trials = { 80 };
    for (int k=0; k<trials.length; ++k) {
      int i = trials[k];
      Frame fr = null, rebalanced = null;
      try {
        NFSFileVec nfs = NFSFileVec.make(find_test_file("bigdata/laptop/mnist/train.csv.gz"));
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
