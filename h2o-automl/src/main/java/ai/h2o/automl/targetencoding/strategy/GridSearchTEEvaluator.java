package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.Algo;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import water.DKV;
import water.Iced;
import water.Key;
import water.Keyed;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;

import java.util.*;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.concat;

public class GridSearchTEEvaluator extends Iced {
  public GridSearchTEEvaluator() {
    
  }

  public double evaluate(TargetEncodingParams teParams, ModelBuilder modelBuilder, String[] columnsToEncode, long seedForFoldColumn) {
    
    long seedForNoise = 1234; // TODO
    double score = 0;
    Map<String, Frame> encodingMap = null;
    Frame trainEncoded = null;
    Frame validEncoded = null;
    Frame testEncoded = null;
    
    // As original modelBuilder could be set up in a different way... let say nfolds = 0 
    // we need to take into consideration the fact that training frame is going to be different every time as we split validation and leaderboard from it
    Frame originalTrainingData = modelBuilder.train();
    Frame originalValidationData = modelBuilder._parms.valid();

    Frame validCopy = originalValidationData.deepCopy(Key.make("validation_frame_copy_for_evaluation" + Key.make()).toString());
    DKV.put(validCopy);
    
    String[] originalIgnoredColumns = modelBuilder._parms._ignored_columns;
    String foldColumnForTE = null;
    Model retrievedModel = null;

    try {
      // We need to apply TE taking into account the way how we train and validate our models.
      // With nfolds model will assign fold column to the data but we need those folds in TE before that. So we need to generate fold column themselves and then provide it to the model.
      // But what if we already have folds from the model search level provided by user? Is it better to use different fold assignments for TE? Maybe yes - it is similar to "n-times m-folds cross-validation" idea.

      String responseColumn = modelBuilder._parms._response_column;
      String[] teColumnsToExclude = columnsToEncode;
      TargetEncoder tec = new TargetEncoder(columnsToEncode, teParams.getBlendingParams());
      byte holdoutType = teParams.getHoldoutType();
      
      if(holdoutType == TargetEncoder.DataLeakageHandlingStrategy.KFold) {
        foldColumnForTE = modelBuilder._job._key.toString() + "_fold"; //TODO quite long but feels unique though
        //TODO default value for KFOLD target encoding. We might want to search for this value but it is quite expensive.
        // We might want to optimize and add fold column once per group of hyperparameters.
        int nfolds = 5;
        addKFoldColumn(originalTrainingData, foldColumnForTE, nfolds, seedForFoldColumn);
        // We might want to fine tune selection of the te column in Grid search as well ( even after TEApplicationStrategy)
      }
      Frame[] splits = splitByRatio(originalTrainingData, new double[]{0.8, 0.2});
      Frame trainSplit = splits[0];
      Frame testSplit = splits[1];
      
      
      if(holdoutType == TargetEncoder.DataLeakageHandlingStrategy.KFold) {
        teColumnsToExclude = concat(columnsToEncode, new String[]{foldColumnForTE});
        encodingMap = tec.prepareEncodingMap(trainSplit, responseColumn, foldColumnForTE, true);
        trainEncoded = tec.applyTargetEncoding(trainSplit, responseColumn, encodingMap, holdoutType, foldColumnForTE, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, seedForNoise);
        validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, holdoutType, foldColumnForTE, teParams.isWithBlendedAvg(), 0.0, true, seedForNoise);
        testEncoded = tec.applyTargetEncoding(testSplit, responseColumn, encodingMap, holdoutType, foldColumnForTE, teParams.isWithBlendedAvg(), 0.0, true, seedForNoise);
      }
      else if(holdoutType == TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut) {
        encodingMap = tec.prepareEncodingMap(trainSplit, responseColumn, null, true);
        trainEncoded = tec.applyTargetEncoding(trainSplit, responseColumn, encodingMap, holdoutType, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, seedForNoise);
        validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, holdoutType, teParams.isWithBlendedAvg(), 0.0, true, seedForNoise);
        testEncoded = tec.applyTargetEncoding(testSplit, responseColumn, encodingMap, holdoutType, teParams.isWithBlendedAvg(), 0.0, true, seedForNoise);

      } else { // Holdout None case might be always a looser as we use less data for training. So it is an unfair competition.
        Frame[] trainAndHoldoutSplits = splitByRatio(trainSplit, new double[]{0.8, 0.2});
        Frame trainSplitForNoneCase = trainAndHoldoutSplits[0];
        Frame holdoutSplitForNoneCase = trainAndHoldoutSplits[1];
        encodingMap = tec.prepareEncodingMap(holdoutSplitForNoneCase, responseColumn, null, true);
        // Note: no need to add noise for training
        trainEncoded = tec.applyTargetEncoding(trainSplitForNoneCase, responseColumn, encodingMap, holdoutType, teParams.isWithBlendedAvg(), 0.0, true, seedForNoise);
        validEncoded = tec.applyTargetEncoding(validCopy, responseColumn, encodingMap, holdoutType, teParams.isWithBlendedAvg(), 0.0, true, seedForNoise);
        testEncoded = tec.applyTargetEncoding(testSplit, responseColumn, encodingMap, holdoutType, teParams.isWithBlendedAvg(), 0.0, true, seedForNoise);
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
//        Key<ModelMetrics> modelMetricsKey = ModelMetricsBinomial.buildKey(retrievedModel, testEncoded);
        hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(retrievedModel, testEncoded);
        score += mmb.auc();
      } catch (H2OIllegalArgumentException exception) {
        System.out.println("Exception during modelBuilder evaluation: " + exception.getMessage());
      }
    } catch(Exception ex ) {
      System.out.println("Exception during applying TE in GridSearchTEEvaluator.evaluate(): " + ex.getMessage());
    } finally {
      if(foldColumnForTE!=null && originalTrainingData != null) {
        Vec removed = originalTrainingData.remove(foldColumnForTE);
        removed.remove();
      }
      //Setting back original frames
      modelBuilder.setTrain(originalTrainingData);
      modelBuilder.setValid(originalValidationData);
      modelBuilder._parms.setTrain(originalTrainingData._key);
      modelBuilder._parms._valid = originalValidationData._key;
      
      modelBuilder._parms._ignored_columns = originalIgnoredColumns;
      if(retrievedModel!=null) retrievedModel.delete();
      if(trainEncoded != null) trainEncoded.delete();
      if(testEncoded != null) testEncoded.delete();
      TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
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
  
  private Frame[] splitByRatio(Frame fr,double[] ratios) {
    Key[] keys = new Key[]{Key.<Frame>make("train_te_grid_search"), Key.<Frame>make("test_te_grid_search")};
    return ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, 42);
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
