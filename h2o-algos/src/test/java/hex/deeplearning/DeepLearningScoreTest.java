package hex.deeplearning;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.RebalanceDataSet;
import water.parser.ParseDataset;

/**
 * This test simulates environment
 * produced by Spark - dataset divided into
 * many small chunks,  some of theam are empty.
 */
public class DeepLearningScoreTest extends TestUtil {
  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  /** Load simple dataset, rebalance to a number of chunks > number of rows, and run deep learning */
  @Test @Ignore public void testPubDev928() {
    // Create rebalanced dataset
    Key rebalancedKey = Key.make("rebalanced");
    NFSFileVec nfs = NFSFileVec.make(find_test_file("smalldata/logreg/prostate.csv"));
    Frame fr = ParseDataset.parse(Key.make(), nfs._key);
    RebalanceDataSet rb = new RebalanceDataSet(fr, rebalancedKey, (int)(fr.numRows()+1));
    H2O.submitTask(rb);
    rb.join();
    Frame rebalanced = DKV.get(rebalancedKey).get();

    DeepLearningModel dlModel = null;
    try {
      // Launch Deep Learning
      DeepLearningModel.DeepLearningParameters dlParams = new DeepLearningModel.DeepLearningParameters();
      dlParams._train = rebalancedKey;
      dlParams._epochs = 5;
      dlParams._response_column = "CAPSULE";

      dlModel = new DeepLearning(dlParams).trainModel().get();
    } finally {
      fr.delete();
      rebalanced.delete();
      if (dlModel != null) dlModel.delete();
    }
  }
}
