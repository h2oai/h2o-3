package hex.deeplearning;

import hex.FrameSplitter;
import water.TestUtil;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.util.FrameUtils;
import water.util.Log;

import java.util.*;

public class DeepLearningMissingTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void run() {
    long seed = 1234;

    DeepLearningModel mymodel = null;
    Frame train = null;
    Frame test = null;
    Frame data = null;
    DeepLearningModel.DeepLearningParameters p;
    Log.info("");
    Log.info("STARTING.");
    Log.info("Using seed " + seed);

    Map<DeepLearningModel.DeepLearningParameters.MissingValuesHandling,Double> sumErr = new TreeMap<>();

    StringBuilder sb = new StringBuilder();
    for (DeepLearningModel.DeepLearningParameters.MissingValuesHandling mvh :
            new DeepLearningModel.DeepLearningParameters.MissingValuesHandling[]{
            DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip,
            DeepLearningModel.DeepLearningParameters.MissingValuesHandling.MeanImputation })
    {
      double sumerr = 0;
      Map<Double,Double> map = new TreeMap<>();
      for (double missing_fraction : new double[]{0, 0.1, 0.25, 0.5, 0.75, 0.99}) {

        try {
          NFSFileVec  nfs = NFSFileVec.make(find_test_file("smalldata/junit/weather.csv"));
          data = ParseDataset.parse(Key.make("data.hex"), nfs._key);
          Log.info("FrameSplitting");
          // Create holdout test data on clean data (before adding missing values)
          FrameSplitter fs = new FrameSplitter(data, new double[]{0.75f});
          H2O.submitTask(fs);//.join();
          Frame[] train_test = fs.getResult();
          train = train_test[0];
          test = train_test[1];
          Log.info("Done...");

          // add missing values to the training data (excluding the response)
          if (missing_fraction > 0) {
            Frame frtmp = new Frame(null, train.names(), train.vecs());
            frtmp.remove(frtmp.numCols() - 1); //exclude the response
            new FrameUtils.MissingInserter(seed, missing_fraction).doAll(frtmp);
          }

          // Build a regularized DL model with polluted training data, score on clean validation set
          p = new DeepLearningModel.DeepLearningParameters();
          p._train = train._key;
          p._valid = test._key;
          p._response_column = train._names[train.numCols()-1];
          p._ignored_columns = new String[]{train._names[1],train._names[22]}; //only for weather data
          p._missing_values_handling = mvh;
          p._activation = DeepLearningModel.DeepLearningParameters.Activation.RectifierWithDropout;
          p._hidden = new int[]{200,200};
          p._l1 = 1e-5;
          p._input_dropout_ratio = 0.2;
          p._epochs = 10;
          p._quiet_mode = true;
          p._destination_key = Key.make();
          p._reproducible = true;
          p._seed = seed;
          DeepLearning dl = new DeepLearning(p);
          try {
            Log.info("Starting with " + missing_fraction * 100 + "% missing values added.");
            mymodel = dl.trainModel().get();
          } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(t);
          } finally {
            dl.remove();
          }

          // Extract the scoring on validation set from the model
          double err = mymodel.error();

          Log.info("Missing " + missing_fraction * 100 + "% -> Err: " + err);
          map.put(missing_fraction, err);
          sumerr += err;

        } catch(Throwable t) {
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
      sb.append("\nMethod: ").append(mvh.toString()).append("\n");
      sb.append("missing fraction --> Error\n");
      for (String s : Arrays.toString(map.entrySet().toArray()).split(",")) sb.append(s.replace("=", " --> ")).append("\n");
      sb.append('\n');
      sb.append("Sum Err: ").append(sumerr).append("\n");

      sumErr.put(mvh, sumerr);
    }
    Log.info(sb.toString());
    Assert.assertTrue(sumErr.get(DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip) > sumErr.get(DeepLearningModel.DeepLearningParameters.MissingValuesHandling.MeanImputation));
    Assert.assertTrue(sumErr.get(DeepLearningModel.DeepLearningParameters.MissingValuesHandling.MeanImputation) < 2);
  }
}

