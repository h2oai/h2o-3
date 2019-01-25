package ai.h2o.automl;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.strategy.AllCategoricalTEApplicationStrategy;
import ai.h2o.automl.targetencoding.strategy.FixedTEParamsStrategy;
import ai.h2o.automl.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.automl.targetencoding.strategy.TEParamsSelectionStrategy;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.TwoDimTable;

import java.util.Map;

/**
 * Perform TargetEncoding for AutoML process based on a strategy of searching best parameters.
 */
class AutoMLTargetEncodingAssistant{

  private Frame _trainingFrame;   
  private Frame _validationFrame; 
  private Frame _leaderboardFrame;
  private Vec _responseColumn;
  private Vec _foldColumn;
  private AutoMLBuildSpec _buildSpec;

  private TEParamsSelectionStrategy _teParamsSelectionStrategy;
  private TEApplicationStrategy _applicationStrategy;
  
  // This field will be initialised with the optimal target encoding params returned from TEParamsSelectionStrategy
  private TargetEncodingParams _teParams;

  AutoMLTargetEncodingAssistant(Frame trainingFrame,
                                Frame validationFrame,
                                Frame leaderboardFrame, 
                                Vec responseColumn,
                                Vec foldColumn,
                                AutoMLBuildSpec buildSpec,
                                TEParamsSelectionStrategy teParamsSelectionStrategy,
                                TEApplicationStrategy applicationStrategy) {
    _trainingFrame = trainingFrame;
    _validationFrame = validationFrame;
    _leaderboardFrame = leaderboardFrame;
    _responseColumn = responseColumn;
    _foldColumn = foldColumn;
    _buildSpec = buildSpec;
    _teParamsSelectionStrategy = teParamsSelectionStrategy != null ? teParamsSelectionStrategy : new FixedTEParamsStrategy(TargetEncodingParams.DEFAULT);
    _teParams = _teParamsSelectionStrategy.getBestParams();
    _applicationStrategy = applicationStrategy != null ? applicationStrategy : new AllCategoricalTEApplicationStrategy(trainingFrame, responseColumn);
  }


  void performAutoTargetEncoding() {
    
    String[] columnsToEncode = getApplicationStrategy().getColumnsToEncode();
    
    if(columnsToEncode.length > 0) {

      BlendingParams blendingParams = _teParams.getBlendingParams();
      boolean withBlendedAvg = _teParams.isWithBlendedAvg();
      boolean imputeNAsWithNewCategory = _teParams.isImputeNAsWithNewCategory();
      byte holdoutType = _teParams.getHoldoutType();
      double noiseLevel = _teParams.getNoiseLevel();
      long seed = _buildSpec.te_spec.seed;

      TargetEncoder tec = new TargetEncoder(columnsToEncode, blendingParams);

      String responseColumnName = _trainingFrame.name(_trainingFrame.find(_responseColumn));

      Map<String, Frame> encodingMap = tec.prepareEncodingMap(_trainingFrame, responseColumnName, getFoldColumnName(), imputeNAsWithNewCategory);;
      
      switch (holdoutType) {
        case TargetEncoder.DataLeakageHandlingStrategy.KFold:
          Frame encodedTrainingFrame = tec.applyTargetEncoding(_trainingFrame, responseColumnName, encodingMap, holdoutType, getFoldColumnName(), withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, seed);
          copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedTrainingFrame, _trainingFrame);

          if(_validationFrame != null) {
            Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, getFoldColumnName(), withBlendedAvg, 0, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedValidationFrame, _validationFrame);
          }
          if(_leaderboardFrame != null) {
            Frame encodedLeaderboardFrame = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, getFoldColumnName(), withBlendedAvg, 0, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedLeaderboardFrame, _leaderboardFrame);
          }
          break;
          
        case TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut:
          Frame encodedTrainingFrameLOO = tec.applyTargetEncoding(_trainingFrame, responseColumnName, encodingMap, holdoutType, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory,seed);
          copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedTrainingFrameLOO, _trainingFrame);

          if(_validationFrame != null) {
            Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0,  imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedValidationFrame, _validationFrame);
          }
          if(_leaderboardFrame != null) {
            Frame encodedLeaderboardFrame = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedLeaderboardFrame, _leaderboardFrame);
          }
          break;
        case TargetEncoder.DataLeakageHandlingStrategy.None:
          Frame encodedTrainingFrameNone = tec.applyTargetEncoding(_trainingFrame, responseColumnName, encodingMap, holdoutType, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, seed);
          copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedTrainingFrameNone, _trainingFrame);

          if(_validationFrame != null) {
            Frame encodedValidationFrameNone = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedValidationFrameNone, _validationFrame);
          }
          if(_leaderboardFrame != null) {
            Frame encodedLeaderboardFrameNone = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedLeaderboardFrameNone, _leaderboardFrame);
          }
      }
      encodingMapCleanUp(encodingMap);
    }
  }

  //Note: we could have avoided this if we were following mutable way in TargetEncoder
  void copyEncodedColumnsToDestinationFrameAndRemoveSource(String[] columnsToEncode, Frame sourceWithEncodings, Frame destinationFrame) {
    for(String column :columnsToEncode) {
      String encodedColumnName = column + "_te";
      Vec encodedVec = sourceWithEncodings.vec(encodedColumnName);
      Vec encodedVecCopy = encodedVec.makeCopy();
      destinationFrame.add(encodedColumnName, encodedVecCopy);
      encodedVec.remove();
    }
    sourceWithEncodings.delete();
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    if(encodingMap != null) {
      for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
        map.getValue().delete();
      }
    }
  }

  public TEApplicationStrategy getApplicationStrategy() {
    return _applicationStrategy;
  }


  public String getFoldColumnName() {
    if(_teParams.getHoldoutType() == TargetEncoder.DataLeakageHandlingStrategy.KFold)
      return _trainingFrame.name(_trainingFrame.find(_foldColumn));
    else return null;
  }

  public void setApplicationStrategy(TEApplicationStrategy applicationStrategy) {
    _applicationStrategy = applicationStrategy;
  }

}
