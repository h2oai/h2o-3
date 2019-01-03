package ai.h2o.automl.targetencoding;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import hex.Model;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.fvec.Frame;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;

public class TargetEncodingIntegrationWithAutoMLTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void TEIsNotBeingAppliedIfWeDontHaveCategoricalColumns() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    Frame originalCopyOfTrainingFrame = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/logreg/prostate_train.csv");
      originalCopyOfTrainingFrame = fr.deepCopy(Key.make().toString());
      DKV.put(originalCopyOfTrainingFrame);
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.response_column = "CAPSULE";

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false; 
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false; 

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      // Expect that if we don't have categorical vectors in training frame then we will return it unchanged 
      assert isBitIdentical(aml.getTrainingFrame(), originalCopyOfTrainingFrame):"The two frames are not the same.";

    } finally {
      // Cleanup
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(fr != null) fr.delete();
      if(originalCopyOfTrainingFrame != null) originalCopyOfTrainingFrame.delete();
    }
  }

  @Test public void KFoldSmokeTest() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      
      String foldColumnName = "fold";
      addKFoldColumn(fr, foldColumnName, 5, 1234L);
      TargetEncoderFrameHelper.factorColumn(fr, "survived");
      
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = "survived";

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

//      Frame trainingFrame = aml.getTrainingFrame();
//      assert !isBitIdentical(trainingFrame, fr):" Two frames should be different.";
//      trainingFrame.delete();

    } finally {
      // Cleanup
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(fr != null) fr.delete();
    }
  }
}
