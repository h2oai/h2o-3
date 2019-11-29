package ai.h2o.automl.targetencoder;

import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderFrameHelper;
import hex.Model;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import water.DKV;
import water.Iced;
import water.Key;
import water.Keyed;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.Map;

import static ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy.KFold;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.concat;


public class TargetEncodingHyperparamsEvaluator extends Iced {
  public TargetEncodingHyperparamsEvaluator() {

  }

  public double evaluateForCVMode(TargetEncodingParams teParams, ModelBuilder clonedModelBuilder, long seedForFoldColumn) {

    double score = 0;
    if (teParams.getColumnsToEncode().length != 0) {
      Map<String, Frame> encodingMap = null;
      Frame trainEncoded = null;
      Key<Frame> trainPreviousKey = null;
      Frame originalTrainingData = clonedModelBuilder.train();

      Frame trainCopy = originalTrainingData.deepCopy(Key.make("train_frame_copy_for_evaluation" + Key.make()).toString());
      DKV.put(trainCopy);

      String[] originalIgnoredColumns = clonedModelBuilder._parms._ignored_columns;
      try {
        String responseColumn = clonedModelBuilder._parms._response_column;
        String[] teColumnsToExclude = teParams.getColumnsToEncode();
        TargetEncoder tec = new TargetEncoder(teParams.getColumnsToEncode());
        TargetEncoder.DataLeakageHandlingStrategy holdoutType = teParams.getHoldoutType();

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

            trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMap, KFold, foldColumnForTE, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, teParams.getBlendingParams(), seedForFoldColumn);
            break;
          case LeaveOneOut:
          case None:
          default:
            // For `None` strategy we can make sure that holdouts from `otherFolds` frame are being chosen in a mutually exclusive way across all folds
            throw new IllegalStateException("Only `KFold` strategy is being used in current version for CV mode.");
        }

        clonedModelBuilder._parms._ignored_columns = concat(concat(originalIgnoredColumns, teColumnsToExclude), new String[]{foldColumnForTE});

        clonedModelBuilder.setTrain(trainEncoded);

        trainPreviousKey = clonedModelBuilder._parms._train;
        clonedModelBuilder._parms.setTrain(trainEncoded._key);
        trainPreviousKey.get().delete();

        score += scoreCV(clonedModelBuilder);
      } catch (Exception ex) {
        throw ex;
      } finally {
        //Setting back original data
        clonedModelBuilder._parms._ignored_columns = originalIgnoredColumns;

        trainCopy.delete();
        if (trainEncoded != null) trainEncoded.delete();
        if (encodingMap == null) {
          Log.debug("Illegal state. encodingMap == null.");
        } else {
          TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
        }
      }
    } else {
      score += scoreCV(clonedModelBuilder);
    }
    //Cleanup for cloned builder
    clonedModelBuilder.train().delete();
    if (clonedModelBuilder._parms.train() != null) clonedModelBuilder._parms.train().delete();

    return score;
  }

  private double scoreCV(ModelBuilder modelBuilder) {
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

  public double evaluateForValidationFrameMode(TargetEncodingParams teParams, ModelBuilder clonedModelBuilder, Frame leaderboard, long seedForFoldColumn) {
    double score = 0;
    if (teParams.getColumnsToEncode().length != 0) {
      Map<String, Frame> encodingMap = null;
      Frame trainEncoded = null;
      Frame validEncoded = null;
      Frame leaderBoardEncoded = null;

      // As original modelBuilder could be set up in a different way... let say nfolds = 0
      // we need to take into consideration the fact that training frame is going to be different every time as we split validation and leaderboard from it
      Frame originalTrainingData = clonedModelBuilder._parms.train();
      Frame originalValidationData = clonedModelBuilder._parms.valid();

      Frame trainCopy = originalTrainingData.deepCopy(Key.make("train_frame_copy_for_evaluation" + Key.make()).toString());
      DKV.put(trainCopy);

      Frame validCopy = originalValidationData.deepCopy(Key.make("validation_frame_copy_for_evaluation" + Key.make()).toString());
      DKV.put(validCopy);

      Frame leaderboardCopy = leaderboard.deepCopy(Key.make("leaderboard_frame_copy_for_evaluation" + Key.make()).toString());
      DKV.put(leaderboardCopy);

      String[] originalIgnoredColumns = clonedModelBuilder._parms._ignored_columns;
      String foldColumnForTE = null;
      Key<Frame> trainPreviousKey = null;
      Key<Frame> validPreviosKey = null;
      try {
        // We need to apply TE taking into account the way how we train and validate our models.
        // With nfolds model will assign fold column to the data but we need those folds in TE before that. So we need to generate fold column themselves and then provide it to the model.
        // But what if we already have folds from the model search level provided by user? Is it better to use different fold assignments for TE? Maybe yes - it is similar to "n-times m-folds cross-validation" idea.

        String responseColumn = clonedModelBuilder._parms._response_column;
        String[] teColumnsToExclude = teParams.getColumnsToEncode();
        TargetEncoder tec = new TargetEncoder(teParams.getColumnsToEncode());
        TargetEncoder.DataLeakageHandlingStrategy holdoutType = teParams.getHoldoutType();

        if (holdoutType == KFold) {
          foldColumnForTE = clonedModelBuilder._job._key.toString() + "_fold"; //TODO quite long but feels unique though
          //TODO default value for KFOLD target encoding. We might want to search for this value but it is quite expensive.
          // We might want to optimize and add fold column once per group of hyperparameters.
          int nfolds = 5;
          addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seedForFoldColumn);
          // We might want to fine tune selection of the te column in Grid search as well ( even after TEApplicationStrategy)
        }

        if (holdoutType == KFold) {
          teColumnsToExclude = concat(teColumnsToExclude, new String[]{foldColumnForTE});
          encodingMap = tec.prepareEncodingMap(trainCopy, responseColumn, foldColumnForTE, true);
          trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMap, KFold, foldColumnForTE, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, teParams.getBlendingParams(), seedForFoldColumn);
          validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnForTE, teParams.isWithBlendedAvg(), 0.0, true, teParams.getBlendingParams(), seedForFoldColumn);
          leaderBoardEncoded = tec.applyTargetEncoding(leaderboardCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnForTE, teParams.isWithBlendedAvg(), 0.0, true, teParams.getBlendingParams(), seedForFoldColumn);
        } else if (holdoutType == TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut) {
          encodingMap = tec.prepareEncodingMap(trainCopy, responseColumn, null, true);

          trainEncoded = tec.applyTargetEncoding(trainCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, teParams.getBlendingParams(), seedForFoldColumn);
          validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, teParams.getBlendingParams(),seedForFoldColumn);
          leaderBoardEncoded = tec.applyTargetEncoding(leaderboardCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, teParams.getBlendingParams(), seedForFoldColumn);

        } else { // Holdout None case might be always a looser as we use less data for training. So it is an unfair competition.
          // It would be more efficient to split it once before all the evaluations.
          // Note: with too small split encoding map is so unusefull so that it hurts. Should we search for this as well?
          Frame[] trainAndHoldoutSplits = splitByRatio(trainCopy, new double[]{0.7, 0.3}, seedForFoldColumn);
          Frame trainSplitForNoneCase = trainAndHoldoutSplits[0];
          Frame holdoutSplitForNoneCase = trainAndHoldoutSplits[1];
          encodingMap = tec.prepareEncodingMap(holdoutSplitForNoneCase, responseColumn, null, true);
          // Note: no need to add noise for training
          trainEncoded = tec.applyTargetEncoding(trainSplitForNoneCase, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, teParams.getBlendingParams(), seedForFoldColumn);
          validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, teParams.getBlendingParams(), seedForFoldColumn);
          leaderBoardEncoded = tec.applyTargetEncoding(leaderboardCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, teParams.getBlendingParams(), seedForFoldColumn);
          trainSplitForNoneCase.delete();
          holdoutSplitForNoneCase.delete();
        }


        clonedModelBuilder._parms._ignored_columns = concat(originalIgnoredColumns, teColumnsToExclude);

        // Temporary set encoded frame as training set and afterwards we can set original back
        // It look like model is using frames from modelBuilder but reports to the console come from modelBuilder._param object
        clonedModelBuilder.setTrain(trainEncoded);
        clonedModelBuilder.setValid(validEncoded);

        //As we are replacing with encoded splits we need to keep track of the previous keys
        trainPreviousKey = clonedModelBuilder._parms._train;
        validPreviosKey = clonedModelBuilder._parms._valid;
        clonedModelBuilder._parms.setTrain(trainEncoded._key);
        clonedModelBuilder._parms.setValid(validEncoded._key);
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
        //Removing unused splits
        trainPreviousKey.get().delete();
        validPreviosKey.get().delete();

        clonedModelBuilder._parms._ignored_columns = originalIgnoredColumns;
        if (trainEncoded != null) trainEncoded.delete();
        if (validEncoded != null) validEncoded.delete();
        if (leaderBoardEncoded != null) leaderBoardEncoded.delete();

        validCopy.delete();
        leaderboardCopy.delete();
        trainCopy.delete();
        if (encodingMap == null) {
          Log.debug("Illegal state. encodingMap == null.");
        } else {
          TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
        }
      }
    } else {
      Frame leaderboardCopy = leaderboard.deepCopy(Key.make("leaderboard_frame_copy_for_evaluation" + Key.make()).toString()); // TODO consider to avoid copy
      DKV.put(leaderboardCopy);
      score += scoreOnTest(clonedModelBuilder, leaderboard);
      leaderboardCopy.delete();
    }
    //Cleanup for cloned builder
    clonedModelBuilder.train().delete();
    clonedModelBuilder.valid().delete();
    if(clonedModelBuilder._parms.train()!=null) clonedModelBuilder._parms.train().delete();
    if(clonedModelBuilder._parms.valid()!=null) clonedModelBuilder._parms.valid().delete();
    return score;
  }

  public double evaluate(TargetEncodingParams teParams, ModelBuilder modelBuilder, ModelValidationMode modelValidationMode, Frame leaderboard, long seedForFoldColumn) {

    switch (modelValidationMode) {
      case CV:
        assert leaderboard == null : "Leaderboard frame should not be provided in case of CV evaluations in AutoML";
        return evaluateForCVMode(teParams, modelBuilder, seedForFoldColumn);
      case VALIDATION_FRAME:
      default:
        return evaluateForValidationFrameMode(teParams, modelBuilder, leaderboard, seedForFoldColumn);
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


}
