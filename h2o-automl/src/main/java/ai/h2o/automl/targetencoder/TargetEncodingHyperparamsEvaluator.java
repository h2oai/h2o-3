package ai.h2o.automl.targetencoder;

import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.targetencoding.TargetEncoderModel;
import hex.Model;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.Map;

import static ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy.KFold;
import static ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.concat;


public class TargetEncodingHyperparamsEvaluator extends Iced {

  public double evaluate(TargetEncoderModel.TargetEncoderParameters teParams, ModelBuilder modelBuilder, ModelValidationMode modelValidationMode, Frame leaderboard, String[] columnNamesToEncode, long seedForFoldColumn) {
    Scope.enter();
    try {
      Frame trainCopy = modelBuilder._parms.train().deepCopy(Key.make("train_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(trainCopy);
      Scope.track(trainCopy);
      modelBuilder.setTrain(trainCopy);

      switch (modelValidationMode) {
        case CV:
          assert leaderboard == null : "Leaderboard frame should not be provided in case of CV evaluations in AutoML";
          return evaluateForCVMode(teParams, modelBuilder, columnNamesToEncode, seedForFoldColumn);
        case VALIDATION_FRAME:
        default:
          Frame validCopy = modelBuilder._parms.valid().deepCopy(Key.make("valid_frame_copy_for_mb_validation_case" + Key.make()).toString()); //TODO  change keys
          DKV.put(validCopy);
          modelBuilder.setValid(validCopy);

          Frame leaderboardCopy = leaderboard.deepCopy(Key.make("leaderboard_frame_copy_for_mb_validation_case" + Key.make()).toString());
          DKV.put(leaderboardCopy);

          Scope.track(validCopy, leaderboardCopy);

          return evaluateForValidationFrameMode(teParams, modelBuilder, leaderboardCopy, columnNamesToEncode, seedForFoldColumn);
      }
    } finally {
      Scope.exit();
    }
  }

  public double evaluateForCVMode(TargetEncoderModel.TargetEncoderParameters teParams, ModelBuilder clonedModelBuilder, String[] columnNamesToEncode, long seedForFoldColumn) {

    double score = 0;
    if (columnNamesToEncode.length != 0) {
      Map<String, Frame> encodingMap = null;
      Frame trainEncoded = null;

      Frame trainCopy = clonedModelBuilder.train();

      String[] originalIgnoredColumns = clonedModelBuilder._parms._ignored_columns;
      try {
        String responseColumn = clonedModelBuilder._parms._response_column;
        String[] teColumnsToExclude = columnNamesToEncode;
        TargetEncoder tec = new TargetEncoder(columnNamesToEncode);
        TargetEncoder.DataLeakageHandlingStrategy holdoutType = teParams._data_leakage_handling;

        String foldColumnForTE = null;

        switch (holdoutType) {
          case KFold:
            // Maybe we can use the same folds that we will use for splitting but in that case we will have only 4 folds for encoding map
            // generation and application of this map to the frame that was used for creating map
            foldColumnForTE = clonedModelBuilder._job._key.toString() + "_fold";
            int nfolds = 5;
            addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seedForFoldColumn);

            //TODO consider optimising this as encoding map will be the same for whole runs as long as we only use KFold scenario with same seed for fold assignments
            encodingMap = tec.prepareEncodingMap(trainCopy, responseColumn, foldColumnForTE, true);

            trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMap, KFold, foldColumnForTE, teParams._blending, teParams._noise_level, true, teParams.getBlendingParameters(), seedForFoldColumn);
            Scope.track(trainEncoded);
            break;
          case LeaveOneOut:
          case None:
          default:
            // For `None` strategy we can make sure that holdouts from `otherFolds` frame are being chosen in a mutually exclusive way across all folds
            throw new IllegalStateException("Only `KFold` strategy is being used in current version for CV mode.");
        }

        clonedModelBuilder._parms._ignored_columns = concat(concat(originalIgnoredColumns, teColumnsToExclude), new String[]{foldColumnForTE});

        clonedModelBuilder.setTrain(trainEncoded);
        score += scoreCV(clonedModelBuilder);
      } catch (Exception ex) {
        throw ex;
      } finally {
        //Setting back original data
        clonedModelBuilder._parms._ignored_columns = originalIgnoredColumns;

        if (encodingMap == null) {
          Log.debug("Illegal state. encodingMap == null.");
        } else {
          TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
        }
      }
    } else {
      score += scoreCV(clonedModelBuilder);
    }
    return score;
  }

  private double scoreCV(ModelBuilder modelBuilder) { // TODO move it in when no need to call scoreCV twice
    Model retrievedModel = null;
    Keyed model = modelBuilder.trainModel().get();
    retrievedModel = DKV.getGet(model._key);
    double cvScore = retrievedModel._output._cross_validation_metrics.auc_obj()._auc;
    retrievedModel.delete();
    retrievedModel.deleteCrossValidationModels();
    retrievedModel.deleteCrossValidationPreds();

    return cvScore;
  }

  private double scoreOnTest(ModelBuilder modelBuilder, Frame testEncodedFrame) {
    Model retrievedModel = null;
    Keyed model = modelBuilder.trainModelOnH2ONode().get(); // or modelBuilder.trainModel().get();  ?
    retrievedModel = DKV.getGet(model._key);
    retrievedModel.score(testEncodedFrame).delete();

    hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(retrievedModel, testEncodedFrame);
    if(retrievedModel!=null) retrievedModel.delete();
//    model.remove();
    return mmb.auc();
  }

  public double evaluateForValidationFrameMode(TargetEncoderModel.TargetEncoderParameters teParams, ModelBuilder clonedModelBuilder, Frame leaderboard, String[] columnNamesToEncode, long seedForFoldColumn) {
    double score = 0;
    if (columnNamesToEncode.length != 0) {
      Map<String, Frame> encodingMap = null;
      Frame trainEncoded, validEncoded, leaderBoardEncoded = null;

      Frame trainCopy = clonedModelBuilder.train();
      Frame validCopy = clonedModelBuilder.valid();

      String[] originalIgnoredColumns = clonedModelBuilder._parms._ignored_columns;
      String foldColumnForTE = null;
      try {
        // We need to apply TE taking into account the way how we train and validate our models.
        // With nfolds model will assign fold column to the data but we need those folds in TE before that. So we need to generate fold column themselves and then provide it to the model.
        // But what if we already have folds from the model search level provided by user? Is it better to use different fold assignments for TE? Maybe yes - it is similar to "n-times m-folds cross-validation" idea.

        String responseColumn = clonedModelBuilder._parms._response_column;
        String[] teColumnsToExclude = columnNamesToEncode;
        TargetEncoder tec = new TargetEncoder(columnNamesToEncode);
        TargetEncoder.DataLeakageHandlingStrategy holdoutType = teParams._data_leakage_handling;

        if (holdoutType == KFold) {
          foldColumnForTE = clonedModelBuilder._job._key.toString() + "_fold"; //TODO quite long but feels unique though
          //TODO default value for KFOLD target encoding. We might want to search for this value but it is quite expensive.
          // We might want to optimize and add fold column once per group of hyperparameters.
          int nfolds = 5;
          addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seedForFoldColumn);
          // We might want to fine tune selection of the te column in Grid search as well ( even after TEApplicationStrategy)

          teColumnsToExclude = concat(teColumnsToExclude, new String[]{foldColumnForTE});
          encodingMap = tec.prepareEncodingMap(trainCopy, responseColumn, foldColumnForTE, true);
          trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMap, KFold, foldColumnForTE, teParams._blending, teParams._noise_level, true, teParams.getBlendingParameters(), seedForFoldColumn);
          validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnForTE, teParams._blending, 0.0, true, teParams.getBlendingParameters(), seedForFoldColumn);
          leaderBoardEncoded = tec.applyTargetEncoding(leaderboard, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnForTE, teParams._blending, 0.0, true, teParams.getBlendingParameters(), seedForFoldColumn);
        } else if (holdoutType == LeaveOneOut) {
          encodingMap = tec.prepareEncodingMap(trainCopy, responseColumn, null, true);

          trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMap, LeaveOneOut, teParams._blending, teParams._noise_level, true, teParams.getBlendingParameters(), seedForFoldColumn);
          validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams._blending, 0.0, true, teParams.getBlendingParameters(),seedForFoldColumn);
          leaderBoardEncoded = tec.applyTargetEncoding(leaderboard, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams._blending, 0.0, true, teParams.getBlendingParameters(), seedForFoldColumn);

        } else { // Holdout None case might be always a looser as we use less data for training. So it is an unfair competition.
          // It would be more efficient to split it once before all the evaluations.
          // Note: with too small split encoding map is so unusefull so that it hurts. Should we search for this as well?
          Frame[] trainAndHoldoutSplits = splitByRatio(trainCopy, new double[]{0.7, 0.3}, seedForFoldColumn);
          Frame trainSplitForNoneCase = trainAndHoldoutSplits[0];
          Frame holdoutSplitForNoneCase = trainAndHoldoutSplits[1];
          encodingMap = tec.prepareEncodingMap(holdoutSplitForNoneCase, responseColumn, null, true);
          // Note: no need to add noise for training
          trainEncoded = tec.applyTargetEncoding(trainSplitForNoneCase, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams._blending, 0.0, true, teParams.getBlendingParameters(), seedForFoldColumn);
          validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams._blending, 0.0, true, teParams.getBlendingParameters(), seedForFoldColumn);
          leaderBoardEncoded = tec.applyTargetEncoding(leaderboard, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams._blending, 0.0, true, teParams.getBlendingParameters(), seedForFoldColumn);
          trainSplitForNoneCase.delete();
          holdoutSplitForNoneCase.delete();
        }

        Scope.track(trainEncoded, validEncoded, leaderBoardEncoded);


        clonedModelBuilder._parms._ignored_columns = concat(originalIgnoredColumns, teColumnsToExclude);

        // Temporary set encoded frame as training set and afterwards we can set original back
        // It look like model is using frames from modelBuilder but reports to the console come from modelBuilder._param object
        clonedModelBuilder.setTrain(trainEncoded);
        clonedModelBuilder.setValid(validEncoded);

        try {
          score += scoreOnTest(clonedModelBuilder, leaderBoardEncoded);
        } catch (H2OIllegalArgumentException exception) {
          Log.debug("Exception during modelBuilder evaluation: " + exception.getMessage());
          throw exception;
        }
      } catch (Exception ex) {
        Log.debug("Exception during applying TE in TargetEncodingHyperparamsEvaluator.evaluate(): " + ex.getMessage());
        throw ex;

      } finally {

        clonedModelBuilder._parms._ignored_columns = originalIgnoredColumns;

        if (encodingMap == null) {
          Log.debug("Illegal state. encodingMap == null.");
        } else {
          TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
        }
      }
    } else { //TODO handle this case somewhere before
      score += scoreOnTest(clonedModelBuilder, leaderboard);
    }
    return score;
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


}
