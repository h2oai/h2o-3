package water.fvec;

import static org.junit.Assert.*;
import org.junit.*;

import water.*;
import water.parser.ParseDataset;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.Log;

import java.io.File;

public class RebalanceDatasetTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }
  @Test public void testProstate(){
    NFSFileVec[] nfs = new NFSFileVec[]{
            NFSFileVec.make(find_test_file("smalldata/logreg/prostate.csv")),
            NFSFileVec.make(find_test_file("smalldata/covtype/covtype.20k.data"))};
            //NFSFileVec.make(find_test_file("bigdata/laptop/usecases/cup98VAL_z.csv"))};
    for (NFSFileVec fv : nfs) {
      Frame fr = ParseDataset.parse(Key.make(), fv._key);
      Key rebalancedKey = Key.make("rebalanced");
      int[] trials = {380, 1, 3, 8, 12, 256, 16, 32, 64, 11, 13};
      for (int i : trials) {
        Frame rebalanced = null;
        try {
          Scope.enter();
          RebalanceDataSet rb = new RebalanceDataSet(fr, rebalancedKey, i);
          H2O.submitTask(rb);
          rb.join();
          rebalanced = DKV.get(rebalancedKey).get();
          assertEquals(rebalanced.numRows(), fr.numRows());
          assertEquals(rebalanced.anyVec().nChunks(), i);
          assertTrue(isBitIdentical(fr, rebalanced));
          Log.info("Rebalanced into " + i + " chunks:");
          Log.info(FrameUtils.chunkSummary(rebalanced).toString());
        } finally {
          if (rebalanced != null) rebalanced.delete();
          Scope.exit();
        }
      }
      if (fr != null) fr.delete();
    }
  }

}
