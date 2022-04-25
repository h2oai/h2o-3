package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.DataTransformSupport;
import hex.Model;
import water.DKV;
import water.Key;
import water.Keyed;
import water.fvec.Frame;
import hex.encoding.CategoricalEncoder;
import hex.encoding.CategoricalEncoderProvider;
import hex.encoding.CategoricalEncoding;
import hex.encoding.CategoricalEncodingSupport;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO: 
 * - document all new interfaces/public methods
 * - double check how CV transform is applied in TE, especially regarding how weights are used to apply folds: shouldn't TE support weight_column?
 * - try to apply default rules to decide if a column should be TE-encoded vs AUTO-encoded, e.g. column cardinality > X.
 * - if TE not encoding all categorical columns then find a way to ensure that AUTO is applied on others (using getDefaultCategoricalEncoding).
 */
public class TargetEncoderAsCategoricalEncoderProvider implements CategoricalEncoderProvider {
  @Override
  public String getScheme() {
    return CategoricalEncoding.Scheme.TargetEncoding.name();
  }

  @Override
  public CategoricalEncoder getEncoder(CategoricalEncodingSupport params) {
    Key<TargetEncoderModel> teKey = Key.make(params.getModelLifecycleId()+"_catencoder_TE");
    TargetEncoderModel teModel =  teKey.get();
    if (teModel != null) return new TargetEncoderAsCategoricalEncoder(teModel);
    
    assert params instanceof Model.Parameters: "TargetEncoder can be trained as a CategoricalEncoder only at model initialization with a complete Model.Parameters instance";
    Model.Parameters modelParams = (Model.Parameters)params;
    return new TargetEncoderAsCategoricalEncoder(modelParams, teKey);
  }
  
  static class TargetEncoderAsCategoricalEncoder implements CategoricalEncoder {
    
    private Key<TargetEncoderModel> _teKey;

    public TargetEncoderAsCategoricalEncoder(TargetEncoderModel teModel) {
      _teKey = teModel.getKey();
    }

    public TargetEncoderAsCategoricalEncoder(Model.Parameters params, Key<TargetEncoderModel> teKey) { 
      _teKey = teKey;
      DKV.put(buildModel(params));
    }
    
    private TargetEncoderModel buildModel(Model.Parameters params) {
      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train = params._train;
      teParams._response_column = params._response_column;
      teParams._fold_column = params._fold_column;
//      teParams._weights_column = params._weights_column;
//      teParams._offset_column = params._offset_column;
//      teParams._treatment_column = params._treatment_column;
      teParams._data_leakage_handling = teParams._fold_column != null
              ? DataLeakageHandlingStrategy.KFold
              : DataLeakageHandlingStrategy.None;
//      teParams._blending = true;
//      teParams._inflection_point = 5;
//      teParams._smoothing = 10;
      teParams._noise = 0;
      teParams._seed = params._seed;
      teParams._keep_original_categorical_columns = false;
      Set<String> nonPredictors = new HashSet<>(Arrays.asList(params.getNonPredictors()));
      Frame trainFr = teParams._train.get();
      List<String> toEncode = new ArrayList<>();
      for (int i=0; i < trainFr.numCols(); i++) {
        String col = trainFr.name(i);
        boolean isCategorical = trainFr.vec(i).isCategorical();
        if (isCategorical && !nonPredictors.contains(col)) {
          toEncode.add(col);
        }
      }
      teParams._columns_to_encode = toEncode.stream()
              .map(col -> new String[] {col})
              .toArray(String[][]::new);

      TargetEncoder te = new TargetEncoder(teParams, _teKey);
      return te.trainModel().get();
    }

    @Override
    public Frame encode(Frame fr, String[] skippedCols, Stage stage, DataTransformSupport params) {
      TargetEncoderModel teModel = getTargetEncoderModel();
      assert teModel != null;
      if (skippedCols != null && skippedCols.length > 0) {
        // technically we could remove the skippedCols and restore them on the transformed frame
        // but in all internal use-cases, skippedCols are non-predictor columns,
        // and they shouldn't have been included during the training of the TE model.
        Set<String> toEncode = Arrays.stream(teModel._output._parms._columns_to_encode)
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());
        assert Arrays.stream(skippedCols).noneMatch(toEncode::contains);
      }
      return teModel.transform(fr, stage, params);
    }
    
    private TargetEncoderModel getTargetEncoderModel() {
      return _teKey == null ? null : _teKey.get();
    }

    @Override
    public void remove() {
      Keyed.remove(_teKey);
    }
  }
}

