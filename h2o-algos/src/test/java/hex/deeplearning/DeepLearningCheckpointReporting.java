package hex.deeplearning;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
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
import water.util.TwoDimTable;

import java.io.File;

public class DeepLearningCheckpointReporting extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void run() {
    Scope.enter();
    Frame frame = null;
    try {
      File file = find_test_file("smalldata/logreg/prostate.csv");
      NFSFileVec trainfv = NFSFileVec.make(file);
      frame = ParseDataset.parse(Key.make(), trainfv._key);
      DeepLearningParameters p = new DeepLearningParameters();

      // populate model parameters
      p._model_id = Key.make("first_model");
      p._train = frame._key;
      p._response_column = "CAPSULE"; // last column is the response
      p._activation = DeepLearningParameters.Activation.Rectifier;
      p._epochs = 8;
      p._train_samples_per_iteration = -1;
      p._score_duty_cycle = 1;
      p._score_interval = 0;
      p._overwrite_with_best_model = false;
      p._classification_stop = -1;
      p._seed = 1234;
      p._reproducible = true;

      // Convert response 'C785' to categorical (digits 1 to 10)
      int ci = frame.find("CAPSULE");
      Scope.track(frame.replace(ci, frame.vecs()[ci].toCategoricalVec())._key);
      DKV.put(frame);

      long start = System.currentTimeMillis();
      Thread.sleep(1000); //to avoid rounding issues with printed time stamp (1 second resolution)

      DeepLearning dl = new DeepLearning(p);
      DeepLearningModel model = null;
      try {
        model = dl.trainModel().get();
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        dl.remove();
      }
      long sleepTime = 3; //seconds
      Thread.sleep(sleepTime*1000);

      // checkpoint restart after sleep
      DeepLearningParameters p2 = (DeepLearningParameters)p.clone();
      p2._checkpoint = p2._model_id;
      p2._model_id = Key.make("second_model");
      p2._epochs *= 2;
      DeepLearning dl2 = new DeepLearning(p2);
      DeepLearningModel model2 = null;
      try {
        model2 = dl2.trainModel().get();
        long end = System.currentTimeMillis();
        TwoDimTable table = model2._output._scoring_history;
        double priorDurationDouble=0;
        long priorTimeStampLong=0;
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        for (int i=0; i<table.getRowDim(); ++i) {

          // Check that timestamp is correct, and growing monotonically
          String timestamp = (String)table.get(i,0);
          long timeStampLong = fmt.parseMillis(timestamp);
          Assert.assertTrue("Timestamp must be later than outside timer start", timeStampLong >= start);
          Assert.assertTrue("Timestamp must be earlier than outside timer end", timeStampLong <= end);
          Assert.assertTrue("Timestamp must increase", timeStampLong >= priorTimeStampLong);
          priorTimeStampLong = timeStampLong;

          // Check that duration is growing monotonically
          String duration = (String)table.get(i,1);
          duration = duration.substring(0, duration.length()-4); //"x.xxxx sec"
          try {
            double durationDouble = Double.parseDouble(duration);
            Assert.assertTrue("Duration must be >0: " + durationDouble, durationDouble >= 0);
            Assert.assertTrue("Duration must increase: " + priorDurationDouble + " -> " + durationDouble, durationDouble >= priorDurationDouble);
            Assert.assertTrue("Duration cannot be more than outside timer delta", durationDouble <= (end - start) / 1e3);
            priorDurationDouble = durationDouble;
          } catch(NumberFormatException ex) {
            //skip
          }

          // Check that epoch counting is good
          Assert.assertTrue("Epoch counter must be contiguous", (Double)table.get(i,3) == i); //1 epoch per step
        }
        // Check that duration doesn't see the sleep
        String durationBefore = (String)table.get((int)(p._epochs),1);
        durationBefore = durationBefore.substring(0, durationBefore.length()-4);
        String durationAfter = (String)table.get((int)(p._epochs+1),1);
        durationAfter = durationAfter.substring(0, durationAfter.length()-4);
        Assert.assertTrue("Duration must be smooth", Double.parseDouble(durationAfter) - Double.parseDouble(durationBefore) < sleepTime);

        // Check that time stamp does see the sleep
        String timeStampBefore = (String)table.get((int)(p._epochs),0);
        long timeStampBeforeLong = fmt.parseMillis(timeStampBefore);
        String timeStampAfter = (String)table.get((int)(p._epochs+1),0);
        long timeStampAfterLong = fmt.parseMillis(timeStampAfter);
        Assert.assertTrue("Time stamp must experience a delay", timeStampAfterLong-timeStampBeforeLong >= (sleepTime-1/*rounding*/)*1000);

        // Check that the training speed is similar before and after checkpoint restart
        String speedBefore = (String)table.get((int)(p._epochs),2);
        speedBefore = speedBefore.substring(0, speedBefore.length()-9);
        double speedBeforeDouble = Double.parseDouble(speedBefore);
        String speedAfter = (String)table.get((int)(p._epochs+1),2);
        speedAfter = speedAfter.substring(0, speedAfter.length()-9);
        double speedAfterDouble = Double.parseDouble(speedAfter);
        Assert.assertTrue("Speed shouldn't change more than 30%", Math.abs(speedAfterDouble-speedBeforeDouble)/speedBeforeDouble < 0.3); //expect less than 30% change in speed

      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        dl2.remove();
        if (model != null) {
          model.delete();
        }
        if (model2 != null) {
          model2.delete();
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      Scope.exit();
      if (frame!=null) frame.remove();
    }
  }
}
