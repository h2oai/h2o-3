package ai.h2o.automl.targetencoder;

import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.strategy.*;
import ai.h2o.targetencoding.BlendingParams;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.strategy.AllCategoricalTEApplicationStrategy;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.StringUtils;
import water.util.TwoDimTable;

import java.util.HashMap;
import java.util.Map;

import static ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy.KFold;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.concat;

/**
 * Dedicated to a single ModelBuilder, i.e. model with hyperParameters
 * Perform TargetEncoding based on a strategy of searching best TE parameters.
 * Side effects will be done mostly through mutating modelBuilder.
 */
public class AutoMLTargetEncoderAssistant{

  private Frame _trainingFrame;
  private Frame _validationFrame;
  private Frame _leaderboardFrame;
  private String _responseColumnName;
  private AutoMLBuildSpec _buildSpec;
  private ModelBuilder _modelBuilder;
  private boolean _CVEarlyStoppingEnabled;

  public TEParamsSelectionStrategy<TargetEncoderModel.TargetEncoderParameters> getTeParamsSelectionStrategy() {
    return _teParamsSelectionStrategy;
  }

  private TEParamsSelectionStrategy _teParamsSelectionStrategy;

  private TEApplicationStrategy _applicationStrategy;
  private String[] _columnsToEncode;

  private TargetEncoderModel.TargetEncoderParameters _teParams;

  public AutoMLTargetEncoderAssistant(Frame trainingFrame, // maybe we don't need all these as we are working with particular modelBuilder and not the main AutoML data
                                       Frame validationFrame,
                                       Frame leaderboardFrame,
                                       AutoMLBuildSpec buildSpec,
                                       ModelBuilder modelBuilder) {
    _modelBuilder = modelBuilder;
    _trainingFrame = modelBuilder._parms.train();
    _validationFrame = validationFrame;
    _leaderboardFrame = leaderboardFrame;
    _responseColumnName = _modelBuilder._parms._response_column;

    _buildSpec = buildSpec;

    _CVEarlyStoppingEnabled = _modelBuilder._parms.valid() == null;

  }

  public TargetEncoderModel.TargetEncoderParameters findBestTEParams() throws NoColumnsToEncodeException {
    TEApplicationStrategy applicationStrategy = _buildSpec.te_spec.application_strategy;

    _columnsToEncode = selectColumnsForEncoding(applicationStrategy);

    //TODO what is the canonical way to get metric we are going to use. DistributionFamily, leaderboard metrics?
    boolean theBiggerTheBetter = _modelBuilder._parms.train().vec(_responseColumnName).get_type() != Vec.T_NUM;

    // Selection strategy
    HPsSelectionStrategy selectionStrategyFromTEScpec = _buildSpec.te_spec.params_selection_strategy;
    HPsSelectionStrategy teParamsSelectionStrategy = selectionStrategyFromTEScpec != null ? selectionStrategyFromTEScpec : HPsSelectionStrategy.RGS;
    switch(teParamsSelectionStrategy) {
      case Fixed:
        assert _buildSpec.te_spec.fixedTEParams != null : "When `HPsSelectionStrategy.Fixed` selection strategy is chosen it is required to provide `buildSpec.te_spec.fixedTEParams`";
        _teParamsSelectionStrategy = new FixedTEParamsStrategy(_buildSpec.te_spec.fixedTEParams);
        break;
      case RGS:
      default:
        //After filtering out some categorical columns with `applicationStrategy` we can try to search for optimal combinationof the rest as well.
        // This covers the case with no columns to encode, i.e. no target encoding
        Map<String, Double> _columnNameToIdxMap = new HashMap<>();//leaderboard.find(_columnsToEncode);
        for (String column : _columnsToEncode) {
          _columnNameToIdxMap.put(column, (double) _trainingFrame.find(column));
        }
        _teParamsSelectionStrategy = new GridSearchTEParamsSelectionStrategy(_leaderboardFrame, _responseColumnName, _columnsToEncode,
                _columnNameToIdxMap, theBiggerTheBetter, _buildSpec.te_spec);
        break;
    }

    // Pre-setup for grid-based strategies based on AutoML's ways of validating models
    if(getTeParamsSelectionStrategy() instanceof GridSearchTEParamsSelectionStrategy) {

      GridSearchTEParamsSelectionStrategy selectionStrategy = (GridSearchTEParamsSelectionStrategy) getTeParamsSelectionStrategy();
      if(_CVEarlyStoppingEnabled) {
        selectionStrategy.setTESearchSpace(ModelValidationMode.CV);
      }
      else {
        selectionStrategy.setTESearchSpace(ModelValidationMode.VALIDATION_FRAME);
      }
    }

    TargetEncoderModel.TargetEncoderParameters bestTEParams = getTeParamsSelectionStrategy().getBestParams(_modelBuilder);
    Log.info("Best TE parameters were selected to be: columnsToEncode = [ " + StringUtils.join(",", _columnsToEncode ) +
            " ], te_params = " + bestTEParams);
    return bestTEParams;
  }

  private String[] selectColumnsForEncoding(TEApplicationStrategy applicationStrategy) throws NoColumnsToEncodeException{
    _applicationStrategy = applicationStrategy != null ? applicationStrategy : new AllCategoricalTEApplicationStrategy(_trainingFrame, new String[]{_responseColumnName});
    String[] columnsToEncode = _applicationStrategy.getColumnsToEncode();
    if(columnsToEncode.length == 0) throw new NoColumnsToEncodeException();
    return columnsToEncode;
  }

  public static class NoColumnsToEncodeException extends Exception { }


  public void applyTE(TargetEncoderModel.TargetEncoderParameters bestTEParams) {

    if (_columnsToEncode.length > 0) { // TODO we already checked it with NoColumnsToEncodeException?

      //TODO move it inside TargetEncoder. add constructor ?
      BlendingParams blendingParams = bestTEParams.getBlendingParameters();
      boolean withBlendedAvg = bestTEParams._blending;
      boolean imputeNAsWithNewCategory = true;
      TargetEncoder.DataLeakageHandlingStrategy holdoutType = bestTEParams._data_leakage_handling;
      double noiseLevel = bestTEParams._noise_level;
      long seed = _buildSpec.te_spec.seed;

      TargetEncoder tec = new TargetEncoder(_columnsToEncode);

      String responseColumnName = _responseColumnName;
      Map<String, Frame> encodingMap = null;

      Frame trainCopy = _trainingFrame.deepCopy(Key.make("train_frame_copy_for_encodings_generation_" + Key.make()).toString());
      DKV.put(trainCopy);

      if (_CVEarlyStoppingEnabled) {
        switch (holdoutType) {
          case KFold:

            String foldColumnForTE = null;
            foldColumnForTE = _modelBuilder._job._key.toString() + "_te_fold_column";
            int nfolds = 5;
            addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seed);

            encodingMap = tec.prepareEncodingMap(trainCopy, responseColumnName, foldColumnForTE, true);
            Frame encodedTrainingFrame  = tec.applyTargetEncoding(trainCopy, responseColumnName, encodingMap, KFold, foldColumnForTE, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, blendingParams, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedTrainingFrame, _trainingFrame);

            encodingMapCleanUp(encodingMap);
            break;
          case LeaveOneOut:
          case None:
            throw new IllegalStateException("With CV being enabled KFOLD strategy is only supported for now. But we probably should support other strategies as well");
        }
      } else {
        switch (holdoutType) {
          case KFold:

            String foldColumnName = getFoldColumnName();
            String autoGeneratedFoldColumnNameForTE = "te_fold_column";

            // 1) If our best TE params, returned from selection strategy, contains DataLeakageHandlingStrategy.KFold as holdoutType
            // then we need kfold column with preferably the same folds as during grid search.
            // 2) Case when original _trainingFrame does not have fold column. Even with CV enabled we at this point have not yet reached code of folds autoassignments.
            // Best we can do is to add fold column with the same seed as we use during Grid search of TE parameters. Otherwise just apply to the `_foldColumn` from the AutoML setup.
            if(foldColumnName == null) {
              foldColumnName = autoGeneratedFoldColumnNameForTE; // TODO consider introducing config `AutoMLTEControl` keep_te_fold_assignments
              int nfolds = 5; // TODO move to `AutoMLTEControl`
              addKFoldColumn(trainCopy, foldColumnName, nfolds, seed);
            }
            encodingMap = tec.prepareEncodingMap(trainCopy, responseColumnName, foldColumnName, imputeNAsWithNewCategory);

            Frame encodedTrainingFrame = tec.applyTargetEncoding(trainCopy, responseColumnName, encodingMap, holdoutType, foldColumnName, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, blendingParams, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedTrainingFrame, _trainingFrame);

            if(_validationFrame != null) {
              Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedValidationFrame, _validationFrame);
            }
            if(_leaderboardFrame != null) {
              Frame encodedLeaderboardFrame = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedLeaderboardFrame, _leaderboardFrame);
            }
            break;

          case LeaveOneOut:
            encodingMap = tec.prepareEncodingMap(trainCopy, responseColumnName, null);

            Frame encodedTrainingFrameLOO = tec.applyTargetEncoding(trainCopy, responseColumnName, encodingMap, holdoutType, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, blendingParams, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedTrainingFrameLOO, _trainingFrame);

            if(_validationFrame != null) {
              Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0.0,  imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedValidationFrame, _validationFrame);
            }
            if(_leaderboardFrame != null) {
              Frame encodedLeaderboardFrame = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0.0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedLeaderboardFrame, _leaderboardFrame);
            }
            break;
          case None:
            //We not only want to search for optimal parameters based on separate test split during grid search but also apply these parameters in the same fashion.
            //But seed is different in current case
            Frame[] trainAndHoldoutSplits = splitByRatio(trainCopy, new double[]{0.7, 0.3}, seed);
            Frame trainNone = trainAndHoldoutSplits[0];
            Frame holdoutNone = trainAndHoldoutSplits[1];
            encodingMap = tec.prepareEncodingMap(holdoutNone, responseColumnName, null);
            Frame encodedTrainingFrameNone = tec.applyTargetEncoding(trainNone, responseColumnName, encodingMap, holdoutType, withBlendedAvg, 0.0, imputeNAsWithNewCategory, blendingParams, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedTrainingFrameNone, trainNone);

            _modelBuilder.setTrain(trainNone);

            if(_validationFrame != null) {
              Frame encodedValidationFrameNone = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedValidationFrameNone, _validationFrame);
            }
            if(_leaderboardFrame != null) {
              Frame encodedLeaderboardFrameNone = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedLeaderboardFrameNone, _leaderboardFrame);
            }
            holdoutNone.delete();
        }
        encodingMapCleanUp(encodingMap);
      }
      trainCopy.delete();
    }

    setColumnsToIgnore(_columnsToEncode);

  }

  private Frame[] splitByRatio(Frame fr,double[] ratios, long seed) {
    Key[] keys = new Key[]{Key.<Frame>make(), Key.<Frame>make()};
    return ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, seed);
  }

  // Due to TE we want to exclude original column so that we can use only encoded ones.
  // We need to be careful and reset value in _buildSpec back as next modelBuilder in AutoML sequence will exclude this
  // columns even before we will have a chance to apply TE.
  void setColumnsToIgnore(String[] columnsToEncode) {
    _buildSpec.input_spec.ignored_columns = concat(_buildSpec.input_spec.ignored_columns, columnsToEncode);
  }

  //Note: we could have avoided this if we were following mutable way in TargetEncoder
  void copyEncodedColumnsToDestinationFrameAndRemoveSource(String[] columnsToEncode, Frame sourceWithEncodings, Frame destinationFrame) {
    for(String column :columnsToEncode) {
      String encodedColumnName = column + "_te";
      Vec encodedVec = sourceWithEncodings.vec(encodedColumnName);
      Vec encodedVecCopy = encodedVec.makeCopy();
      destinationFrame.add(encodedColumnName, encodedVecCopy);
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

  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }

  public TEApplicationStrategy getApplicationStrategy() {
    return _applicationStrategy;
  }


  public String getFoldColumnName() {
    if(_teParams._data_leakage_handling == KFold) {
      int foldColumnIndex = _trainingFrame.find(_modelBuilder._parms._fold_column);
      return foldColumnIndex != -1 ? _trainingFrame.name(foldColumnIndex) : null;
    }
    else
      return null;
  }

  public void setApplicationStrategy(TEApplicationStrategy applicationStrategy) {
    _applicationStrategy = applicationStrategy;
  }

}