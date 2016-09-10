package hex.deeplearning;

import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
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
      p._train = frame._key;
      p._response_column = "CAPSULE"; // last column is the response
      p._activation = DeepLearningParameters.Activation.Rectifier;
      p._epochs = 4;
      p._train_samples_per_iteration = -1;
      p._score_duty_cycle = 1;
      p._score_interval = 0;
      p._overwrite_with_best_model = false;
      p._classification_stop = -1;
      p._seed = 1234;
      p._reproducible = true;

      // Convert response 'C785' to categorical (digits 1 to 10)
      int ci = frame.find("CAPSULE");
      Scope.track(frame.replace(ci, frame.vecs()[ci].toCategoricalVec()));
      DKV.put(frame);

      long start = System.currentTimeMillis();
      try { Thread.sleep(1000); } catch( InterruptedException ex ) { } //to avoid rounding issues with printed time stamp (1 second resolution)

      DeepLearningModel model = new DeepLearning(p).trainModel().get();
      long sleepTime = 5; //seconds
      try { Thread.sleep(sleepTime*1000); } catch( InterruptedException ex ) { }

      // checkpoint restart after sleep
      DeepLearningParameters p2 = (DeepLearningParameters)p.clone();
      p2._checkpoint = model._key;
      p2._epochs *= 2;
      DeepLearningModel model2 = null;
      try {
        model2 = new DeepLearning(p2).trainModel().get();
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
          Assert.assertTrue("Iteration counter must match epochs", (Integer)table.get(i,4) == i); //1 iteration per step
        }
        try {
          // Check that duration doesn't see the sleep
          String durationBefore = (String)table.get((int)(p._epochs),1);
          durationBefore = durationBefore.substring(0, durationBefore.length()-4);
          String durationAfter = (String)table.get((int)(p._epochs+1),1);
          durationAfter = durationAfter.substring(0, durationAfter.length()-4);
          Assert.assertTrue("Duration must be smooth", Double.parseDouble(durationAfter) - Double.parseDouble(durationBefore) < sleepTime+1);

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
          Assert.assertTrue("Speed shouldn't change more than 50%", Math.abs(speedAfterDouble-speedBeforeDouble)/speedBeforeDouble < 0.5); //expect less than 50% change in speed
        } catch(NumberFormatException ex) {
          //skip runtimes > 1 minute (too hard to parse into seconds here...).
        }

      } finally {
        if (model != null) model.delete();
        if (model2 != null) model2.delete();
      }
    } finally {
      if (frame!=null) frame.remove();
      Scope.exit();
    }
  }
}
