package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.Algo;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.Model;
import hex.ModelBuilder;
import hex.ScoreKeeper;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import water.DKV;
import water.Iced;
import water.Keyed;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;

import java.util.*;

import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.concat;

/**
 * For now it should be named RandomGridSearchTEParamsStrategy
 */
public class GridSearchTEEvaluator extends Iced {
  public GridSearchTEEvaluator() {
    
  }

  public double evaluate(TargetEncodingParams teParams, ModelBuilder modelBuilder, String[] columnsToEncode, long seedForFoldColumn) {
    
    long seedForNoise = 1234;
    double score = 0;
    Map<String, Frame> encodingMap = null;
    Frame trainEncoded = null;
    Frame originalTrainingData = modelBuilder.train();
    String[] originalIgnoredColumns = modelBuilder._parms._ignored_columns;
    String foldColumnForTE = null;
    Model retrievedModel = null;
    try {
      // We need to apply TE taking into account the way how we train and validate our models.
      // with nfolds model will assign fold column to the data but we need those folds in TE before that. So we need to generate and then provide it to the model.
      // but what if we already have folds from the model search level? Is it better to use different fold assignments? Maybe yes - it is similar to "n-times m-folds cross-validation" idea.

      String responseColumn = modelBuilder._parms._response_column;
      String[] teColumnsToExclude = null;
      TargetEncoder tec = new TargetEncoder(columnsToEncode, teParams.getBlendingParams());
      
      if(teParams.getHoldoutType() == TargetEncoder.DataLeakageHandlingStrategy.KFold) {
        foldColumnForTE = modelBuilder._job._key.toString() + "_fold"; //TODO quite long but feels unique though
        teColumnsToExclude = concat(columnsToEncode, new String[]{foldColumnForTE});

        //TODO default value for KFOLD target encoding. We might want to search for this value but it is quite expensive.
        // We might want to optimize and add fold column once per group of hyperparameters.
        int nfolds = 5; 
        addKFoldColumn(originalTrainingData, foldColumnForTE, nfolds, seedForFoldColumn);
        // We might want to fine tune selection of the te column in Grid search as well ( even after TEApplicationStrategy)
        
        encodingMap = tec.prepareEncodingMap(originalTrainingData, responseColumn, foldColumnForTE, true);
        trainEncoded = tec.applyTargetEncoding(originalTrainingData, responseColumn, encodingMap, teParams.getHoldoutType(), foldColumnForTE, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, seedForNoise);

      }
      else if(teParams.getHoldoutType() == TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut) {
        teColumnsToExclude = columnsToEncode;
        encodingMap = tec.prepareEncodingMap(originalTrainingData, responseColumn, null, true);
        trainEncoded = tec.applyTargetEncoding(originalTrainingData, responseColumn, encodingMap, teParams.getHoldoutType(), teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, seedForNoise);
      } else {
        throw new IllegalStateException("DataLeakageHandlingStrategy.None strategy is not supported");
      }
      
      modelBuilder._parms._ignored_columns = concat(originalIgnoredColumns, teColumnsToExclude);

      // Temporary set encoded frame as training set and afterwards we can set original back
      modelBuilder.setTrain(trainEncoded);
      printOutFrameAsTable( modelBuilder.train(), false, 100);
      
      try {
        Keyed model = modelBuilder.trainModel().get();
        retrievedModel = DKV.getGet(model._key);
        score += retrievedModel.auc();
      } catch (H2OIllegalArgumentException exception) {
        System.out.println(exception.getMessage());
      }
    } catch(Exception ex ) {
      System.out.println(ex.getMessage());
    } finally {
      if(foldColumnForTE!=null && originalTrainingData != null) {
        Vec removed = originalTrainingData.remove(foldColumnForTE);
        removed.remove();
      }
      //Setting back original frame
      modelBuilder.setTrain(originalTrainingData);
      modelBuilder._parms._ignored_columns = originalIgnoredColumns;
      if(retrievedModel!=null) retrievedModel.delete();
      if(trainEncoded != null) trainEncoded.delete();
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
  
  @Deprecated
  public double evaluate(TargetEncodingParams teParams, Algo[] evaluationAlgos, Frame inputData, String responseColumn, String foldColumnForTE, String[] columnsToEncode) {
    double score = 0;
    Map<String, Frame> encodingMap = null;
    Frame trainEncoded = null;
    try {
      // We need to apply TE taking into account the way how we train and validate our models.
      // with nfolds model will assign fold column to the date but we need those folds in TE before that. So we need to generate and then provide to the mode.
      // but what if we already have folds from the model search level? Is it better to use different fold assignments? Maybe yes - it is similar to "n-times m-folds cross-validation" idea.

      String[] teColumnsWithFoldToExclude = concat(columnsToEncode, new String[]{foldColumnForTE});

      // We might want to fine tune selection of the te column in Grid search as well ( even after TEApplicationStrategy)
      TargetEncoder tec = new TargetEncoder(columnsToEncode, teParams.getBlendingParams());
      encodingMap = tec.prepareEncodingMap(inputData, responseColumn, foldColumnForTE, true);
      trainEncoded = tec.applyTargetEncoding(inputData, responseColumn, encodingMap, teParams.getHoldoutType(), foldColumnForTE, teParams.isWithBlendedAvg(), teParams.getNoiseLevel(), true, 1234);

      for (Algo algo : evaluationAlgos) {
        if (algo == Algo.GBM) {
          score += GridSearchTEEvaluator.evaluateWithGBM(trainEncoded, responseColumn, teColumnsWithFoldToExclude);
        }
        if (algo == Algo.GLM) {
          score += GridSearchTEEvaluator.evaluateWithGLM(trainEncoded, responseColumn, teColumnsWithFoldToExclude);
        }
      }
    } finally {
      if(trainEncoded != null) trainEncoded.delete();
      TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
    }
    return score;
  }

  static double evaluateWithGBM(Frame inputData, String responseColumn, String[] columnsToExclude)  {
    GBMModel gbm = null; // TODO maybe we should receive GBMParameters from caller?

    double score = 0;
    try {
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = inputData._key;
      parms._response_column = responseColumn;
      parms._score_tree_interval = 10;
      parms._ntrees = 100;
//      parms._fold_column = foldColumnName; //TODO we will train on differend folds that will be assigned by the models themselves. Fold column that was used for TE should be excluded.
      parms._nfolds = 5;
      parms._max_depth = 5;
      parms._distribution = DistributionFamily.multinomial;
      parms._stopping_tolerance = 0.001;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 5;
      parms._ignored_columns = columnsToExclude;
      parms._keep_cross_validation_fold_assignment = false;
      parms._keep_cross_validation_models = false;
      parms._seed = 1234L;
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      score = gbm.auc();
//      System.out.println(gbm._output._variable_importances.toString(2, true));
    } finally {
      if(gbm != null) gbm.delete();
    }
      return score;
  }

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
