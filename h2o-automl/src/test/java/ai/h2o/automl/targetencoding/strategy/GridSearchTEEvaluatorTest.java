package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.Algo;
import ai.h2o.automl.UserFeedbackEvent;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.TargetEncodingTestFixtures;
import hex.Model;
import hex.ModelBuilder;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.*;

public class GridSearchTEEvaluatorTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void evaluateMethodWorksWithModelBuilder() {
    Frame fr = null;
    Frame frCopy = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);
      
      frCopy = fr.deepCopy(Key.make().toString());
      DKV.put(frCopy);

      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec(responseColumnName), 4);

      GridSearchTEEvaluator evaluator = new GridSearchTEEvaluator();

      TargetEncodingParams randomTEParams = TargetEncodingTestFixtures.randomTEParams();
      ModelBuilder modelBuilder = modelBuilderFixture(fr, responseColumnName);
      
      String[] columnsToEncode = strategy.getColumnsToEncode();

      modelBuilder.init(false); //verifying that we can call init and then modify builder in evaluator
      
      int seedForFoldColumn = 2345;
      double auc = evaluator.evaluate(randomTEParams, modelBuilder, columnsToEncode, seedForFoldColumn);
      
      System.out.println("AUC with target encoding: " + auc);
      printOutFrameAsTable(modelBuilder._parms.train(), false, 5);

      ModelBuilder clonedModelBuilder = ModelBuilder.clone(modelBuilder);
      clonedModelBuilder.init(false);
      double auc2 = evaluator.evaluate(randomTEParams, clonedModelBuilder, columnsToEncode, seedForFoldColumn); // check that we can reuse modelBuilder

      assertTrue(isBitIdentical(frCopy, modelBuilder._parms.train()));
      assertTrue(auc > 0);
      assertEquals(auc, auc2, 1e-5);
    } finally {
      fr.delete();
      frCopy.delete();
    }
  }

  @Test
  public void evaluateMethodWorksWithModelBuilderAndIgnoredColumns() {
    //TODO check case when original model builder has ignored columns
  }
  
  private ModelBuilder modelBuilderFixture(Frame fr, String responseColumnName) {
    Algo algo = Algo.GBM;
    String algoUrlName = algo.name().toLowerCase();
    String algoName = ModelBuilder.algoName(algoUrlName);
    Key<Model> testModelKey = Key.make("testModelKey");

    Job<Model> job = new Job<>(testModelKey, ModelBuilder.javaName(algoUrlName), algoName);
    ModelBuilder builder = ModelBuilder.make(algoUrlName, job, testModelKey);

    // Model Parameters
    GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
    gbmParameters._score_tree_interval = 5;
    gbmParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;

    builder._parms = gbmParameters;

    setCommonModelBuilderParams(builder._parms, fr, responseColumnName);
    return builder;
  }

  private void setCommonModelBuilderParams(Model.Parameters params,Frame trainingFr, String responseColumnName) {
    params._train = trainingFr._key;
    params._response_column = responseColumnName;
  }
  
  @Test
  public void evaluateMethodDoesNotLeakKeys() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      long seedForFoldColumn = 2345L;
      final String foldColumnForTE = "custom_fold";
      int nfolds = 5;
      addKFoldColumn(fr, foldColumnForTE, nfolds, seedForFoldColumn);

      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      TEApplicationStrategy strategy = new ThresholdTEApplicationStrategy(fr, fr.vec("survived"), 4);

      GridSearchTEEvaluator evaluator = new GridSearchTEEvaluator();

      TargetEncodingParams anyParams = TargetEncodingTestFixtures.defaultTEParams();
      evaluator.evaluate(anyParams, new Algo[]{Algo.GBM}, fr, responseColumnName, foldColumnForTE, strategy.getColumnsToEncode());
    } finally {
      fr.delete();
    }
  }
  
  @Test
  public void gbmEvaluatorDoesNotLeakKeys() {
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      String[] columnsOtExclude = new String[]{};

      GridSearchTEEvaluator.evaluateWithGBM(fr, responseColumnName, columnsOtExclude);

    } finally {
      fr.delete();
    }
  }
  
  @Test
  public void glmEvaluatorDoesNotLeakKeys() {
    
    Frame fr = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/titanic.csv");

      String responseColumnName = "survived";

      asFactor(fr, responseColumnName);

      String[] columnsOtExclude = new String[]{};

      GridSearchTEEvaluator.evaluateWithGLM(fr, responseColumnName, columnsOtExclude);
    } finally {
      fr.delete();
    }
  }
}
