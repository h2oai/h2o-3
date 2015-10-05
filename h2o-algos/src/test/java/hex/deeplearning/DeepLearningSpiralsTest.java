package hex.deeplearning;

import hex.ModelMetricsBinomial;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.util.Log;

public class DeepLearningSpiralsTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void run() {
    Scope.enter();
    NFSFileVec  nfs = NFSFileVec.make(find_test_file("smalldata/junit/two_spiral.csv"));
    Frame frame = ParseDataset.parse(Key.make(), nfs._key);
    Log.info(frame);
    int resp = frame.names().length-1;

    Key dest = Key.make("spirals2");

    for (boolean sparse : new boolean[]{false}) {
      for (boolean col_major : new boolean[]{false}) {
        if (!sparse && col_major) continue;

        // build the model
        {
          DeepLearningParameters p = new DeepLearningParameters();
          p._seed = 0xbabefff;
          p._epochs = 600;
          p._hidden = new int[]{100};
          p._sparse = sparse;
          p._col_major = col_major;
          p._elastic_averaging = false;
          p._activation = DeepLearningParameters.Activation.Tanh;
          p._max_w2 = Float.POSITIVE_INFINITY;
          p._l1 = 0;
          p._l2 = 0;
          p._initial_weight_distribution = DeepLearningParameters.InitialWeightDistribution.Normal;
          p._initial_weight_scale = 2.5;
          p._loss = DeepLearningParameters.Loss.CrossEntropy;
          p._train = frame._key;
          p._response_column = frame.names()[resp];
          Scope.track(frame.replace(resp, frame.vecs()[resp].toCategoricalVec())._key); // Convert response to categorical
          DKV.put(frame);
          p._valid = null;
          p._score_interval = 2;
          p._train_samples_per_iteration = 0; //sync once per period
//          p._quiet_mode = true;
          p._fast_mode = true;
          p._ignore_const_cols = true;
          p._nesterov_accelerated_gradient = true;
          p._score_training_samples = 1000;
          p._score_validation_samples = 10000;
          p._shuffle_training_data = false;
          p._force_load_balance = false;
          p._replicate_training_data = false;
          p._model_id = dest;
          p._adaptive_rate = true;
          p._reproducible = true;
          p._rho = 0.99;
          p._epsilon = 5e-3;
          DeepLearning dl = new DeepLearning(p);
          try {
            dl.trainModel().get();
          } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
          } finally {
            dl.remove();
          }
        }

        // score and check result
        {
          DeepLearningModel mymodel = DKV.getGet(dest);
          Frame pred = mymodel.score(frame);
          ModelMetricsBinomial mm = ModelMetricsBinomial.getFromDKV(mymodel,frame);
          double error = mm._auc.defaultErr();
          Log.info("Error: " + error);
          if (error > 0) {
            Assert.fail("Classification error is not 0, but " + error + ".");
          }
          pred.delete();
          mymodel.delete();
        }
      }
    }
    frame.delete();
    Scope.exit();
  }
}
