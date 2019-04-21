package water.fvec;

import static org.junit.Assert.*;
import org.junit.*;

import water.*;
import water.parser.ParseDataset;
import water.util.FileUtils;
import water.util.FrameUtils;
import water.util.Log;

public class RebalanceDatasetTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }
  @Test public void testProstate(){
    NFSFileVec[] nfs = new NFSFileVec[]{
            TestUtil.makeNfsFileVec("smalldata/logreg/prostate.csv"),
        TestUtil.makeNfsFileVec("smalldata/covtype/covtype.20k.data"),
        TestUtil.makeNfsFileVec("smalldata/chicago/chicagoCrimes10k.csv.zip")};
            //NFSFileVec.make(find_test_file("bigdata/laptop/usecases/cup98VAL_z.csv"))};
    for (NFSFileVec fv : nfs) {
      Frame fr = ParseDataset.parse(Key.make(), fv._key);
      Key rebalancedKey = Key.make("rebalanced");
      int[] trials = {380, 1, 3, 8, 9, 12, 256, 16, 32, 64, 11, 13};
      for (int i : trials) {
        Frame rebalanced = null;
        try {
          Scope.enter();
          RebalanceDataSet rb = new RebalanceDataSet(fr, rebalancedKey, i);
          H2O.submitTask(rb);
          rb.join();
          rebalanced = DKV.get(rebalancedKey).get();
          ParseDataset.logParseResults(rebalanced);
          assertEquals(rebalanced.numRows(), fr.numRows());
          assertEquals(rebalanced.anyVec().nChunks(), i);
          assertTrue(TestUtil.isIdenticalUpToRelTolerance(fr, rebalanced, 1e-10));
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

  @Test
  public void testEmptyPubDev5873() {
     Scope.enter();
     try {
       final Frame f = new TestFrameBuilder()
               .withName("testFrame")
               .withColNames("ColA")
               .withVecTypes(Vec.T_NUM)
               .withDataForCol(0, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7))
               .withChunkLayout(2, 2, 0, 0, 2, 1)
               .build();
       Scope.track(f);
       assertEquals(0, f.anyVec().chunkForChunkIdx(2)._len);
       Key<Frame> key = Key.make("rebalanced");
       RebalanceDataSet rb = new RebalanceDataSet(f, key, 3);
       H2O.submitTask(rb);
       rb.join();
       Frame rebalanced = key.get();
       assertNotNull(rebalanced); // no exception, successful completion
       Scope.track(rebalanced);
     } finally {
       Scope.exit();
     }
  }

}
