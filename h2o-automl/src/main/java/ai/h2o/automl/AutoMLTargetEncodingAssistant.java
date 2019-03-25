package ai.h2o.automl;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncodingParams;
import ai.h2o.automl.targetencoding.strategy.*;
import hex.ModelBuilder;
import hex.splitframe.ShuffleSplitFrame;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.Map;

import static ai.h2o.automl.targetencoding.TargetEncoder.DataLeakageHandlingStrategy.KFold;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.addKFoldColumn;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.concat;

/**
 * Dedicated to a single ModelBuilder, i.e. model with hyperParameters
 * Perform TargetEncoding based on a strategy of searching best TE parameters. 
 * Side effects will be done mostly through mutating modelBuilder.
 */
class AutoMLTargetEncodingAssistant{

  private Frame _trainingFrame;   
  private Frame _validationFrame; 
  private Frame _leaderboardFrame;
  private Vec _responseColumn;
  private Vec _foldColumn;
  private AutoMLBuildSpec _buildSpec;
  private ModelBuilder _modelBuilder;
  private String[] _originalIgnoredColumns;
  private boolean _CVEarlyStoppingEnabled;

  private TEParamsSelectionStrategy _teParamsSelectionStrategy;
  private TEApplicationStrategy _applicationStrategy;
  
  // This field will be initialised with the optimal target encoding params returned from TEParamsSelectionStrategy
  private TargetEncodingParams _teParams;

  AutoMLTargetEncodingAssistant(Frame trainingFrame, // maybe we don't need all these as we are working with particular modelBuilder and not the main AutoML data
                                Frame validationFrame,
                                Frame leaderboardFrame, 
                                Vec responseColumn,
                                Vec foldColumn,
                                AutoMLBuildSpec buildSpec,
                                ModelBuilder modelBuilder) {
    _modelBuilder = modelBuilder;
    _trainingFrame = modelBuilder.train();
    _validationFrame = validationFrame;
    _leaderboardFrame = leaderboardFrame;
    _responseColumn = responseColumn;
    _foldColumn = foldColumn;
    _buildSpec = buildSpec;

    _CVEarlyStoppingEnabled = _modelBuilder._parms.valid() == null;

    // Application strategy
    TEApplicationStrategy applicationStrategy = buildSpec.te_spec.application_strategy;
    _applicationStrategy = applicationStrategy != null ? applicationStrategy : new AllCategoricalTEApplicationStrategy(trainingFrame, responseColumn);

    // Selection strategy
    TEParamsSelectionStrategy teParamsSelectionStrategy = buildSpec.te_spec.params_selection_strategy;
    _teParamsSelectionStrategy = teParamsSelectionStrategy != null ? teParamsSelectionStrategy : new FixedTEParamsStrategy(TargetEncodingParams.DEFAULT);

    //TODO  It is better for user to specify strategy as just enum value, and we can initalize selection strategy here without presetups. 
    // But in that case we will not be able to provide `FixedTEParamsStrategy`
    
    // Pre-setup based on AutoML's ways of validating models 
    if(_teParamsSelectionStrategy instanceof GridBasedTEParamsSelectionStrategy ) {
      
      if(_CVEarlyStoppingEnabled) {
        ((GridBasedTEParamsSelectionStrategy) _teParamsSelectionStrategy).setTESearchSpace(ModelValidationMode.CV);
      }
      else {
        ((GridBasedTEParamsSelectionStrategy) _teParamsSelectionStrategy).setTESearchSpace(ModelValidationMode.VALIDATION_FRAME);
      }
    }
    
    _teParams = _teParamsSelectionStrategy.getBestParams(modelBuilder);
    
    _originalIgnoredColumns = modelBuilder._parms._ignored_columns;
  }


  void performAutoTargetEncoding() {

    Log.info("Best TE parameters were selected to be: holdout_type = " + _teParams.getHoldoutType() + ", isWithBlending = " + _teParams.isWithBlendedAvg() + ", smoothing = " + _teParams.getBlendingParams().getF() + ", inflection_point = " + _teParams.getBlendingParams().getK());

    String[] columnsToEncode = getApplicationStrategy().getColumnsToEncode();

    if (columnsToEncode.length > 0) {

      //TODO move it inside TargetEncoder. add constructor ? not all the parameters are used durin
      BlendingParams blendingParams = _teParams.getBlendingParams();
      boolean withBlendedAvg = _teParams.isWithBlendedAvg();
      boolean imputeNAsWithNewCategory = _teParams.isImputeNAsWithNewCategory();
      byte holdoutType = _teParams.getHoldoutType();
      double noiseLevel = _teParams.getNoiseLevel();
      long seed = _buildSpec.te_spec.seed;

      TargetEncoder tec = new TargetEncoder(columnsToEncode, blendingParams);

      String responseColumnName = _trainingFrame.name(_trainingFrame.find(_responseColumn));

      Map<String, Frame> encodingMap = null;

      Frame trainCopy = _trainingFrame.deepCopy(Key.make("train_frame_copy_for_encodings_generation_" + Key.make()).toString());
      DKV.put(trainCopy);

      if (_CVEarlyStoppingEnabled) {
        switch (holdoutType) {
          case TargetEncoder.DataLeakageHandlingStrategy.KFold:
            
            String foldColumnForTE = null;
            foldColumnForTE = _modelBuilder._job._key.toString() + "_te_fold_column";
            int nfolds = 5;
            addKFoldColumn(trainCopy, foldColumnForTE, nfolds, seed);

            encodingMap = tec.prepareEncodingMap(trainCopy, responseColumnName, foldColumnForTE, true);
            Frame.export(encodingMap.get("home.dest"), "assistant_cv_kfold.csv", encodingMap.get("home.dest")._key.toString(), true, 1).get();
            Frame encodedTrainingFrame  = tec.applyTargetEncoding(trainCopy, responseColumnName, encodingMap, KFold, foldColumnForTE, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, seed);

            Frame.export(encodedTrainingFrame, "assistant_encoded_train_cv_kfold.csv", encodedTrainingFrame._key.toString(), true, 1).get();

            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedTrainingFrame, _trainingFrame);

            break;
          case TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut:
          case TargetEncoder.DataLeakageHandlingStrategy.None:
        }
        trainCopy.delete();
        encodingMapCleanUp(encodingMap);
      } else {
        switch (holdoutType) {
          case TargetEncoder.DataLeakageHandlingStrategy.KFold:

            String foldColumnName = getFoldColumnName();
            String autoGeneratedFoldColumnForTE = "te_fold_column";

            // If our best TE params, returned from selection strategy, contains DataLeakageHandlingStrategy.KFold as holdoutType
            // then we need kfold column with preferably the same folds as during grid search. 
            // Case when original _trainingFrame does not have fold column. Even with CV enabled we at this point have not yet reached code of folds autoassignments.
            // Best we can do is to add fold column with the same seed as we use during Grid search of TE parameters. Otherwise just apply to the `_foldColumn` from the AutoML setup.
            if(foldColumnName == null) {
              foldColumnName = autoGeneratedFoldColumnForTE; // TODO introduce config `AutoMLTEControl` keep_te_fold_assignments
              int nfolds = 5; // TODO move to `AutoMLTEControl`
              addKFoldColumn(_trainingFrame, foldColumnName, nfolds, seed);
            }
            encodingMap = tec.prepareEncodingMap(_trainingFrame, responseColumnName, foldColumnName, imputeNAsWithNewCategory);

            // Case when we ignore validation frame and do early stopping based on CV models. Leaderboard is not used as well as we are using CV metrics to order model in the Leaderboard.
            if(_trainingFrame != null && _buildSpec.build_control.nfolds!=0) {
              // Ideally we would split training frame either train/test or train/valid/test. But we are not going to use these splits as we do CV.
              //This is bad as we will train CV models on data that contributed to the test splits of corresponding CV models.
            }

            Frame encodedTrainingFrame = tec.applyTargetEncoding(_trainingFrame, responseColumnName, encodingMap, holdoutType, foldColumnName, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedTrainingFrame, _trainingFrame);

            if(_validationFrame != null) {
              Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, imputeNAsWithNewCategory, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedValidationFrame, _validationFrame);
            }
            if(_leaderboardFrame != null) {
              Frame encodedLeaderboardFrame = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, foldColumnName, withBlendedAvg, 0, imputeNAsWithNewCategory, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedLeaderboardFrame, _leaderboardFrame);
            }
            if(foldColumnName.equals(autoGeneratedFoldColumnForTE)) {
              _trainingFrame.remove(autoGeneratedFoldColumnForTE).remove();
            }
            break;

          case TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut:
            encodingMap = tec.prepareEncodingMap(_trainingFrame, responseColumnName, null);

            Frame encodedTrainingFrameLOO = tec.applyTargetEncoding(_trainingFrame, responseColumnName, encodingMap, holdoutType, withBlendedAvg, noiseLevel, imputeNAsWithNewCategory,seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedTrainingFrameLOO, _trainingFrame);

            if(_validationFrame != null) {
              Frame encodedValidationFrame = tec.applyTargetEncoding(_validationFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0.0,  imputeNAsWithNewCategory, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedValidationFrame, _validationFrame);
            }
            if(_leaderboardFrame != null) {
              Frame encodedLeaderboardFrame = tec.applyTargetEncoding(_leaderboardFrame, responseColumnName, encodingMap, TargetEncoder.DataLeakageHandlingStrategy.None, withBlendedAvg, 0.0, imputeNAsWithNewCategory, seed);
              copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedLeaderboardFrame, _leaderboardFrame);
            }
            break;
          case TargetEncoder.DataLeakageHandlingStrategy.None:
            //We not only want to search for optimal parameters based on separate test split during grid search but also apply these parameters in the same fashion.
            //But seed is different in current case
            Frame[] trainAndHoldoutSplits = splitByRatio(_trainingFrame, new double[]{0.7, 0.3}, seed);
            Frame trainNone = trainAndHoldoutSplits[0];
            Frame holdoutNone = trainAndHoldoutSplits[1];
            encodingMap = tec.prepareEncodingMap(holdoutNone, responseColumnName, null);
            Frame encodedTrainingFrameNone = tec.applyTargetEncoding(trainNone, responseColumnName, encodingMap, holdoutType, withBlendedAvg, 0.0, imputeNAsWithNewCategory, seed);
            copyEncodedColumnsToDestinationFrameAndRemoveSource(columnsToEncode, encodedTrainingFrameNone, trainNone);

            _modelBuilder.setTrain(trainNone);

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

    setColumnsToIgnore(columnsToEncode);

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
    if(_teParams.getHoldoutType() == TargetEncoder.DataLeakageHandlingStrategy.KFold) {
      int foldColumnIndex = _trainingFrame.find(_foldColumn);
      return foldColumnIndex != -1 ? _trainingFrame.name(foldColumnIndex) : null;
    }
    else 
      return null;
  }

  public void setApplicationStrategy(TEApplicationStrategy applicationStrategy) {
    _applicationStrategy = applicationStrategy;
  }

}
