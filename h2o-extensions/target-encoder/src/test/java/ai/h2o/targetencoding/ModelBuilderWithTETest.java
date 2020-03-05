package ai.h2o.targetencoding;

import hex.Model;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Job;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.Arrays;

import static ai.h2o.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class ModelBuilderWithTETest {

  public static ModelBuilder modelBuilderGBMWithCVFixture(GBMModel.GBMParameters gbmParameters, Frame fr, String responseColumnName, long builderSeed) {
    String algoUrlName = "gbm";
    String algoName = ModelBuilder.algoName(algoUrlName);
    Key<Model> testModelKey = Key.make("testModelKey");

    Job<Model> job = new Job<>(testModelKey, ModelBuilder.javaName(algoUrlName), algoName);
    ModelBuilder builder = ModelBuilder.make(algoUrlName, job, testModelKey);

    // Model Parameters
    gbmParameters._score_tree_interval = 5;
    gbmParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;

    builder._parms = gbmParameters;
    builder._parms._seed = builderSeed;

    builder._parms._train = fr._key;
    builder._parms._response_column = responseColumnName;
    builder._parms._keep_cross_validation_models = true;
    builder._parms._keep_cross_validation_predictions = true;
    return builder;
  }

  public static class ModelBuilderWithTENonParametrizedTest extends TestUtil {

    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Test
    public void te_model_is_applied_correctly_when_main_model_uses_folds_weights_and_offset() {
      try {
        Scope.enter();
        Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
        String weightsColumnName = "weights";
        trainingFrame.add(weightsColumnName, trainingFrame.anyVec().makeCon(0.6));
        String foldColumnName = "fold_m";
        addKFoldColumn(trainingFrame, foldColumnName, 5, 1234L);
        String offsetColumnName = "offset";
        trainingFrame.add(offsetColumnName, trainingFrame.anyVec().makeCon(0.2));

        Scope.track(trainingFrame);
        Frame testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
        testFrame.add(offsetColumnName, testFrame.anyVec().makeCon(0.2));
        testFrame.add(weightsColumnName, testFrame.anyVec().makeCon(0.6));
        Scope.track(testFrame);

        TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();
        parameters._data_leakage_handling = TargetEncoder.DataLeakageHandlingStrategy.None;
        parameters._k = 0.3;
        parameters._f = 0.7;
        parameters._blending = true;
        parameters._response_column = "IsDepDelayed";
        parameters._fold_column = foldColumnName;
        parameters._ignored_columns = ignoredColumns(trainingFrame, "Origin", foldColumnName, parameters._response_column);
        parameters._train = trainingFrame._key;
        parameters._seed = 0XFEED;

        TargetEncoderBuilder temb = new TargetEncoderBuilder(parameters);
        final Model targetEncoderModel = temb.trainModel().get();

        GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
        gbmParameters._offset_column = offsetColumnName;
        gbmParameters._weights_column = weightsColumnName;
        gbmParameters._fold_column = foldColumnName;
        gbmParameters._nfolds = 0;
        ModelBuilder modelBuilderForMainModel = modelBuilderGBMWithCVFixture(gbmParameters,trainingFrame, parameters._response_column, parameters._seed);

        modelBuilderForMainModel.addTEModelKey(targetEncoderModel._key);

        Model gbmModel = (GBMModel) modelBuilderForMainModel.trainModel().get();

        Frame scoredTest = gbmModel.score(testFrame);
        Scope.track(scoredTest);

        hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(gbmModel, testFrame);
        assertTrue(mmb.auc() > 0);

        String variableImportancesTableAsString = ((GBMModel) gbmModel)._output._variable_importances.toString(0, true);

        String[] teEncodedColumns = {"Origin_te"};
        assertTrue(Arrays.stream(teEncodedColumns).allMatch(variableImportancesTableAsString::contains));

        Scope.track_generic(gbmModel);
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void te_model_is_being_applied_once_added_to_main_model_builder() {

      try {
        Scope.enter();
        Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
        Scope.track(trainingFrame);
        Frame testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
        Scope.track(testFrame);

        TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();
        parameters._data_leakage_handling = TargetEncoder.DataLeakageHandlingStrategy.None;
        parameters._k = 0.3;
        parameters._f = 0.7;
        parameters._blending = true;
        parameters._response_column = "IsDepDelayed";
        parameters._ignored_columns = ignoredColumns(trainingFrame, "Origin", parameters._response_column);
        parameters._train = trainingFrame._key;
        parameters._seed = 0XFEED;


        TargetEncoderBuilder temb = new TargetEncoderBuilder(parameters);
        final Model targetEncoderModel = temb.trainModel().get();

        GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
        gbmParameters._nfolds = 5;
        ModelBuilder modelBuilderForMainModel = modelBuilderGBMWithCVFixture(gbmParameters, trainingFrame, parameters._response_column, parameters._seed);

        modelBuilderForMainModel.addTEModelKey(targetEncoderModel._key);

        Model gbmModel = (GBMModel) modelBuilderForMainModel.trainModel().get();

        Frame scoredTest = gbmModel.score(testFrame);
        Scope.track(scoredTest);

        hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(gbmModel, testFrame);
        assertTrue(mmb.auc() > 0);

        String variableImportancesTableAsString = ((GBMModel) gbmModel)._output._variable_importances.toString(0, true);
        String[] teEncodedColumns = {"Origin_te"};
        assertTrue(Arrays.stream(teEncodedColumns).allMatch(variableImportancesTableAsString::contains));

        Scope.track_generic(gbmModel);
      } finally {
        Scope.exit();
      }
    }

  }


  @RunWith(Parameterized.class)
  public static class ModelBuilderWithTEParametrizedTest extends TestUtil {

    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "CV mode = {0}, te_is_provided = {1}")
    public static Object[][] testParams() {
      return new Object[][]{
              {false, false},
              {false, true},
              {true, false},
              {true, true}
      };
    }

    @Parameterized.Parameter (value = 0)
    public boolean isCVMode;

    @Parameterized.Parameter (value = 1)
    public Boolean teIsProvided;


    @Test
    public void te_is_applied_to_specified_columns_and_rest_categorical_columns_are_encoded_with_accordance_to_categorical_encoding_param() {
      Scope.enter();
      try {
        long seed = 2345;
        Frame originalTrainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
        Scope.track(originalTrainingFrame);
        Frame[] splits = null;
        if(!isCVMode) {
          Key[] keys = new Key[]{
                  Key.make("training_" + originalTrainingFrame._key),
                  Key.make("validation_" + originalTrainingFrame._key)
          };
          double[] splitRatios = new double[]{0.8, 0.2};
          splits = ShuffleSplitFrame.shuffleSplitFrame(
                  originalTrainingFrame,
                  keys,
                  splitRatios,
                  seed
          );
          Scope.track(splits[0], splits[1]);
        }
        Frame trainingFrame = isCVMode ? originalTrainingFrame : splits[0];
        Frame validationFrame = isCVMode ? null : splits[1];
        Frame testFrame = parse_test_file("./smalldata/testng/airlines_test.csv");
        Scope.track(testFrame);

        TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();
        parameters._data_leakage_handling = TargetEncoder.DataLeakageHandlingStrategy.None;
        parameters._k = 0.3;
        parameters._f = 0.7;
        parameters._blending = true;
        parameters._response_column = "IsDepDelayed";
        parameters._ignored_columns = ignoredColumns(trainingFrame, "Origin", "Dest", "fDayofMonth", parameters._response_column);
        parameters._train = trainingFrame._key;
        parameters._seed = seed;

        TargetEncoderBuilder temb = new TargetEncoderBuilder(parameters);
        final Model targetEncoderModel = temb.trainModel().get();

        if(!teIsProvided) Scope.track_generic(targetEncoderModel);

        GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
        gbmParameters._nfolds = 0;
        if (!isCVMode) {
          gbmParameters._valid = validationFrame._key;
        }
        ModelBuilder modelBuilderForMainModel = modelBuilderGBMWithCVFixture(gbmParameters, trainingFrame, parameters._response_column, parameters._seed);
        modelBuilderForMainModel._parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.EnumLimited;

        if(teIsProvided)
          modelBuilderForMainModel.addTEModelKey(targetEncoderModel._key);

        modelBuilderForMainModel.init(false);

        Model gbmModel = (GBMModel) modelBuilderForMainModel.trainModel().get();

        Frame scoredTest = gbmModel.score(testFrame);
        Scope.track(scoredTest);

        if(!isCVMode) {
          hex.ModelMetricsBinomial mmb_valid = hex.ModelMetricsBinomial.getFromDKV(gbmModel, validationFrame);
          assertTrue(mmb_valid.auc() > 0);
        }

        hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(gbmModel, testFrame);
        assertTrue(mmb.auc() > 0);

        String variableImportancesTableAsString = ((GBMModel) gbmModel)._output._variable_importances.toString(0, true);

        if(teIsProvided) {
          String[] enumLimitedEncodedColumns = {"fYear.top_10_levels"};
          assertTrue(Arrays.stream(enumLimitedEncodedColumns).allMatch(variableImportancesTableAsString::contains));
          String[] teEncodedColumns = {"Origin_te", "fDayofMonth_te", "Dest_te"};
          assertTrue(Arrays.stream(teEncodedColumns).allMatch(variableImportancesTableAsString::contains));
        } else {
          String[] enumLimitedEncodedColumns = {"fYear.top_10_levels", "fDayofMonth.top_10_levels", "Origin.top_10_levels"};
          assertTrue(Arrays.stream(enumLimitedEncodedColumns).allMatch(variableImportancesTableAsString::contains));
        }

        System.out.println(variableImportancesTableAsString);

        Scope.track_generic(gbmModel);
      } finally {
        Scope.exit();
      }
    }

  }

}