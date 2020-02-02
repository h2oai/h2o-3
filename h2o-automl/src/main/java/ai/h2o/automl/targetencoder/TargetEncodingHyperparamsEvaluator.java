package ai.h2o.automl.targetencoder;

import ai.h2o.automl.targetencoder.strategy.ModelParametersSelectionStrategy;
import ai.h2o.automl.targetencoder.strategy.ModelValidationMode;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderBuilder;
import ai.h2o.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.targetencoding.TargetEncoderModel;
import hex.Model;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import water.*;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import java.util.Map;

import static ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy.KFold;
import static ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.concat;

public class TargetEncodingHyperparamsEvaluator extends ModelParametersEvaluator<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters> {

  public ModelParametersSelectionStrategy.Evaluated<TargetEncoderModel> evaluate(TargetEncoderModel.TargetEncoderParameters teParams,
                         ModelBuilder modelBuilder,
                         ModelValidationMode modelValidationMode,
                         Frame leaderboard,
                         String[] columnNamesToEncode,
                         long seedForFoldColumn) {
    Scope.enter();
    try {
      switch (modelValidationMode) {
        case CV:
          assert leaderboard == null : "Leaderboard frame should not be provided in case of CV evaluations in AutoML";
          return evaluateForCVMode(teParams, modelBuilder, columnNamesToEncode, seedForFoldColumn);
        case VALIDATION_FRAME:
        default:
          return evaluateForValidationFrameMode(teParams, modelBuilder, leaderboard, columnNamesToEncode, seedForFoldColumn);
      }
    } finally {
      Scope.exit();
    }
  }

  public ModelParametersSelectionStrategy.Evaluated<TargetEncoderModel> evaluateForCVMode(TargetEncoderModel.TargetEncoderParameters teParams, ModelBuilder clonedModelBuilder, String[] columnNamesToEncode, long seedForFoldColumn) {

    final TargetEncoderModel targetEncoderModel;
    double score = 0;
    Frame trainEncoded = null;

    Frame originalTrain = clonedModelBuilder._parms.train();
    Frame trainCopy = originalTrain.deepCopy(Key.make().toString());
    DKV.put(trainCopy);
    Scope.track(trainCopy);

    String[] originalIgnoredColumns = clonedModelBuilder._parms._ignored_columns;
    try {

      assert teParams._data_leakage_handling == KFold : "Only `KFold` strategy is being used in current version for CV mode.";


      { //Applying encoding here
        // Maybe we can use the same folds that we will use for splitting but in that case we will have only 4 folds for encoding map
        // generation and application of this map to the frame that was used for creating map
        String foldColumnForTE = clonedModelBuilder._job._key.toString() + "_fold";
        int nfolds = 5;
        addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seedForFoldColumn);

        teParams._response_column = clonedModelBuilder._parms._response_column;
        teParams._fold_column = foldColumnForTE;
        String[] ignoredColumnsDuringEncoding = ArrayUtils.difference(trainCopy.names(), concat(columnNamesToEncode, new String[]{teParams._response_column, foldColumnForTE}));
        teParams._ignored_columns = ignoredColumnsDuringEncoding;
        teParams._train = trainCopy._key;
        teParams._seed = seedForFoldColumn;

        TargetEncoderBuilder job = new TargetEncoderBuilder(teParams);
        targetEncoderModel = job.trainModel().get();
//          Scope.track_generic(targetEncoderModel);
        trainEncoded = targetEncoderModel.score(trainCopy);
        Scope.track(trainEncoded);
        trainEncoded.remove(foldColumnForTE).remove();
//        printOutFrameAsTable(trainEncoded, false, 20);
//        Job export = Frame.export(trainEncoded, "encoder_" + teParams.toString(), trainEncoded._key.toString(), true, 1);
//        export.get();

      }

      // TODO maybe remove foldColumnForTE here?
      clonedModelBuilder._parms._ignored_columns = concat(originalIgnoredColumns, columnNamesToEncode);
      clonedModelBuilder.setTrain(trainEncoded);

      score += scoreCV(clonedModelBuilder);
    } finally {
      //Setting back original data
      clonedModelBuilder.setTrain(originalTrain);
      clonedModelBuilder._parms._ignored_columns = originalIgnoredColumns; //TODO Do we need to do this if we made a clone? Or we can do this once before evaluating all parameters and after all of them
    }
    return new ModelParametersSelectionStrategy.Evaluated<>(targetEncoderModel, score);
  }

  private double scoreCV(ModelBuilder modelBuilder) { // TODO move it in when no need to call scoreCV twice
    Model retrievedModel = null;
    Keyed model = null;
    try {
      model = modelBuilder.trainModel().get();
      retrievedModel = DKV.getGet(modelBuilder.dest());
    } catch (Throwable ex) {
      throw ex;
    }
    double cvScore = retrievedModel.loss();
    retrievedModel.delete();
    retrievedModel.deleteCrossValidationModels();
    retrievedModel.deleteCrossValidationPreds();

    return cvScore;
  }

  private double scoreOnTest(ModelBuilder modelBuilder, Frame testEncodedFrame) {
    Keyed model = modelBuilder.trainModel().get();
    Model retrievedModel = DKV.getGet(model._key);
    retrievedModel.score(testEncodedFrame).delete();

    hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(retrievedModel, testEncodedFrame);
    if(retrievedModel!=null) retrievedModel.delete();
    return mmb.auc();
  }

  public ModelParametersSelectionStrategy.Evaluated<TargetEncoderModel> evaluateForValidationFrameMode(TargetEncoderModel.TargetEncoderParameters teParams, ModelBuilder clonedModelBuilder, Frame leaderboard, String[] columnNamesToEncode, long seedForFoldColumn) {
      double score = 0;
      Map<String, Frame> encodingMap = null;
      Frame trainEncoded, validEncoded, leaderBoardEncoded = null;
      Frame originalTrain = clonedModelBuilder._parms.train();
      Frame trainCopy = originalTrain.deepCopy(Key.make("train_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(trainCopy);

      Frame originalValid = clonedModelBuilder._parms.valid();
      Frame validCopy = originalValid.deepCopy(Key.make("valid_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(validCopy);

      Frame leaderboardCopy = leaderboard.deepCopy(Key.make("leaderboard_frame_copy_for_mb_validation_case" + Key.make()).toString());
      DKV.put(leaderboardCopy);


      Scope.track(trainCopy, validCopy, leaderboardCopy);

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
          foldColumnForTE = clonedModelBuilder._job._key.toString() + "_fold";
          //Note: default value for KFOLD target encoding. We might want to search for this value but it is quite expensive.
          // We might want to optimize and add fold column once per group of hyperparameters.
          int nfolds = 5;
          addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seedForFoldColumn);

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
        clonedModelBuilder._parms.setTrain(trainEncoded._key);
        clonedModelBuilder._parms.setValid(validEncoded._key);

        score += scoreOnTest(clonedModelBuilder, leaderBoardEncoded);

      } finally {

        clonedModelBuilder._parms._ignored_columns = originalIgnoredColumns;
        clonedModelBuilder._parms.setTrain(originalTrain._key);
        clonedModelBuilder._parms.setValid(originalValid._key);

        if (encodingMap != null) TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
      }
      return new ModelParametersSelectionStrategy.Evaluated<>(null, score); //TODO null !!!!
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
