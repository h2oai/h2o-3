package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.Model;
import water.Key;
import water.fvec.Frame;
import hex.encoding.CategoricalEncoder;
import hex.encoding.CategoricalEncoderProvider;
import hex.encoding.CategoricalEncoding;
import hex.encoding.CategoricalEncodingSupport;
import water.fvec.Vec;

import java.util.*;
import java.util.stream.Collectors;

public class TargetEncoderCategoricalEncoderProvider implements CategoricalEncoderProvider {
  @Override
  public String getScheme() {
    return CategoricalEncoding.Scheme.TargetEncoding.name();
  }

  @Override
  public CategoricalEncoder getEncoder(CategoricalEncodingSupport params) {
    CategoricalEncoder encoder = params.getCategoricalEncoder();
    if (encoder != null) return encoder;
    
    assert params instanceof Model.Parameters: "TargetEncoder can be instantiated as a CategoricalEncoder only at model initialization with a complete Model.Parameters instance";
    Model.Parameters modelParams = (Model.Parameters)params;
    encoder = new TargetEncoderAsCategoricalEncoder(modelParams);
    // store the instance in params as we don't want to have to retrain TE each time we need to encode a frame.
    modelParams._categoricalEncoder = encoder;
    return encoder;
  }
  
  static class TargetEncoderAsCategoricalEncoder implements CategoricalEncoder {
    
    private Key<TargetEncoderModel> _teKey;

    public TargetEncoderAsCategoricalEncoder(Model.Parameters params) {
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
      
      TargetEncoder te = new TargetEncoder(teParams);
      _teKey = te.trainModel().get()._key;
    }

    @Override
    public Frame encode(Frame fr, String[] skipCols) {
      TargetEncoderModel teModel = getTargetEncoderModel();
      assert teModel != null;
      Set<String> encodedByDefault = Arrays.stream(teModel._output._parms._columns_to_encode)
              .flatMap(Arrays::stream)
              .collect(Collectors.toSet());
      String[] toSkip = Arrays.stream(skipCols)
              .filter(encodedByDefault::contains)
              .toArray(String[]::new);
      Map<String, Vec> removed = new LinkedHashMap<>();
      Frame toTransform = new Frame(fr);
      for (String col : toSkip) {
        Vec rem = toTransform.remove(col);
        if (rem != null) removed.put(col, rem);
      }
      Frame encoded = teModel.transform(toTransform);
      encoded.add(removed.keySet().toArray(new String[0]), 
                  removed.values().toArray(new Vec[0]));
      return encoded;
    }
    
    private TargetEncoderModel getTargetEncoderModel() {
      return _teKey == null ? null : _teKey.get();
    }
  }
}

