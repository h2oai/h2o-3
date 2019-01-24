package ai.h2o.automl.targetencoding;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import hex.Model;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;

public class TargetEncodingIntegrationWithAutoMLTest extends water.TestUtil {

  @BeforeClass public static void setup() { stall_till_cloudsize(1); }


  @Test public void justGettingOneModelFromAutoMLTest() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    Frame trainingFrame = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String foldColumnName = "fold";
      addKFoldColumn(fr, foldColumnName, 5, 1234L);
      String responceColumnName = "survived";
      factorColumn(fr, responceColumnName);

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responceColumnName;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      trainingFrame = aml.getTrainingFrame();

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(trainingFrame != null)  trainingFrame.delete();
      if(fr != null) fr.delete();
    }
  }
  
  
  @Test public void gettingOneModelFromAutoML_AndAddRemoveArbitraryColumnTest() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    Frame trainingFrame = null;
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      
      String foldColumnName = "fold";
      addKFoldColumn(fr, foldColumnName, 5, 1234L);
      String responceColumnName = "survived";
      factorColumn(fr, responceColumnName);
      
      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responceColumnName;

      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      trainingFrame = aml.getTrainingFrame();
      
      
      // Add and remove column on a `training` frame

      addThenRemoveNumerator(trainingFrame);

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(trainingFrame != null)  trainingFrame.delete();
      if(fr != null) fr.delete();
    }
  }
  

  @Test
  public void addThenRemoveTest() {
    Frame fr = null;
    try {
      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b", "a"))
              .withDataForCol(1, ar("yes", "no", "yes"))
              .build();

      // Add and remove column on a frame from test builder
      addThenRemoveNumerator(fr);
      
    } finally {
      if(fr!= null) fr.delete();

    }
  }

  @Test
  public void addThenRemoveWithParsingFileTest() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      // Add and remove column on a `training` frame
      addThenRemoveNumerator(fr);

    } finally {
      if(fr!= null) fr.delete();

    }
  }
  
  void addThenRemoveNumerator(Frame fr) {
    // NOTE: In case of parse_test_file I need to call `emptyNumerator.remove()` after add() operation
    //       In case of TestFrameBuilder I don't need to call `emptyNumerator.remove()` and actually can't call it because it causes Missing chunk errors.
    Vec emptyNumerator = Vec.makeZero(fr.numRows()); 
    fr.add("numerator", emptyNumerator);

    Vec removedNumeratorNone = fr.remove("numerator");
    removedNumeratorNone.remove();
  }

  public static Frame factorColumn(Frame fr, String columnName) {
    int columnIndex = fr.find(columnName);
    Vec vec = fr.vec(columnIndex);
    fr.replace(columnIndex, vec.toCategoricalVec());
    vec.remove();
    return fr;
  }
}
