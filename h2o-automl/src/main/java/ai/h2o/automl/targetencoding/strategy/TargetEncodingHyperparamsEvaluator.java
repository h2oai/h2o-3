package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.*;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.splitframe.ShuffleSplitFrame;
import water.DKV;
import water.Iced;
import water.Key;
import water.Keyed;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.*;

import static ai.h2o.automl.targetencoding.TargetEncoder.DataLeakageHandlingStrategy.*;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.concat;

public class TargetEncodingHyperparamsEvaluator extends Iced {
  public TargetEncodingHyperparamsEvaluator() {
    
  }

  public double evaluateForCVMode(TargetEncodingParams teParams, ModelBuilder modelBuilder, String[] columnsToEncode, long seedForFoldColumn) {

    double score = 0;
    Map<String, Frame> encodingMap = null;
    Frame trainEncoded = null;
    Frame testEncoded = null;
    Frame originalTrainingData = modelBuilder.train();

    Frame trainCopy = originalTrainingData.deepCopy(Key.make("train_frame_copy_for_evaluation" + Key.make()).toString());
    DKV.put(trainCopy);

    String[] originalIgnoredColumns = modelBuilder._parms._ignored_columns;

    try {
      String responseColumn = modelBuilder._parms._response_column;
      String[] teColumnsToExclude = columnsToEncode;
      TargetEncoder tec = new TargetEncoder(columnsToEncode, teParams.getBlendingParams());
      byte holdoutType = teParams.getHoldoutType();

      String foldColumnForTE = null;

      switch (holdoutType) {
        case KFold:
          // Maybe we can use the same folds that we will use for splitting but in that case we will have only 4 folds for encoding map
          // generation and application of this map to the frame that was used for creating map
          foldColumnForTE = modelBuilder._job._key.toString() + "_fold";
          int nfolds = 5;
          addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seedForFoldColumn);

          //TODO consider optimising this as encoding map will be the same for whole runs as long as we only use KFold scenario with same seed for fold assignments
          encodingMap = tec.prepareEncodingMap(trainCopy, responseColumn, foldColumnForTE, true);
//          Frame.export(encodingMap.get("home.dest"), "evaluator_cv_kfold.csv", encodingMap.get("home.dest")._key.toString(), true, 1).get();

          trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMap, KFold, foldColumnForTE, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, seedForFoldColumn);
//          Frame.export(trainEncoded, "evaluator_encoded_train_cv_kfold.csv", trainEncoded._key.toString(), true, 1).get();

          break;
        case LeaveOneOut:
        case None:
        default:
          // For `None` strategy we can make sure that holdouts from `otherFolds` frame are being chosen in a mutually exclusive way across all folds
          throw new IllegalStateException("Only `KFold` strategy is being used in current version for CV mode.");
      }

      modelBuilder._parms._ignored_columns = concat(concat(originalIgnoredColumns, teColumnsToExclude), new String[]{ foldColumnForTE});

      modelBuilder.setTrain(trainEncoded);
      modelBuilder._parms.setTrain(trainEncoded._key);

      try {
        Model retrievedModel = null;
        Keyed model = modelBuilder.trainModel().get();
        retrievedModel = DKV.getGet(model._key);
        double cvScore = retrievedModel._output._cross_validation_metrics.auc_obj()._auc;
        score += cvScore;
      } catch (Exception exception) {
        Log.debug("Exception during modelBuilder evaluation: " + exception.getMessage());
      }
      
    } catch(Exception ex ) {
      Log.debug("Exception during applying TE in TargetEncodingHyperparamsEvaluator.evaluate(): " + ex.getMessage());
    } finally {
      //Setting back original frames
      modelBuilder.setTrain(originalTrainingData);
      modelBuilder._parms.setTrain(originalTrainingData._key);

      modelBuilder._parms._ignored_columns = originalIgnoredColumns;
      if(testEncoded != null) testEncoded.delete();
      if(trainCopy != null) trainCopy.delete();
      if (encodingMap == null) {
        Log.debug("Illegal state. encodingMap == null.");
      } else {
        TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
      }
    }
    return score;
  }
  
  private double scoreOnTest(ModelBuilder modelBuilder, Frame testEncodedFrame) {
    Model retrievedModel = null;
    Keyed model = modelBuilder.trainModel().get();
    retrievedModel = DKV.getGet(model._key);
    retrievedModel.score(testEncodedFrame);

    hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(retrievedModel, testEncodedFrame);
    if(retrievedModel!=null) retrievedModel.delete();

    return mmb.auc();
  }

  public double evaluateForValidationFrameMode(TargetEncodingParams teParams, ModelBuilder modelBuilder, Frame leaderboard, String[] columnsToEncode, long seedForFoldColumn) {
    double score = 0;
    Map<String, Frame> encodingMap = null;
    Frame trainEncoded = null;
    Frame validEncoded = null;
    Frame testEncoded = null;

    // As original modelBuilder could be set up in a different way... let say nfolds = 0 
    // we need to take into consideration the fact that training frame is going to be different every time as we split validation and leaderboard from it
    Frame originalTrainingData = modelBuilder.train();
    Frame originalValidationData = modelBuilder._parms.valid();

    Frame trainCopy = originalTrainingData.deepCopy(Key.make("train_frame_copy_for_evaluation" + Key.make()).toString());
    DKV.put(trainCopy);

    Frame validCopy = originalValidationData.deepCopy(Key.make("validation_frame_copy_for_evaluation" + Key.make()).toString());
    DKV.put(validCopy);

    Frame leaderboardCopy = leaderboard.deepCopy(Key.make("leaderboard_frame_copy_for_evaluation" + Key.make()).toString());
    DKV.put(leaderboardCopy);

    String[] originalIgnoredColumns = modelBuilder._parms._ignored_columns;
    String foldColumnForTE = null;

    try {
      // We need to apply TE taking into account the way how we train and validate our models.
      // With nfolds model will assign fold column to the data but we need those folds in TE before that. So we need to generate fold column themselves and then provide it to the model.
      // But what if we already have folds from the model search level provided by user? Is it better to use different fold assignments for TE? Maybe yes - it is similar to "n-times m-folds cross-validation" idea.

      String responseColumn = modelBuilder._parms._response_column;
      String[] teColumnsToExclude = columnsToEncode;
      TargetEncoder tec = new TargetEncoder(columnsToEncode, teParams.getBlendingParams());
      byte holdoutType = teParams.getHoldoutType();

      if(holdoutType == KFold) {
        foldColumnForTE = modelBuilder._job._key.toString() + "_fold"; //TODO quite long but feels unique though
        //TODO default value for KFOLD target encoding. We might want to search for this value but it is quite expensive.
        // We might want to optimize and add fold column once per group of hyperparameters.
        int nfolds = 5;
        addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seedForFoldColumn);
        // We might want to fine tune selection of the te column in Grid search as well ( even after TEApplicationStrategy)
      }

      if(holdoutType == KFold) {
        teColumnsToExclude = concat(columnsToEncode, new String[]{foldColumnForTE});
        encodingMap = tec.prepareEncodingMap(trainCopy, responseColumn, foldColumnForTE, true);
        trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMap, holdoutType, foldColumnForTE, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, seedForFoldColumn);
        validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnForTE, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
        testEncoded = tec.applyTargetEncoding(leaderboardCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnForTE, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
      }
      else if(holdoutType == TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut) {
        encodingMap = tec.prepareEncodingMap(trainCopy, responseColumn, null, true);

        trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMap, holdoutType, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, seedForFoldColumn);
        validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
        testEncoded = tec.applyTargetEncoding(leaderboardCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);

      } else { // Holdout None case might be always a looser as we use less data for training. So it is an unfair competition.
        // It would be more efficient to split it once before all the evaluations.
        // Note: with too small split encoding map is so unusefull so that it hurts. Should we search for this as well?
        Frame[] trainAndHoldoutSplits = splitByRatio(trainCopy, new double[]{0.7, 0.3}, seedForFoldColumn);
        Frame trainSplitForNoneCase = trainAndHoldoutSplits[0];
        Frame holdoutSplitForNoneCase = trainAndHoldoutSplits[1];
        encodingMap = tec.prepareEncodingMap(holdoutSplitForNoneCase, responseColumn, null, true);
        // Note: no need to add noise for training
        trainEncoded = tec.applyTargetEncoding(trainSplitForNoneCase, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
        validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
        testEncoded = tec.applyTargetEncoding(leaderboardCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
      }


      modelBuilder._parms._ignored_columns = concat(originalIgnoredColumns, teColumnsToExclude);

      // Temporary set encoded frame as training set and afterwards we can set original back
      // It look like model is using frames from modelBuilder but reports to the console come from modelBuilder._param object
      modelBuilder.setTrain(trainEncoded);
      modelBuilder.setValid(validEncoded);
      modelBuilder._parms.setTrain(trainEncoded._key);
      modelBuilder._parms._valid = validEncoded._key;

      try {
        score += scoreOnTest(modelBuilder, testEncoded);
      } catch (H2OIllegalArgumentException exception) {
        Log.debug("Exception during modelBuilder evaluation: " + exception.getMessage());
      }
    } catch(Exception ex ) {
      Log.debug("Exception during applying TE in TargetEncodingHyperparamsEvaluator.evaluate(): " + ex.getMessage());
    } finally {
      //Setting back original frames
      modelBuilder.setTrain(originalTrainingData);
      modelBuilder.setValid(originalValidationData);
      modelBuilder._parms.setTrain(originalTrainingData._key);
      modelBuilder._parms._valid = originalValidationData._key;

      modelBuilder._parms._ignored_columns = originalIgnoredColumns;
      if(trainEncoded != null) trainEncoded.delete();
      if(testEncoded != null) testEncoded.delete();
      validCopy.delete();
      leaderboardCopy.delete();
      trainCopy.delete();
      if (encodingMap == null) {
        Log.debug("Illegal state. encodingMap == null.");
      } else {
        TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
      }
    }
    return score;
  }

  public double evaluate(TargetEncodingParams teParams, ModelBuilder modelBuilder, ModelValidationMode modelValidationMode, Frame leaderboard, String[] columnsToEncode, long seedForFoldColumn) {
  
      switch (modelValidationMode) {
      case CV:
        return evaluateForCVMode(teParams, modelBuilder, columnsToEncode, seedForFoldColumn);
      case VALIDATION_FRAME: 
      default:
        return evaluateForValidationFrameMode(teParams, modelBuilder, leaderboard, columnsToEncode, seedForFoldColumn);
    }
  }

  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }
  
  private Frame[] splitByRatio(Frame fr,double[] ratios, long seed) {
    Key[] keys = new Key[]{Key.<Frame>make(), Key.<Frame>make()};
    return ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, seed);
  }

  @Deprecated
  static double evaluateWithGLM(Frame inputData, String responseColumn, String[] columnsToExclude) {
        GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial); //TODO we should pass a parameter to decise which Family to use
        GLMModel model = null;
        double score = 0;
        try {
          params._nlambdas = 3;
          params._response_column = responseColumn;
          params._train = inputData._key;
//          params._fold_column = foldColumnName;
          params._nfolds = 5;
          params._alpha = new double[]{.99};
          params._objective_epsilon = 1e-6;
          params._beta_epsilon = 1e-4;
          params._max_active_predictors = 50;
          params._max_iterations = 500;
          params._solver = GLMModel.GLMParameters.Solver.AUTO;
          params._lambda_search = true;
          params._ignored_columns = columnsToExclude;
          params._keep_cross_validation_fold_assignment = false;
          params._keep_cross_validation_models = false;
          model = new GLM(params).trainModel().get();
          System.out.println(model._output._training_metrics);
          
          score = model.auc();
          System.out.println(model._output._model_summary);
        } finally{
          if(model != null) model.delete();
        }
        return score;
  }
  
}
