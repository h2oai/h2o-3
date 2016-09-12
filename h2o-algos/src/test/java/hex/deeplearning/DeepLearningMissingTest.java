package hex.deeplearning;

import hex.FrameSplitter;
import water.TestUtil;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
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

import static water.util.FrameUtils.generateNumKeys;

public class DeepLearningMissingTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void run() {
    long seed = 1234;

    DeepLearningModel mymodel = null;
    Frame train = null;
    Frame test = null;
    Frame data = null;
    DeepLearningParameters p;
    Log.info("");
    Log.info("STARTING.");
    Log.info("Using seed " + seed);

    Map<DeepLearningParameters.MissingValuesHandling,Double> sumErr = new TreeMap<>();

    StringBuilder sb = new StringBuilder();
    for (DeepLearningParameters.MissingValuesHandling mvh :
            new DeepLearningParameters.MissingValuesHandling[]{
                    DeepLearningParameters.MissingValuesHandling.MeanImputation,
                    DeepLearningParameters.MissingValuesHandling.Skip
            })
    {
      double sumloss = 0;
      Map<Double,Double> map = new TreeMap<>();
      for (double missing_fraction : new double[]{0, 0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99}) {

        double loss =0;
        try {
          Scope.enter();
          NFSFileVec  nfs = NFSFileVec.make(find_test_file("smalldata/junit/weather.csv"));
          data = ParseDataset.parse(Key.make("data.hex"), nfs._key);
          Log.info("FrameSplitting");
          // Create holdout test data on clean data (before adding missing values)
          FrameSplitter fs = new FrameSplitter(data, new double[]{0.75f}, generateNumKeys(data._key,2), null);
          H2O.submitTask(fs);//.join();
          Frame[] train_test = fs.getResult();
          train = train_test[0];
          test = train_test[1];
          Log.info("Done...");

          // add missing values to the training data (excluding the response)
          if (missing_fraction > 0) {
            Frame frtmp = new Frame(Key.<Frame>make(), train.names(), train.vecs());
            frtmp.remove(frtmp.numCols() - 1); //exclude the response
            DKV.put(frtmp._key, frtmp); //need to put the frame (to be modified) into DKV for MissingInserter to pick up
            FrameUtils.MissingInserter j = new FrameUtils.MissingInserter(frtmp._key, seed, missing_fraction);
            j.execImpl().get(); //MissingInserter is non-blocking, must block here explicitly
            DKV.remove(frtmp._key); //Delete the frame header (not the data)
          }

          // Build a regularized DL model with polluted training data, score on clean validation set
          p = new DeepLearningParameters();
          p._train = train._key;
          p._valid = test._key;
          p._response_column = train._names[train.numCols()-1];
          p._ignored_columns = new String[]{train._names[1],train._names[22]}; //only for weather data
          p._missing_values_handling = mvh;
          p._loss = DeepLearningParameters.Loss.ModifiedHuber;
          p._activation = DeepLearningParameters.Activation.Rectifier;
          p._hidden = new int[]{50,50};
          p._l1 = 1e-5;
          p._input_dropout_ratio = 0.2;
          p._epochs = 3;
          p._reproducible = true;
          p._seed = seed;
          p._elastic_averaging = false;

          // Convert response to categorical
          int ri = train.numCols()-1;
          int ci = test.find(p._response_column);
          Scope.track(train.replace(ri, train.vecs()[ri].toCategoricalVec()));
          Scope.track(test .replace(ci, test.vecs()[ci].toCategoricalVec()));
          DKV.put(train);
          DKV.put(test);

          DeepLearning dl = new DeepLearning(p);
          Log.info("Starting with " + missing_fraction * 100 + "% missing values added.");
          mymodel = dl.trainModel().get();

          // Extract the scoring on validation set from the model
          loss = mymodel.loss();

          Log.info("Missing " + missing_fraction * 100 + "% -> logloss: " + loss);
        } catch(Throwable t) {
          t.printStackTrace();
          loss = 100;
        } finally {
          Scope.exit();
          // cleanup
          if (mymodel != null) {
            mymodel.delete();
          }
          if (train != null) train.delete();
          if (test != null) test.delete();
          if (data != null) data.delete();
        }
        map.put(missing_fraction, loss);
        sumloss += loss;
      }
      sb.append("\nMethod: ").append(mvh.toString()).append("\n");
      sb.append("missing fraction --> loss\n");
      for (String s : Arrays.toString(map.entrySet().toArray()).split(",")) sb.append(s.replace("=", " --> ")).append("\n");
      sb.append('\n');
      sb.append("sum loss: ").append(sumloss).append("\n");

      sumErr.put(mvh, sumloss);
    }
    Log.info(sb.toString());
    Assert.assertEquals(406.1049, sumErr.get(DeepLearningParameters.MissingValuesHandling.Skip), 1e-2);
    Assert.assertEquals(8.703970, sumErr.get(DeepLearningParameters.MissingValuesHandling.MeanImputation), 1e-3);
  }
}

