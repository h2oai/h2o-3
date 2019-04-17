package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.integration.AutoMLBuildSpec;
import hex.Model;
import org.junit.BeforeClass;
import org.junit.Test;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

public class ThresholdTEApplicationStrategyTest extends water.TestUtil {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void shouldReturnCatColumnsWithCardinalityHigherThanThresholdTest() {
    Frame fr=null;

    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      printOutColumnsMetadata(fr);
      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);
      assertArrayEquals(new String[]{"cabin", "home.dest"}, strategy.getColumnsToEncode());

    } finally {
      if(fr != null) fr.delete();
    }
  }
  
  @Test
  public void thresholdValueIsIncludedTest() {
    Frame fr=null;

    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      printOutColumnsMetadata(fr);
      //Cardinality of the cabin column is 186
      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 186);
      assertArrayEquals(new String[]{"cabin", "home.dest"}, strategy.getColumnsToEncode());
      
      TEApplicationStrategy strategy2 = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 187);
      assertArrayEquals(new String[]{"home.dest"}, strategy2.getColumnsToEncode());

    } finally {
      if(fr != null) fr.delete();
    }
  }

  // Integration test
  @Test public void ThresholdApplicationStrategyTest() {
    AutoML aml=null;
    Frame fr=null;
    Model leader = null;
    Frame trainingFrame = null;
    String columnThatIsSupposedToBeSkipped = "ColA";
    String responseColumnName = "ColC";
    String foldColumnName = "fold";
    try {
      AutoMLBuildSpec autoMLBuildSpec = new AutoMLBuildSpec();

      String columnThatIsSupposedToBeEncoded = "ColB";
      fr = new TestFrameBuilder()
              .withName("trainingFrame")
              .withColNames(columnThatIsSupposedToBeSkipped, columnThatIsSupposedToBeEncoded, responseColumnName, foldColumnName)
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "b", "b", "a", "a", "a"))
              .withDataForCol(1, ar("yellow", "yellow", "blue", "green", "red", "purple", "orange"))
              .withDataForCol(2, ar("2", "6", "6", "6", "6", "2", "2"))
              .withDataForCol(3, ar(1, 2, 2, 3, 2, 1, 2))
              .build();

      autoMLBuildSpec.input_spec.training_frame = fr._key;
      autoMLBuildSpec.input_spec.fold_column = foldColumnName;
      autoMLBuildSpec.input_spec.response_column = responseColumnName;

      Vec responseColumn = fr.vec(responseColumnName);
      TEApplicationStrategy teApplicationStrategy = new ThresholdTEApplicationStrategy(fr, responseColumn, 5);

      autoMLBuildSpec.te_spec.application_strategy = teApplicationStrategy;
      autoMLBuildSpec.te_spec.params_selection_strategy = HPsSelectionStrategy.Fixed;

      autoMLBuildSpec.te_spec.fixedTEParams = new TargetEncodingParams(teApplicationStrategy.getColumnsToEncode(), new BlendingParams(5, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);
      autoMLBuildSpec.te_spec.search_over_columns = false;
      autoMLBuildSpec.te_spec.seed = 3456; // Note: with other seeds we might end up in a situation where our encodings are all equal to prior mean (e.g. two `yellow` levels are in separate folds)

      autoMLBuildSpec.build_control.project_name = "automl_" + UUID.randomUUID().toString();
      autoMLBuildSpec.build_control.stopping_criteria.set_max_models(1);
      autoMLBuildSpec.build_control.keep_cross_validation_models = false;
      autoMLBuildSpec.build_control.keep_cross_validation_predictions = false;

      aml = AutoML.startAutoML(autoMLBuildSpec);
      aml.get();

      leader = aml.leader();

      trainingFrame = aml.getTrainingFrame();

      assertNotEquals(" Two frames should be different.", fr, trainingFrame);
      assertEquals(1, teApplicationStrategy.getColumnsToEncode().length);

      assertTrue(Arrays.asList(leader._output._names).contains(columnThatIsSupposedToBeEncoded + "_te"));
      assertFalse(Arrays.asList(leader._output._names).contains(columnThatIsSupposedToBeSkipped + "_te"));

    } finally {
      if(leader!=null) leader.delete();
      if(aml!=null) aml.delete();
      if(trainingFrame != null)  trainingFrame.delete();
      if(fr != null) fr.delete();
    }
  }

}
