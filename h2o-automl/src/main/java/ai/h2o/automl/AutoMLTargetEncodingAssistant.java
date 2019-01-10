package ai.h2o.automl;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.strategy.TEApplicationStrategy;
import ai.h2o.automl.targetencoding.strategy.TEParamsSelectionStrategy;
import water.Iced;
import water.fvec.Frame;
import water.fvec.Vec;

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
    _teParamsSelectionStrategy = teParamsSelectionStrategy;
    _applicationStrategy = applicationStrategy;
  }

  void performAutoTargetEncoding() {
    
    String[] columnsToEncode = getApplicationStrategy().getColumnsToEncode();
    
    if(columnsToEncode.length > 0) {

      TargetEncodingParams teParams = _teParamsSelectionStrategy.getBestParams();
      BlendingParams blendingParams = teParams.getBlendingParams();
      boolean withBlendedAvg = teParams.isWithBlendedAvg();
      boolean imputeNAsWithNewCategory = teParams.isImputeNAsWithNewCategory();
      long seed = _buildSpec.build_control.stopping_criteria.seed(); // TODO make it a dedicated parameter for users to set
      byte holdoutType = teParams.getHoldoutType();

      TargetEncoder tec = new TargetEncoder(columnsToEncode, blendingParams);

      String responseColumnName = _trainingFrame.name(_trainingFrame.find(_responseColumn));

      Map<String, Frame> encodingMap = null;
      
      switch (holdoutType) {
        case TargetEncoder.DataLeakageHandlingStrategy.KFold:

          encodingMap = tec.prepareEncodingMap(_trainingFrame, responseColumnName, getFoldColumnName(), imputeNAsWithNewCategory);

          Frame encodedTrainingFrame = tec.applyTargetEncoding(_trainingFrame, responseColumnName, encodingMap, holdoutType, getFoldColumnName(), withBlendedAvg, imputeNAsWithNewCategory, seed);
          copyEncodedColumnsToDestinationFrame(columnsToEncode, encodedTrainingFrame, _trainingFrame);

          if(_validationFrame != null) {
            Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, getFoldColumnName(), withBlendedAvg, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrame(columnsToEncode, encodedValidationFrame, _validationFrame);
          }
          if(_leaderboardFrame != null) {
            Frame encodedLeaderboardFrame = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, getFoldColumnName(), withBlendedAvg, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrame(columnsToEncode, encodedLeaderboardFrame, _leaderboardFrame);
          }
          break;
          
        case TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut:
          encodingMap = tec.prepareEncodingMap(_trainingFrame, responseColumnName, null);
          
          Frame encodedTrainingFrameLOO = tec.applyTargetEncoding(_trainingFrame, responseColumnName, encodingMap, holdoutType, withBlendedAvg,  imputeNAsWithNewCategory,seed);
          copyEncodedColumnsToDestinationFrame(columnsToEncode, encodedTrainingFrameLOO, _trainingFrame);

          if(_validationFrame != null) {
            Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrame(columnsToEncode, encodedValidationFrame, _validationFrame);
          }
          if(_leaderboardFrame != null) {
            Frame encodedLeaderboardFrame = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrame(columnsToEncode, encodedLeaderboardFrame, _leaderboardFrame);
          }
          break;
        case TargetEncoder.DataLeakageHandlingStrategy.None:
          encodingMap = tec.prepareEncodingMap(_trainingFrame, responseColumnName, null);

          Frame encodedTrainingFrameNone = tec.applyTargetEncoding(_trainingFrame, responseColumnName, encodingMap, holdoutType, withBlendedAvg,  imputeNAsWithNewCategory,seed);
          copyEncodedColumnsToDestinationFrame(columnsToEncode, encodedTrainingFrameNone, _trainingFrame);

          if(_validationFrame != null) {
            Frame encodedValidationFrameNone = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrame(columnsToEncode, encodedValidationFrameNone, _validationFrame);
          }
          if(_leaderboardFrame != null) {
            Frame encodedLeaderboardFrameNone = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrame(columnsToEncode, encodedLeaderboardFrameNone, _leaderboardFrame);
          }
      }
      encodingMapCleanUp(encodingMap);
    }
  }

  //Note: we could have avoided this if we were following mutable way in TargetEncoder
  private void copyEncodedColumnsToDestinationFrame(String[] columnsToEncode, Frame encodedFrame, Frame destinationFrame) {
    for(String column :columnsToEncode) {
      String encodedColumnName = column + "_te";
      Vec encodedVec = encodedFrame.vec(encodedColumnName);
      Vec encodedVecCopy = encodedVec.makeCopy();
      destinationFrame.add(encodedColumnName, encodedVecCopy);
      encodedVec.remove();
      encodedVecCopy.remove();
    }
    encodedFrame.delete();
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
    return _trainingFrame.name(_trainingFrame.find(_foldColumn));
  }

  public void setApplicationStrategy(TEApplicationStrategy applicationStrategy) {
    _applicationStrategy = applicationStrategy;
  }

}
