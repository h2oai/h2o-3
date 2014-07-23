package water.fvec;

import static org.junit.Assert.*;
import org.junit.*;

import water.*;
import water.parser.ParseDataset2;

/** Created by tomasnykodym on 4/1/14. */
public class RebalanceDatasetTest extends TestUtil {
  @Test public void testProstate(){
    Key rebalancedKey = Key.make("rebalanced");
    NFSFileVec nfs = NFSFileVec.make(find_test_file("smalldata/logreg/prostate.csv"));
    Frame fr = null, rebalanced = null;
    try{
      fr = ParseDataset2.parse(Key.make(), nfs._key);
      RebalanceDataSet rb = new RebalanceDataSet(fr,rebalancedKey,300);
      H2O.submitTask(rb);
      rb.join();
      rebalanced = DKV.get(rebalancedKey).get();
      assertEquals(rebalanced.numRows(),fr.numRows());
      assertEquals(rebalanced.anyVec().nChunks(),300);
      assertTrue(isBitIdentical(fr,rebalanced));
    } finally {
      if( fr != null ) fr.delete();
      if( rebalanced != null ) rebalanced.delete();
    }
  }
}
