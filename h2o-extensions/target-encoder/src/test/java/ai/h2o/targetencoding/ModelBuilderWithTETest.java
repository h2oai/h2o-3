package ai.h2o.targetencoding;

import hex.Model;
import hex.ModelBuilder;
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
import water.util.TwoDimTable;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

@RunWith(Enclosed.class)
public class ModelBuilderWithTETest {

  public static ModelBuilder modelBuilderGBMWithCVFixture(Frame fr, String responseColumnName, long builderSeed) {
    String algoUrlName = "gbm";
    String algoName = ModelBuilder.algoName(algoUrlName);
    Key<Model> testModelKey = Key.make("testModelKey");

    Job<Model> job = new Job<>(testModelKey, ModelBuilder.javaName(algoUrlName), algoName);
    ModelBuilder builder = ModelBuilder.make(algoUrlName, job, testModelKey);

    // Model Parameters
    GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
    gbmParameters._score_tree_interval = 5;
    gbmParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;

    builder._parms = gbmParameters;
    builder._parms._seed = builderSeed;

    builder._parms._train = fr._key;
    builder._parms._response_column = responseColumnName;
    builder._parms._nfolds = 5;
    builder._parms._keep_cross_validation_models = true;
    builder._parms._keep_cross_validation_predictions = true;
    return builder;
  }


  @RunWith(Parameterized.class)
  public static class ModelBuilderWithTEParametrizedTest extends TestUtil {

    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "Validation mode = {0}, task type = {1}")
    public static Object[][] validationMode() {
      return new Object[][]{
//              {"", "regression"},
              {"", "classification"}
      };
    }

    @Parameterized.Parameter
    public String validationMode;

    @Parameterized.Parameter (value = 1)
    public String taskType;

    @Test
    public void te_model_with_multiple_columns_to_encode() {

    }

    @Test
    public void te_model_when_main_model_has_fold_column_provided() {
      // folds, weights column etc
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

        ModelBuilder modelBuilderForMainModel = modelBuilderGBMWithCVFixture(trainingFrame, parameters._response_column, parameters._seed);

        modelBuilderForMainModel.addTEModelKey(targetEncoderModel._key);

        Model gbmModel = (GBMModel) modelBuilderForMainModel.trainModel().get();

        Frame scoredTest = gbmModel.score(testFrame);
        Scope.track(scoredTest);

        hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(gbmModel, testFrame);
        assertTrue(mmb.auc() > 0);

        TwoDimTable model_summary = gbmModel._output._model_summary;
        System.out.println(model_summary);

        Scope.track_generic(gbmModel);
      } finally {
        Scope.exit();
      }

    }

    @Test
    public void te_is_applied_to_specified_columns_and_rest_categorical_columns_are_encoded_with_accordance_to__categorical_encoding() {

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

        ModelBuilder modelBuilderForMainModel = modelBuilderGBMWithCVFixture(trainingFrame, parameters._response_column, parameters._seed);
        modelBuilderForMainModel._parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.EnumLimited;

        modelBuilderForMainModel.addTEModelKey(targetEncoderModel._key);
        modelBuilderForMainModel.init(false);

        Model gbmModel = (GBMModel) modelBuilderForMainModel.trainModel().get();

        Frame scoredTest = gbmModel.score(testFrame);
        Scope.track(scoredTest);

        hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(gbmModel, testFrame);
        assertTrue(mmb.auc() > 0);

        String variableImportancesTableAsString = ((GBMModel) gbmModel)._output._variable_importances.toString(0, true);

        String[] enumLimitedEncodedColumns = {"fYear.top_10_levels", "fDayofMonth.top_10_levels", "Dest.top_10_levels"};
        assertTrue(Arrays.stream(enumLimitedEncodedColumns).allMatch(variableImportancesTableAsString::contains));
        System.out.println(variableImportancesTableAsString);


        Scope.track_generic(gbmModel);
      } finally {
        Scope.exit();
      }

    }
  }

}