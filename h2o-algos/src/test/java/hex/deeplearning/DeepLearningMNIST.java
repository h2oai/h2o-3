package hex.deeplearning;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.util.Log;

import java.io.File;

import static hex.deeplearning.DeepLearningModel.DeepLearningParameters;

/**
 * Simple Deep Neural Network on MNIST with ~12M parameters
 * ~50MB model size, won't fit in L3 cache -> main memory bandwidth (fprop) and latency (bprop) limited
 * Note: requires './gradlew syncBigDataLaptop'
 */
public class DeepLearningMNIST extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }

  @Test @Ignore public void run() {
    Scope.enter();
    try {
      File file = find_test_file("bigdata/laptop/mnist/train.csv.gz");
      if (file != null) {
        NFSFileVec trainfv = NFSFileVec.make(file);
        Frame frame = ParseDataset.parse(Key.make(), trainfv._key);
        DeepLearningParameters p = new DeepLearningParameters();

        // populate model parameters
        p._destination_key = Key.make("dl_mnist_model");
        p._train = frame._key;
        p._response_column = "C785"; //last column is the response
        p._convert_to_enum = true; //response is categorical (digits 1 to 10)
        p._activation = DeepLearningParameters.Activation.Tanh;
        p._hidden = new int[]{2500, 2000, 1500, 1000, 500};
        p._train_samples_per_iteration = 1500 * H2O.getCloudSize(); //process 1500 rows per node per map-reduce step
        p._epochs = 1.8 * (float) p._train_samples_per_iteration / frame.numRows(); //train long enough to do 2 map-reduce passes (with scoring each time)

        // speed up training
        p._adaptive_rate = false; //disable adaptive per-weight learning rate -> default settings for learning rate and momentum are probably not ideal (slow convergence)
        p._replicate_training_data = false; //avoid extra communication cost upfront, got enough data on each node for load balancing
        p._override_with_best_model = false; //no need to keep the best model around
        p._diagnostics = false; //no need to compute statistics during training
        p._score_interval = 20; //score and print progress report (only) every 20 seconds
        p._score_training_samples = 50; //only score on a small sample of the training set -> don't want to spend too much time scoring (note: there will be at least 1 row per chunk)

        DeepLearning dl = new DeepLearning(p);
        DeepLearningModel model = null;
        try {
          model = dl.trainModel().get();
          if (model != null) {
            Assert.assertTrue(1000. * model.model_info().get_processed_total() / model.run_time > 20); //we expect at least a training speed of 20 samples/second (MacBook Pro: ~50 samples/second)
          }
        } catch (Throwable t) {
          t.printStackTrace();
          throw new RuntimeException(t);
        } finally {
          dl.remove();
          if (model != null) {
            model.delete_xval_models();
            model.delete();
          }
        }
      } else {
        Log.info("Please run ./gradlew syncBigDataLaptop in the top-level directory of h2o-dev.");
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      Scope.exit();
    }
  }
}
