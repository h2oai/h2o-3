package hex.tree.gbm;

import hex.FrameSplitter;
import hex.ModelMetricsBinomial;
import hex.ScoringInfo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.util.FrameUtils;
import static water.util.FrameUtils.generateNumKeys;
import water.util.Log;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class GBMMissingTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void run() {
    long seed = 1234;

    GBMModel mymodel = null;
    Frame train = null;
    Frame test = null;
    Frame data = null;
    GBMModel.GBMParameters p;
    Log.info("");
    Log.info("STARTING.");
    Log.info("Using seed " + seed);

    StringBuilder sb = new StringBuilder();
    double sumerr = 0;
    Map<Double,Double> map = new TreeMap<>();
    for (double missing_fraction : new double[]{0, 0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 0.99}) {

      double err=0;
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

        // Build a regularized GBM model with polluted training data, score on clean validation set
        p = new GBMModel.GBMParameters();
        p._train = train._key;
        p._valid = test._key;
        p._response_column = train._names[train.numCols()-1];
        p._ignored_columns = new String[]{train._names[1],train._names[22]}; //only for weather data
        p._seed = seed;

        // Convert response to categorical
        int ri = train.numCols()-1;
        int ci = test.find(p._response_column);
        Scope.track(train.replace(ri, train.vecs()[ri].toCategoricalVec()));
        Scope.track(test .replace(ci, test.vecs()[ci].toCategoricalVec()));
        DKV.put(train);
        DKV.put(test);

        GBM gbm = new GBM(p);
        Log.info("Starting with " + missing_fraction * 100 + "% missing values added.");
        mymodel = gbm.trainModel().get();

        // Extract the scoring on validation set from the model
        err = ((ModelMetricsBinomial)mymodel._output._validation_metrics).logloss();

        Frame train_preds = mymodel.score(train);
        Assert.assertTrue(mymodel.testJavaScoring(train, train_preds, 1e-15));
        train_preds.remove();

        Log.info("Missing " + missing_fraction * 100 + "% -> logloss: " + err);
      } catch(Throwable t) {
        t.printStackTrace();
        err = 100;
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
      map.put(missing_fraction, err);
      sumerr += err;
    }
    sb.append("missing fraction --> Error\n");
    for (String s : Arrays.toString(map.entrySet().toArray()).split(",")) sb.append(s.replace("=", " --> ")).append("\n");
    sb.append('\n');
    sb.append("Sum Err: ").append(sumerr).append("\n");
    Log.info(sb.toString());
  }
}

