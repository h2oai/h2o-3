package ai.h2o.automl;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;

import java.util.Date;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class AutoMLTest extends TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void AirlinesTest() {
    AutoML aml=null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(5);
      autoMLBuildSpec.build_control.max_after_balance_size = 5.0f;
      autoMLBuildSpec.build_control.keep_cross_validation_models = false; //Prevent leaked keys from CV models
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; //Prevent leaked keys from CV predictions

      aml = AutoML.makeAutoML(Key.<AutoML>make(), new Date(), autoMLBuildSpec);
      AutoML.startAutoML(aml);
      aml.get();

    } finally {
      // Cleanup
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.remove();
    }
  }

  @Test public void KeepCrossValidationFoldAssignmentTest() {
    AutoML aml=null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
      autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(5);
      autoMLBuildSpec.build_control.keep_cross_validation_fold_assignment = true;

      aml = AutoML.makeAutoML(Key.<AutoML>make(), new Date(), autoMLBuildSpec);
      AutoML.startAutoML(aml);
      aml.get();

      assertTrue(aml.leader() !=null && aml.leader()._parms._keep_cross_validation_fold_assignment);
      assertTrue(aml.leader() !=null && aml.leader()._output._cross_validation_fold_assignment_frame_id != null);

    } finally {
      // cleanup
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.remove();
    }
  }

  @Test public void verifyImmutabilityNotWorkingTest() {
    AutoML aml=null;
    Frame fr=null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "IsDepDelayed";
      autoMLBuildSpec.build_control.keep_cross_validation_fold_assignment = true;

      // Two stopping criteria
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.stopping_criteria.set_max_runtime_secs(60);

      // During makeAutoML in the `handleDatafileParameters` method we save checksums
      aml = AutoML.makeAutoML(Key.<AutoML>make(), new Date(), autoMLBuildSpec);

      //Lets try to mess things up in training frame
      Frame trainingFrame = aml.getTrainingFrame();

      // Setting DepDelay to zero vec
      Vec depDelayedVec = trainingFrame.vec("DepDelay");
      Vec zeros = Vec.makeZero(depDelayedVec.length());
      int indexOfColumn = trainingFrame.find("DepDelay");
      trainingFrame.vecs()[indexOfColumn] = zeros;
      assertVecEquals(zeros, trainingFrame.vec("DepDelay"), 1e-5);

      // Add some more mess
      for(int i = 0; i < 20000; i++ ) {
        trainingFrame.vecs()[indexOfColumn].set(i, 42.0);
      }

      printOutFrameAsTable(trainingFrame, 100, true);

      AutoML.startAutoML(aml);
      aml.get();

      //With changed DepDelay we finally managed to train one model, hit the max_models parameter and return result. No exceptions regarding checksums were thrown!
      assertEquals(1, aml.leaderboard().getModelCount());

    } finally {
      // cleanup
      if(aml!=null) aml.deleteWithChildren();
      if(fr != null) fr.remove();
    }
  }

  private void printOutFrameAsTable(Frame fr, int numRows, boolean rollups) {

    TwoDimTable twoDimTable = fr.toTwoDimTable(0, numRows, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }
}
