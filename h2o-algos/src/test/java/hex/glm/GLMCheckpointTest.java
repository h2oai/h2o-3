package hex.glm;

import hex.ScoringInfo;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.TwoDimTable;

import java.util.ArrayList;

import static hex.glm.GLM.*;
import static hex.glm.GLMModel.GLMParameters;
import static hex.glm.GLMModel.GLMParameters.Family.binomial;
import static hex.glm.GLMModel.GLMParameters.Family.multinomial;
import static hex.glm.GLMModel.GLMParameters.Solver.IRLSM;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMCheckpointTest extends TestUtil {
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
  private static final double TOLERANCE = 1e-10;

  @Test
  public void testRestoreScoringHistory() {
    try {
      Scope.enter();
      Frame train = parseTestFile("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv");
      // set cat columns
      int numCols = train.numCols();
      int enumCols = (numCols-1)/2;
      for (int cindex=0; cindex<enumCols; cindex++) {
        train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
      }
      train.replace((10), train.vec(10).toCategoricalVec()).remove();
      DKV.put(train);
      Scope.track(train);

      GLMParameters params = new GLMParameters(multinomial);
      params._response_column = "C11";
      params._solver = IRLSM;
      params._train = train._key;
      GLMModel glm = new GLM(params).trainModel().get();
      Scope.track_generic(glm);

      ScoringHistory manualScoringHistory = new ScoringHistory(false, false, false);
      TwoDimTable modelScoringHistory = glm._output._scoring_history;
      int[] colHeaderIndex = restoreScoringHistoryFromCheckpoint(modelScoringHistory, params, null,
              manualScoringHistory);
      // check original scoring history and manually restore scoring histories are the same
      assertEqualScoringHistories(modelScoringHistory, colHeaderIndex, manualScoringHistory);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testCheckpointDefaultFamilyWontFail() {
    Frame tfr = null;
    GLMModel glm = null;
    GLMModel glm2 = null;

    try {
      tfr = parseTestFile("./smalldata/iris/iris.csv");
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();

      parms._train = tfr._key;
      parms._response_column = "C5";
      parms._seed = 0xdecaf;
      parms._solver = IRLSM;

      glm = new GLM(parms).trainModel().get();

      GLMModel.GLMParameters parms2 = new GLMModel.GLMParameters();
      parms2._train = tfr._key;
      parms2._response_column = "C5";
      parms2._seed = 0xdecaf;
      parms2._checkpoint = glm._key;
      parms2._solver = IRLSM;

      glm2 = new GLM(parms2).trainModel().get();

    } finally {
      if (tfr != null) tfr.delete();
      if (glm != null) glm.delete();
      if (glm2 != null) glm2.delete();
    }
  }

  /**
   * A continuation must not inherit the checkpointed run's cross-validation results. GLMModel.setCrossValidationScore
   * keys ScoringInfo.cross_validation off _output._cross_validation_metrics, so a stale metric surviving deepClone
   * would fill every iteration's scored_xval from the same frozen object - scoreKeepers() then hands
   * ScoreKeeper.stopEarly a flat series and early stopping fires immediately, silently truncating the fit.
   * The key-valued slots are checked too: they point at the source model's fold models and frames, so a
   * continuation that kept them would delete the source's cross-validation artifacts when it is removed.
   */
  @Test
  public void testCheckpointDoesNotInheritCrossValidationResults() {
    try {
      Scope.enter();
      Frame train = parseTestFile("./smalldata/iris/iris.csv");
      Scope.track(train);

      GLMParameters parms = new GLMParameters();
      parms._train = train._key;
      parms._response_column = "C5";
      parms._seed = 0xdecaf;
      parms._solver = IRLSM;
      parms._nfolds = 3;
      parms._keep_cross_validation_predictions = true;  // otherwise the prediction-key slots are null anyway
      GLMModel glm = new GLM(parms).trainModel().get();
      Scope.track_generic(glm);
      // Without these the assertions below pass vacuously.
      assertNotNull("the checkpointed model must actually have cross-validation results, otherwise this test " +
              "cannot detect them being inherited", glm._output._cross_validation_metrics);
      assertNotNull("the checkpointed model must actually have fold models", glm._output._cross_validation_models);
      assertNotNull("the checkpointed model must actually have fold predictions",
              glm._output._cross_validation_predictions);

      // Continue without cross-validation: nothing in the continued model may come from the checkpoint's CV run.
      GLMParameters contParms = new GLMParameters();
      contParms._train = train._key;
      contParms._response_column = "C5";
      contParms._seed = 0xdecaf;
      contParms._solver = IRLSM;
      contParms._checkpoint = glm._key;
      GLMModel continued = new GLM(contParms).trainModel().get();
      Scope.track_generic(continued);

      assertNull("continued model inherited the checkpoint's cross-validation metrics",
              continued._output._cross_validation_metrics);
      assertNull("continued model inherited the checkpoint's cross-validation metrics summary",
              continued._output._cross_validation_metrics_summary);
      assertNull("continued model inherited the checkpoint's fold models - removing it would delete them",
              continued._output._cross_validation_models);
      assertNull("continued model inherited the checkpoint's fold predictions - removing it would delete them",
              continued._output._cross_validation_predictions);
      assertNull("continued model inherited the checkpoint's holdout predictions frame",
              continued._output._cross_validation_holdout_predictions_frame_id);
      assertNull("continued model inherited the checkpoint's fold assignment frame",
              continued._output._cross_validation_fold_assignment_frame_id);

      // The observable consequence: ScoringInfo must not claim an xval score the continuation never computed.
      assertNotNull("continued model has no scoring info to check", continued.getScoringInfo());
      for (ScoringInfo si : continued.getScoringInfo()) {
        assertFalse("continued model reports cross_validation=true on a run with nfolds=0", si.cross_validation);
      }

      // The checkpoint keeps its own results - the continuation must not have consumed or removed them.
      assertNotNull("the checkpointed model lost its cross-validation metrics",
              glm._output._cross_validation_metrics);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testRestoreLambdaScoringHistory() {
    try {
      Scope.enter();
      Frame train = parseTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
      // set cat columns
      int numCols = train.numCols();
      int enumCols = (numCols-1)/2;
      for (int cindex=0; cindex<enumCols; cindex++) {
        train.replace(cindex, train.vec(cindex).toCategoricalVec()).remove();
      }
      train.replace(20, train.vec(20).toCategoricalVec()).remove();
      DKV.put(train);
      Scope.track(train);

      GLMParameters params = new GLMParameters(binomial);
      params._response_column = "C21";
      params._solver = IRLSM;
      params._train = train._key;
      params._lambda_search = true;
      params._nlambdas = 10;
      GLMModel glm = new GLM(params).trainModel().get();
      Scope.track_generic(glm);

      LambdaSearchScoringHistory  manualScoringHistory = new LambdaSearchScoringHistory(false, false);
      TwoDimTable modelScoringHistory = glm._output._scoring_history;
      int[] colHeaderIndex = restoreScoringHistoryFromCheckpoint(modelScoringHistory, params, manualScoringHistory, null);
      assertEqualLambdaScoringHistories(modelScoringHistory, colHeaderIndex, manualScoringHistory);
    } finally {
      Scope.exit();
    }
  }

  // copy from scoring_history back to _sc or _lsc
  private int[] restoreScoringHistoryFromCheckpoint(TwoDimTable scoringHistory, GLMParameters parms,
                                                    LambdaSearchScoringHistory lscHistory, ScoringHistory scHistory) {
    String[] colHeaders2Restore = parms._lambda_search ?
            new String[]{"iteration", "timestamp", "lambda", "predictors", "deviance_train",
                    "deviance_test", "alpha"}
            : new String[]{"iteration", "timestamp", "negative_log_likelihood", "objective", "sum(etai-eta0)^2",
            "convergence"};
    int num2Copy = parms._lambda_search ? colHeaders2Restore.length : colHeaders2Restore.length-2;
    int[] colHeadersIndex = grabHeaderIndex(scoringHistory, num2Copy, colHeaders2Restore);
    if (parms._lambda_search)
      lscHistory.restoreFromCheckpoint(scoringHistory, colHeadersIndex);
    else
      scHistory.restoreFromCheckpoint(scoringHistory, colHeadersIndex);
    return colHeadersIndex;
  }

  private void assertEqualScoringHistories(TwoDimTable sHist, int[] colIndices, ScoringHistory manualSC) {
    int numRows = sHist.getRowDim();
    ArrayList<Integer> scoringIters = manualSC.getScoringIters();
    ArrayList<Long> scoringTimes = manualSC.getScoringTimes();
    ArrayList<Double> likelihoods = manualSC.getLikelihoods();
    ArrayList<Double> objectives = manualSC.getObjectives();
    for (int rowInd = 0; rowInd < numRows; rowInd++) {  // if lambda_search is enabled, _sc is not updated
      assert scoringIters.get(rowInd) == (Integer) sHist.get(rowInd, colIndices[0]) : "scoring iterations differs.";
      assert scoringTimes.get(rowInd) == DATE_TIME_FORMATTER.parseMillis((String) sHist.get(rowInd, colIndices[1])) :
              "scoring time differs.";
      assert Math.abs(likelihoods.get(rowInd)-(Double) sHist.get(rowInd, colIndices[2])) < TOLERANCE : "likelhood differs.";
      assert Math.abs(objectives.get(rowInd)-(Double) sHist.get(rowInd, colIndices[3])) < TOLERANCE : "objective differs.";
    }
  }

  private void assertEqualLambdaScoringHistories(TwoDimTable sHist, int[] colIndices, LambdaSearchScoringHistory manualLSC) {
    int numRows = sHist.getRowDim();
    ArrayList<Long> scoringTimes = manualLSC.getScoringTimes();
    ArrayList<Integer> scoringIters = manualLSC.getScoringIters();
    ArrayList<Double> lambdas = manualLSC.getLambdas();
    ArrayList<Double> alphas = manualLSC.getAlphas();
    ArrayList<Integer> predictors = manualLSC.getPredictors();
    ArrayList<Double> devTrain = manualLSC.getDevTrain();

    for (int rowInd = 0; rowInd < numRows; rowInd++) {
      assert scoringTimes.get(rowInd) == DATE_TIME_FORMATTER.parseMillis((String) sHist.get(rowInd, colIndices[1])) :
              "Scoring time differs.";
      assert scoringIters.get(rowInd) == (int) sHist.get(rowInd, colIndices[0]) : "Scoring iteration differs.";
      assert Math.abs(lambdas.get(rowInd)-Double.valueOf((String) sHist.get(rowInd, colIndices[2]))) < TOLERANCE : "lambda differs.";
      assert Math.abs(alphas.get(rowInd)-Double.valueOf((Double) sHist.get(rowInd, colIndices[6]))) < TOLERANCE : "alpha differs.";
      assert predictors.get(rowInd) == (int) sHist.get(rowInd, colIndices[3]) : " predictor differs";
      assert Math.abs(devTrain.get(rowInd)-(double) sHist.get(rowInd, colIndices[4])) < TOLERANCE : "deviance train differs.";
    }
  }
}
