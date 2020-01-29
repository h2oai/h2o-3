package ai.h2o.automl.targetencoder;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.strategy.*;
import ai.h2o.targetencoding.BlendingParams;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderFrameHelper;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.strategy.AllCategoricalTEApplicationStrategy;
import ai.h2o.targetencoding.strategy.TEApplicationStrategy;
import hex.Model;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.StringUtils;

import java.util.Map;
import java.util.Optional;

import static ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy.KFold;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.targetencoding.TargetEncoderFrameHelper.concat;

/**
 * Dedicated to a single ModelBuilder, i.e. model with hyperParameters
 * Perform TargetEncoding based on a strategy of searching best TE parameters.
 * Side effects will be done mostly through mutating modelBuilder.
 */
public class AutoMLTargetEncoderAssistant<MP extends Model.Parameters>{ // TODO generalize with  MP. Probably something like PreProcessingStepAssistant

  private Frame _trainingFrame;
  private Frame _validationFrame;
  private Frame _leaderboardFrame;
  private String _responseColumnName;
  private AutoMLBuildSpec _buildSpec;
  private ModelBuilder _modelBuilder;
  private ModelValidationMode _validationMode;

  public ModelParametersSelectionStrategy<TargetEncoderModel.TargetEncoderParameters> getTeParamsSelectionStrategy() {
    return _modelParametersSelectionStrategy;
  }

  private ModelParametersSelectionStrategy<TargetEncoderModel.TargetEncoderParameters> _modelParametersSelectionStrategy;

  private String[] _columnsToEncode;

  public AutoMLTargetEncoderAssistant(AutoML aml,
                                      AutoMLBuildSpec buildSpec,
                                      ModelBuilder modelBuilder) {
    _modelBuilder = modelBuilder;
    _buildSpec = buildSpec;

    _trainingFrame = modelBuilder._parms.train();
    _validationFrame = aml.getValidationFrame();
    _leaderboardFrame = aml.getLeaderboardFrame();
    _responseColumnName = _modelBuilder._parms._response_column;

    _validationMode = _modelBuilder._parms.valid() == null ? ModelValidationMode.CV : ModelValidationMode.VALIDATION_FRAME;
  }

  public Optional<TargetEncoderModel.TargetEncoderParameters> findBestTEParams() {
    return selectColumnsForEncoding(_buildSpec.te_spec.application_strategy)
            .map(columnsToEncode -> {
              _columnsToEncode = columnsToEncode;
              _modelParametersSelectionStrategy = new GridSearchModelParametersSelectionStrategy(_modelBuilder, _buildSpec.te_spec, _leaderboardFrame, columnsToEncode, _validationMode);

              TargetEncoderModel.TargetEncoderParameters bestTEParams = getTeParamsSelectionStrategy().getBestParams();
              Log.info("Best TE parameters for chosen columns " + StringUtils.join(",", columnsToEncode) + " were selected to be: " + bestTEParams);
              return bestTEParams;
            });
  }

  private Optional<String[]> selectColumnsForEncoding(TEApplicationStrategy applicationStrategy) {
    TEApplicationStrategy effectiveApplicationStrategy = applicationStrategy != null ? applicationStrategy : new AllCategoricalTEApplicationStrategy(_trainingFrame, new String[]{_responseColumnName});
    String[] columnsToEncode = effectiveApplicationStrategy.getColumnsToEncode();
    return columnsToEncode.length == 0 ? Optional.empty() : Optional.of(columnsToEncode);
  }

  public void applyTE(TargetEncoderModel.TargetEncoderParameters bestTEParams) {

    Scope.enter();
    try {
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

      if (_validationMode == ModelValidationMode.CV) {
        switch (holdoutType) {
          case KFold:

            String foldColumnForTE = null;
            foldColumnForTE = _modelBuilder._job._key.toString() + "_te_fold_column";
            int nfolds = 5;
            addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seed);

            encodingMap = tec.prepareEncodingMap(trainCopy, responseColumnName, foldColumnForTE, true);
            Frame encodedTrainingFrame = tec.applyTargetEncoding(trainCopy, responseColumnName, encodingMap, KFold, foldColumnForTE, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, blendingParams, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedTrainingFrame, _trainingFrame);

            TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
            break;
          case LeaveOneOut:
          case None:
            throw new IllegalStateException("With CV being enabled KFOLD strategy is only supported for now.");
        }
      } else {
        switch (holdoutType) {
          case KFold:

            // Normally when it is not a CV case we don't have `fold_column` parameter provided
            String foldColumnName = _modelBuilder._parms._fold_column;

            // 1) If our best TE params, returned from selection strategy, contains DataLeakageHandlingStrategy.KFold as holdoutType
            // then we need kfold column with preferably the same folds as during grid search.
            // 2) Case when original _trainingFrame does not have fold column. Even with CV enabled we at this point have not yet reached code of folds autoassignments.
            // Best we can do is to add fold column with the same seed as we use during Grid search of TE parameters. Otherwise just apply to the `_foldColumn` from the AutoML setup.
            if (foldColumnName == null) {
              foldColumnName = "te_fold_column"; // TODO consider introducing config `AutoMLTEControl` keep_te_fold_assignments
              int nfolds = 5; // TODO move to `AutoMLTEControl`
              addKFoldColumn(trainCopy, foldColumnName, nfolds, seed);
            }
            encodingMap = tec.prepareEncodingMap(trainCopy, responseColumnName, foldColumnName, imputeNAsWithNewCategory);

            Frame encodedTrainingFrame = tec.applyTargetEncoding(trainCopy, responseColumnName, encodingMap, holdoutType, foldColumnName, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, blendingParams, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedTrainingFrame, _trainingFrame);

            if (_validationFrame != null) {
              Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedValidationFrame, _validationFrame);
            }
            if (_leaderboardFrame != null) {
              Frame encodedLeaderboardFrame = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedLeaderboardFrame, _leaderboardFrame);
            }
            break;

          case LeaveOneOut:
            encodingMap = tec.prepareEncodingMap(trainCopy, responseColumnName, null);

            Frame encodedTrainingFrameLOO = tec.applyTargetEncoding(trainCopy, responseColumnName, encodingMap, holdoutType, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, blendingParams, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedTrainingFrameLOO, _trainingFrame);

            if (_validationFrame != null) {
              Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0.0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedValidationFrame, _validationFrame);
            }
            if (_leaderboardFrame != null) {
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

            if (_validationFrame != null) {
              Frame encodedValidationFrameNone = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedValidationFrameNone, _validationFrame);
            }
            if (_leaderboardFrame != null) {
              Frame encodedLeaderboardFrameNone = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0, imputeNAsWithNewCategory, blendingParams, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(_columnsToEncode, encodedLeaderboardFrameNone, _leaderboardFrame);
            }
            holdoutNone.delete();
        }
        TargetEncoderFrameHelper.encodingMapCleanUp(encodingMap);
      }
      trainCopy.delete();
      setColumnsToIgnore(_columnsToEncode);
    } finally {
      Scope.exit();
    }
  }

  private Frame[] splitByRatio(Frame fr,double[] ratios, long seed) {
    Key[] keys = new Key[]{Key.<Frame>make(), Key.<Frame>make()};
    return ShuffleSplitFrame.shuffleSplitFrame(fr, keys, ratios, seed);
  }

  // Due to TE we want to exclude original columns so that we can use only encoded ones.
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
    Scope.track(sourceWithEncodings);
  }
}