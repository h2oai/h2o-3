package hex.deeplearning;

import static hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset2;
import water.util.Log;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class DeepLearningReproducibilityTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(2); }

  @Test
  public void run() {
    long seed = new Random().nextLong();

    DeepLearningModel mymodel = null;
    Frame train = null;
    Frame test = null;
    Frame data = null;
    Log.info("");
    Log.info("STARTING.");
    Log.info("Using seed " + seed);

    Map<Integer,Float> repeatErrs = new TreeMap<>();

    int N = 6;
    StringBuilder sb = new StringBuilder();
    float repro_error = 0;
    for (boolean repro : new boolean[]{true, false}) {
      Frame[] preds = new Frame[N];
      for (int repeat = 0; repeat < N; ++repeat) {
        try {
          NFSFileVec file = NFSFileVec.make(find_test_file("smalldata/weather/weather.csv"));
          data = ParseDataset2.parse(Key.make("data.hex"), file._key);

          // Create holdout test data on clean data (before adding missing values)
          train = data;
          test = data;

          // Build a regularized DL model with polluted training data, score on clean validation set
          DeepLearningParameters p = new DeepLearningParameters();

          p._training_frame = train._key;
          p._validation_frame = test._key;
          p._destination_key = Key.make();
          p._response_column = train.names()[train.names().length-1];
          p.ignored_columns = new String[]{"EvapMM", "RISK_MM"}; //for weather data
          p.activation = DeepLearningParameters.Activation.RectifierWithDropout;
          p.hidden = new int[]{32, 58};
          p.l1 = 1e-5;
          p.l2 = 3e-5;
          p.seed = 0xbebe;
          p.input_dropout_ratio = 0.2;
          p.hidden_dropout_ratios = new double[]{0.4, 0.1};
          p.epochs = 3.32;
          p.quiet_mode = true;
          p.reproducible = repro;
          DeepLearning dl = new DeepLearning(p);
          try {
            mymodel = dl.train().get();
          } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
          } finally {
            dl.remove();
          }

          // Extract the scoring on validation set from the model
          mymodel = DKV.get(p._destination_key).get();
          preds[repeat] = mymodel.score(test);
          repeatErrs.put(repeat, mymodel.error());

        } catch (Throwable t) {
          t.printStackTrace();
          throw new RuntimeException(t);
        } finally {
          // cleanup
          if (mymodel != null) {
            mymodel.delete_xval_models();
            mymodel.delete_best_model();
            mymodel.delete();
          }
          if (train != null) train.delete();
          if (test != null) test.delete();
          if (data != null) data.delete();
        }
      }
      sb.append("Reproducibility: ").append(repro ? "on" : "off").append("\n");
      sb.append("Repeat # --> Validation Error\n");
      for (String s : Arrays.toString(repeatErrs.entrySet().toArray()).split(","))
        sb.append(s.replace("=", " --> ")).append("\n");
      sb.append('\n');
      Log.info(sb.toString());

      try {
        if (repro) {
          // check reproducibility
          for (Float error : repeatErrs.values()) {
            assertTrue(error.equals(repeatErrs.get(0)));
          }
          // exposes bug: no work gets done on remote if frame has only 1 chunk and is homed remotely.
//          for (Frame f : preds) {
//            assertTrue(TestUtil.isBitIdentical(f, preds[0]));
//          }
          repro_error = repeatErrs.get(0);
        } else {
          // check standard deviation of non-reproducible mode
          double mean = 0;
          for (Float error : repeatErrs.values()) {
            mean += error;
          }
          mean /= N;
          Log.info("mean error: " + mean);
          double stddev = 0;
          for (Float error : repeatErrs.values()) {
            stddev += (error - mean) * (error - mean);
          }
          stddev /= N;
          stddev = Math.sqrt(stddev);
          Log.info("standard deviation: " + stddev);
          assertTrue(stddev < 0.1 / Math.sqrt(N));
          Log.info("difference to reproducible mode: " + Math.abs(mean - repro_error) / stddev + " standard deviations");
        }
      } finally {
        for (Frame f : preds) if (f != null) f.delete();
      }
    }
  }
}
