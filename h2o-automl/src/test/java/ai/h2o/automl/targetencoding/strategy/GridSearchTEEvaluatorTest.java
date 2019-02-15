package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.Algo;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.TargetEncodingTestFixtures;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static org.junit.Assert.*;

public class GridSearchTEEvaluatorTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
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
