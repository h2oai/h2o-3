package ai.h2o.automl.targetencoding;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMojoWriter;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.IcedHashMapGeneric;

import java.util.HashMap;
import java.util.Map;

public class TargetEncoderModel extends Model<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters, TargetEncoderModel.TargetEncoderOutput> {
  
  public TargetEncoderModel(Key<TargetEncoderModel> selfKey, TargetEncoderParameters parms, TargetEncoderOutput output) {
    super(selfKey, parms, output);
  }
  
  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No Model Metrics for TargetEncoder.");
  }

  public static class TargetEncoderParameters extends Model.Parameters {
    
    @Override
    public String algoName() {
      return "TargetEncoder";
    }

    @Override
    public String fullName() {
      return "TargetEncoder";
    }

    @Override
    public String javaName() {
      return TargetEncoderModel.class.getName();
    }

    @Override
    public long progressUnits() {
      return 0;
    }
    
    public Boolean _withBlending = true;
    public BlendingParams _blendingParams = new BlendingParams(10, 20);
    public String[] _columnNamesToEncode;
    public String _teFoldColumnName;
  }

  public static class TargetEncoderOutput extends Model.Output {
    
    public IcedHashMapGeneric<String, Frame> _targetEncodingMap; // stores encoding map created and 'prepareEncodingMap'
    public TargetEncoderParameters _teParams;
    public IcedHashMapGeneric<String, Integer> _teColumnNameToIdx;
    
    public TargetEncoderOutput(TargetEncoderBuilder b) {
      super(b);
      _targetEncodingMap = convertMapIntoIcedMap(b._targetEncodingMap);
      _teParams = b._parms;

      _teColumnNameToIdx = createColumnNameToIndexMap( _teParams);
    }
    
    // TODO `TargetEncoder.prepareEncodingMap` can return appropriate(iced) structure from the beginning but it is better to do this in a separate PR
    public IcedHashMapGeneric<String, Frame> convertMapIntoIcedMap(Map<String, Frame> map) {
      IcedHashMapGeneric<String, Frame> icedMap = new IcedHashMapGeneric<>();
      icedMap.putAll(map);
      return icedMap;
    }
    
    private IcedHashMapGeneric<String, Integer> createColumnNameToIndexMap(TargetEncoderParameters teParams) {
      IcedHashMapGeneric<String, Integer> teColumnNameToIdx = new IcedHashMapGeneric<>();
      String[] names = teParams.train().names().clone();
      String[] features = ArrayUtils.remove(names, teParams._response_column);
      for(String teColumn : teParams._columnNamesToEncode) {
        teColumnNameToIdx.put(teColumn, ArrayUtils.find(features, teColumn)); 
      }
      return teColumnNameToIdx;
    }

    @Override
    public int nfeatures() {
      return super.nfeatures() - (_teParams._teFoldColumnName == null ? 0 : 1);
    }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.TargetEncoder;
    }
  }

  private TargetEncoder constructTargetEncoderFromTargetEncoderParameters() {
    return new TargetEncoder(_parms._columnNamesToEncode, _parms._blendingParams);
  }
  /**
   * Transform with noise */
  public Frame transform(Frame data, byte strategy, double noiseLevel, long seed){
    return constructTargetEncoderFromTargetEncoderParameters()
            .applyTargetEncoding(data, _parms._response_column, this._output._targetEncodingMap, strategy,
            _parms._teFoldColumnName, _parms._withBlending, noiseLevel, true, seed);
  }

  /**
   * Transform with default noise of 0.01 */
  public Frame transform(Frame data, byte strategy, long seed){
    return constructTargetEncoderFromTargetEncoderParameters().applyTargetEncoding(data, _parms._response_column, this._output._targetEncodingMap, strategy,
            _parms._teFoldColumnName, _parms._withBlending, true, seed);
  }
  
  @Override
  protected double[] score0(double data[], double preds[]){
    throw new UnsupportedOperationException("TargetEncoderModel doesn't support scoring. Use `transform()` instead.");
  }

  @Override
  public ModelMojoWriter getMojo() {
//    return new TargetEncoderMojoWriter(this);
    throw H2O.unimpl("Will be enabled after PR #3282");
  }
  
}
