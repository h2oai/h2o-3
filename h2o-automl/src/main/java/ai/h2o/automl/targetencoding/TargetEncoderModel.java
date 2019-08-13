package ai.h2o.automl.targetencoding;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMojoWriter;
import water.*;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.util.ArrayUtils;

import java.util.HashMap;
import java.util.Map;

public class TargetEncoderModel extends Model<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters, TargetEncoderModel.TargetEncoderOutput> {

  protected static final String ALGO_NAME = "TargetEncoder";

  private final transient TargetEncoder _targetEncoder;

  public TargetEncoderModel(Key<TargetEncoderModel> selfKey, TargetEncoderParameters parms, TargetEncoderOutput output, TargetEncoder tec) {
    super(selfKey, parms, output);
    _targetEncoder = tec;
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No Model Metrics for TargetEncoder.");
  }

  public static class TargetEncoderParameters extends Model.Parameters {
    
    @Override
    public String algoName() {
      return ALGO_NAME;
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
    public String _leakageHandlingStrategy;
  }

  public static class TargetEncoderOutput extends Model.Output {
    
    public transient Map<String, Frame> _target_encoding_map;
    public TargetEncoderParameters _teParams;
    public transient Map<String, Integer> _teColumnNameToIdx;
    public transient Map<String, Integer> _teColumnNameToMissingValuesPresence;
    public double _priorMean;
    
    public TargetEncoderOutput(TargetEncoderBuilder b, Map<String, Frame> teMap, double priorMean) {
      super(b);
      _target_encoding_map = teMap;
      _teParams = b._parms;

      _teColumnNameToIdx = createColumnNameToIndexMap(_teParams);
      _teColumnNameToMissingValuesPresence = createMissingValuesPresenceMap();
      _priorMean = priorMean;
    }

    private Map<String, Integer> createMissingValuesPresenceMap() {

      Map<String, Integer> presenceOfNAMap = new HashMap<>();
      for(Map.Entry<String, Frame> entry : _target_encoding_map.entrySet()) {
        String teColumn = entry.getKey();
        Frame frameWithEncodings = entry.getValue();
        presenceOfNAMap.put(teColumn, _teParams.train().vec(teColumn).cardinality() < frameWithEncodings.vec(teColumn).cardinality() ? 1 : 0);
      }
      
      return presenceOfNAMap;
    }
    
    private Map<String, Integer> createColumnNameToIndexMap(TargetEncoderParameters teParams) {
      Map<String, Integer> teColumnNameToIdx = new HashMap<>();
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

  /**
   * Transform with noise 
   */
  public Frame transform(Frame data, byte strategy, double noiseLevel, long seed){
    final TargetEncoder.DataLeakageHandlingStrategy leakageHandlingStrategy = TargetEncoder.DataLeakageHandlingStrategy.fromVal(strategy);
    return _targetEncoder.applyTargetEncoding(data, _parms._response_column, this._output._target_encoding_map, leakageHandlingStrategy,
            _parms._teFoldColumnName, _parms._withBlending, noiseLevel,false, seed);
  }

  /**
   * Transform with default noise of 0.01 
   */
  public Frame transform(Frame data, byte strategy, long seed){

    final TargetEncoder.DataLeakageHandlingStrategy leakageHandlingStrategy = TargetEncoder.DataLeakageHandlingStrategy.fromVal(strategy);
    return _targetEncoder.applyTargetEncoding(data, _parms._response_column, this._output._target_encoding_map, leakageHandlingStrategy,
            _parms._teFoldColumnName, _parms._withBlending,false, seed);
  }
  
  @Override
  public Frame score(Frame fr) throws IllegalArgumentException {
    return super.score(fr);
  }

  @Override
  protected double[] score0(double data[], double preds[]){
    throw new UnsupportedOperationException("TargetEncoderModel doesn't support scoring. Use `transform()` instead.");
  }

  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
    final TargetEncoder.DataLeakageHandlingStrategy leakageHandlingStrategy = TargetEncoder.DataLeakageHandlingStrategy.valueOf(_parms._leakageHandlingStrategy);
    return _targetEncoder.applyTargetEncoding(fr, _parms._response_column, this._output._target_encoding_map, leakageHandlingStrategy,
            _parms._teFoldColumnName, _parms._withBlending, _parms._seed,false, Key.<Frame>make(destination_key));
  }
  

  @Override
  public ModelMojoWriter getMojo() {
    return new TargetEncoderMojoWriter(this);
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    TargetEncoderFrameHelper.encodingMapCleanUp(_output._target_encoding_map);
    _output._teColumnNameToIdx.clear();
    return super.remove_impl(fs, cascade);
  }

  @Override
  public Model.Type getModelType() {
    return Type.DATA_TRANSFORMING;
  }
}
