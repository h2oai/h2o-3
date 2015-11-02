package hex.deeplearning;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.util.Log;

import java.io.File;

/**
 * Simple Deep Neural Network on MNIST
 * Note: requires './gradlew syncBigDataLaptop'
 *
 * 7 hours on i7 5820k to get to 0.91% test set error (or wait longer to get to world-record test set error: 0.83%)
 *
 *    Duration     Training Speed      Epochs   Samples  Training MSE  Training R^2  Training LogLoss  Training Classification Error  Validation MSE  Validation R^2  Validation LogLoss  Validation Classification Error
 * 6:59:29.885  2384.428 rows/sec  1000.26288  60015771       0.00005       0.99999           0.00016                        0.00010         0.00823         0.99902             0.05626                          0.00910
 * INFO: Confusion Matrix (vertical: actual; across: predicted):
 * INFO:          0    1    2    3   4   5   6    7   8    9  Error          Rate
 * INFO:      0 975    0    1    0   0   0   1    1   2    0 0.0051 =     5 / 980
 * INFO:      1   0 1131    0    1   0   0   2    0   1    0 0.0035 =   4 / 1,135
 * INFO:      2   0    0 1025    0   1   0   1    3   2    0 0.0068 =   7 / 1,032
 * INFO:      3   0    0    1 1004   0   0   0    3   2    0 0.0059 =   6 / 1,010
 * INFO:      4   1    0    0    0 970   0   5    0   0    6 0.0122 =    12 / 982
 * INFO:      5   2    0    0    2   0 885   2    0   1    0 0.0078 =     7 / 892
 * INFO:      6   3    3    0    0   1   2 948    0   1    0 0.0104 =    10 / 958
 * INFO:      7   1    2    5    0   0   0   0 1017   1    2 0.0107 =  11 / 1,028
 * INFO:      8   1    0    0    2   0   3   0    1 963    4 0.0113 =    11 / 974
 * INFO:      9   1    2    0    3   6   2   0    4   0  991 0.0178 =  18 / 1,009
 * INFO: Totals 984 1138 1032 1012 978 892 959 1029 973 1003 0.0091 = 91 / 10,000
 * INFO: Top-10 Hit Ratios:
 * INFO:  K  Hit Ratio
 * INFO:  1   0.990900
 * INFO:  2   0.998100
 * INFO:  3   0.999200
 * INFO:  4   0.999500
 * INFO:  5   0.999900
 * INFO:  6   0.999900
 * INFO:  7   1.000000
 * INFO:  8   1.000000
 * INFO:  9   1.000000
 * INFO: 10   1.000000
 */
public class DeepLearningMNIST extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test @Ignore public void run() {
    Scope.enter();
    try {
      File file = find_test_file("bigdata/laptop/mnist/train.csv.gz");
      File valid = find_test_file("bigdata/laptop/mnist/test.csv.gz");
      if (file != null) {
        NFSFileVec trainfv = NFSFileVec.make(file);
        Frame frame = ParseDataset.parse(Key.make(), trainfv._key);
        NFSFileVec validfv = NFSFileVec.make(valid);
        Frame vframe = ParseDataset.parse(Key.make(), validfv._key);
        DeepLearningParameters p = new DeepLearningParameters();

        // populate model parameters
        p._model_id = Key.make("dl_mnist_model");
        p._train = frame._key;
//        p._valid = vframe._key;
        p._response_column = "C785"; // last column is the response
        p._activation = DeepLearningParameters.Activation.RectifierWithDropout;
//        p._activation = DeepLearningParameters.Activation.MaxoutWithDropout;
        p._hidden = new int[]{80,80};
        p._input_dropout_ratio = 0.2;
        p._mini_batch_size = 1;
        p._train_samples_per_iteration = -1;
        p._score_duty_cycle = 0.4;
//        p._shuffle_training_data = true;
//        p._l1= 1e-5;
//        p._max_w2= 10;
        p._epochs = 1000*10*5./6;
        p._sparse = true; //faster as activations remain sparse

        // Convert response 'C785' to categorical (digits 1 to 10)
        int ci = frame.find("C785");
        Scope.track(frame.replace(ci, frame.vecs()[ci].toCategoricalVec())._key);
        Scope.track(vframe.replace(ci, vframe.vecs()[ci].toCategoricalVec())._key);
        DKV.put(frame);
        DKV.put(vframe);

        // speed up training
        p._adaptive_rate = true; //disable adaptive per-weight learning rate -> default settings for learning rate and momentum are probably not ideal (slow convergence)
        p._replicate_training_data = true; //avoid extra communication cost upfront, got enough data on each node for load balancing
        p._overwrite_with_best_model = true; //no need to keep the best model around
        p._classification_stop = -1;
        p._score_interval = 5; //score and print progress report (only) every 20 seconds
        p._score_training_samples = 10000; //only score on a small sample of the training set -> don't want to spend too much time scoring (note: there will be at least 1 row per chunk)

        DeepLearning dl = new DeepLearning(p);
        DeepLearningModel model = null;
        try {
          model = dl.trainModel().get();
        } catch (Throwable t) {
          t.printStackTrace();
          throw new RuntimeException(t);
        } finally {
          dl.remove();
          if (model != null) {
            model.delete();
          }
        }
      } else {
        Log.info("Please run ./gradlew syncBigDataLaptop in the top-level directory of h2o-3.");
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      Scope.exit();
    }
  }
}
