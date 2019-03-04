package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.Model;
import hex.ModelBuilder;
import hex.ModelMetricsBinomial;
import hex.splitframe.ShuffleSplitFrame;
import water.DKV;
import water.Iced;
import water.Key;
import water.Keyed;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.rapids.StratificationAssistant;
import water.util.Log;

import java.util.Map;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.concat;

public class GridSearchTEStratifiedEvaluator extends Iced {
  public GridSearchTEStratifiedEvaluator() {
    
  }

  public double evaluate(TargetEncodingParams teParams, ModelBuilder modelBuilder, Frame leaderboard, String[] columnsToEncode, long seedForFoldColumn) {
    
    double score = 0;
    Map<String, Frame> encodingMap = null;
    Frame trainEncoded = null;
    Frame validEncoded = null;
    Frame testEncoded = null;
    String responseColumn = modelBuilder._parms._response_column;
    
    boolean debugExport = false;
    
    // As original modelBuilder could be set up in a different way... let say nfolds = 0 
    // we need to take into consideration the fact that training frame is going to be different every time as we split validation and leaderboard from it
    Frame originalTrainingData = modelBuilder.train();
    Frame originalValidationData = modelBuilder._parms.valid();

    Frame trainCopy = originalTrainingData.deepCopy(Key.make("train_frame_copy_for_evaluation" + Key.make()).toString());
    DKV.put(trainCopy);
    double stratifiedSamplingRatio = 0.8;
    Frame stratifiedTrainCopy = StratificationAssistant.sample(trainCopy, responseColumn, stratifiedSamplingRatio, seedForFoldColumn);
    
    Frame validCopy = originalValidationData.deepCopy(Key.make("validation_frame_copy_for_evaluation" + Key.make()).toString());
    DKV.put(validCopy);
    Frame stratifiedValidCopy = StratificationAssistant.sample(validCopy, responseColumn, stratifiedSamplingRatio, seedForFoldColumn);

    Frame leaderboardCopy = leaderboard.deepCopy(Key.make("leaderboard_frame_copy_for_evaluation" + Key.make()).toString());
    DKV.put(leaderboardCopy);
    Frame stratifiedLeaderboardCopy = StratificationAssistant.sample(leaderboardCopy, responseColumn, stratifiedSamplingRatio, seedForFoldColumn);


    String[] originalIgnoredColumns = modelBuilder._parms._ignored_columns;
    String foldColumnForTE = null;
    Model retrievedModel = null;

    try {
      // We need to apply TE taking into account the way how we train and validate our models.
      // With nfolds model will assign fold column to the data but we need those folds in TE before that. So we need to generate fold column themselves and then provide it to the model.
      // But what if we already have folds from the model search level provided by user? Is it better to use different fold assignments for TE? Maybe yes - it is similar to "n-times m-folds cross-validation" idea.
      String[] teColumnsToExclude = columnsToEncode;
      TargetEncoder tec = new TargetEncoder(columnsToEncode, teParams.getBlendingParams());
      byte holdoutType = teParams.getHoldoutType();
      
      if(holdoutType == TargetEncoder.DataLeakageHandlingStrategy.KFold) {
        foldColumnForTE = modelBuilder._job._key.toString() + "_fold"; //TODO quite long but feels unique though
        //TODO default value for KFOLD target encoding. We might want to search for this value but it is quite expensive.
        // We might want to optimize and add fold column once per group of hyperparameters.
        int nfolds = 5;
        addKFoldColumn(stratifiedTrainCopy, foldColumnForTE, nfolds, seedForFoldColumn);
        // We might want to fine tune selection of the te column in Grid search as well ( even after TEApplicationStrategy)
      }
      
      if(holdoutType == TargetEncoder.DataLeakageHandlingStrategy.KFold) {
        teColumnsToExclude = concat(columnsToEncode, new String[]{foldColumnForTE});
        encodingMap = tec.prepareEncodingMap(stratifiedTrainCopy, responseColumn, foldColumnForTE, true);
        trainEncoded = tec.applyTargetEncoding(stratifiedTrainCopy, responseColumn, encodingMap, holdoutType, foldColumnForTE, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, seedForFoldColumn);
        validEncoded = tec.applyTargetEncoding(stratifiedValidCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnForTE, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
        testEncoded = tec.applyTargetEncoding(stratifiedLeaderboardCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnForTE, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
      }
      else if(holdoutType == TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut) {
        encodingMap = tec.prepareEncodingMap(stratifiedTrainCopy, responseColumn, null, true);
        if(debugExport) Frame.export(stratifiedTrainCopy, "trainCopy_evaluator.csv", stratifiedTrainCopy._key.toString(), true, 1).get();

        trainEncoded = tec.applyTargetEncoding(stratifiedTrainCopy, responseColumn, encodingMap, holdoutType, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, seedForFoldColumn);
        validEncoded = tec.applyTargetEncoding(stratifiedValidCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
        if(debugExport) Frame.export(stratifiedLeaderboardCopy, "leaderboard_before_evaluator.csv", stratifiedLeaderboardCopy._key.toString(), true, 1).get();

        testEncoded = tec.applyTargetEncoding(stratifiedLeaderboardCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
        if(debugExport)Frame.export(testEncoded, "leaderboard_encoded_evaluator.csv", testEncoded._key.toString(), true, 1).get();

      } else { // Holdout None case might be always a looser as we use less data for training. So it is an unfair competition.
        // It would be more efficient to split it once before all the evaluations.
        // Note: with too small split encoding map is so unusefull so that it hurts. Should we search for this as well?
        Frame[] trainAndHoldoutSplits = splitByRatio(stratifiedTrainCopy, new double[]{0.7, 0.3}, seedForFoldColumn);
        Frame trainSplitForNoneCase = trainAndHoldoutSplits[0];
        Frame holdoutSplitForNoneCase = trainAndHoldoutSplits[1];
        encodingMap = tec.prepareEncodingMap(holdoutSplitForNoneCase, responseColumn, null, true);
//         Note: no need to add noise for training
        if(debugExport)Frame.export(trainSplitForNoneCase, "train_none_before_evaluator.csv", trainSplitForNoneCase._key.toString(), true, 1).get();

        trainEncoded = tec.applyTargetEncoding(trainSplitForNoneCase, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
        if(debugExport)Frame.export(trainEncoded, "train_none_evaluator.csv", trainEncoded._key.toString(), true, 1).get();

        validEncoded = tec.applyTargetEncoding(stratifiedValidCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
        testEncoded = tec.applyTargetEncoding(stratifiedLeaderboardCopy, responseColumn, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, teParams.isWithBlendedAvg(), 0.0, true, seedForFoldColumn);
      }


      modelBuilder._parms._ignored_columns = concat(originalIgnoredColumns, teColumnsToExclude);

      // Temporary set encoded frame as training set and afterwards we can set original back
      // It look like model is using frames from modelBuilder but reports to the console come from modelBuilder._param object
      modelBuilder.setTrain(trainEncoded);
      modelBuilder.setValid(validEncoded);
      modelBuilder._parms.setTrain(trainEncoded._key);
      modelBuilder._parms._valid = validEncoded._key;
      
      try {
        Keyed model = modelBuilder.trainModel().get();
        retrievedModel = DKV.getGet(model._key);
        retrievedModel.score(testEncoded);
        if(debugExport) Frame.export(encodingMap.get("home.dest"), "encoding_map_evaluator.csv", encodingMap.get("home.dest")._key.toString(), true, 1).get();

        ModelMetricsBinomial mmb = ModelMetricsBinomial.getFromDKV(retrievedModel, testEncoded);
        score += mmb.auc();
      } catch (H2OIllegalArgumentException exception) {
        Log.debug("Exception during modelBuilder evaluation: " + exception.getMessage());
      }
    } catch(Exception ex ) {
      Log.debug("Exception during applying TE in GridSearchTEEvaluator.evaluate(): " + ex.getMessage());
    } finally {
      //Setting back original frames
      modelBuilder.setTrain(originalTrainingData);
      modelBuilder.setValid(originalValidationData);
      modelBuilder._parms.setTrain(originalTrainingData._key);
      modelBuilder._parms._valid = originalValidationData._key;
      
      modelBuilder._parms._ignored_columns = originalIgnoredColumns;
      if(retrievedModel!=null) retrievedModel.delete();
      if(trainEncoded != null) trainEncoded.delete();
      if(testEncoded != null) testEncoded.delete();
      trainCopy.delete();
      validCopy.delete();
      leaderboardCopy.delete();
      stratifiedTrainCopy.delete();
      stratifiedValidCopy.delete();
      stratifiedLeaderboardCopy.delete();
      if (encodingMap == null) {
        Log.debug("Illegal state. encodingMap == null.");
      } else {
        TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
      }
    }
    return score;
  }
  
  private Frame[] splitByRatio(Frame fr,double[] ratios, long seed) {
    Key[] keys = new Key[]{Key.<Frame>make(), Key.<Frame>make()};
    return ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, seed);
  }
}
