package ai.h2o.automl.targetencoding;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.ModelMojoWriter;
import water.*;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

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
    
    public Boolean _blending = false;
    public BlendingParams _blending_parameters;
    public Frame.VecSpecifier[] _encoded_columns;
    public String _data_leakage_handling;
    public Frame.VecSpecifier _target_column;
  }

  public static class TargetEncoderOutput extends Model.Output {
    
    public transient Map<String, Frame> _target_encoding_map;
    public TargetEncoderParameters _parms;
    public transient Map<String, Integer> column_name_to_idx;
    public transient Map<String, Integer> _column_name_to_missing_val_presence;
    public double _prior_mean;
    
    public TargetEncoderOutput(TargetEncoderBuilder b, Map<String, Frame> teMap, double priorMean) {
      super(b);
      _target_encoding_map = teMap;
      _parms = b._parms;
      _model_summary = constructSummary();

      column_name_to_idx = createColumnNameToIndexMap(_parms);
      _column_name_to_missing_val_presence = createMissingValuesPresenceMap();
      _prior_mean = priorMean;
    }

    private Map<String, Integer> createMissingValuesPresenceMap() {

      Map<String, Integer> presenceOfNAMap = new HashMap<>();
      for(Map.Entry<String, Frame> entry : _target_encoding_map.entrySet()) {
        String teColumn = entry.getKey();
        Frame frameWithEncodings = entry.getValue();
        presenceOfNAMap.put(teColumn, _parms.train().vec(teColumn).cardinality() < frameWithEncodings.vec(teColumn).cardinality() ? 1 : 0);
      }
      
      return presenceOfNAMap;
    }
    
    private TwoDimTable constructSummary(){
      TwoDimTable summary = new TwoDimTable("Target Encoder model summary.", "Summary for target encoder model", new String[_names.length],
              new String[]{"Original name", "Encoded column name"}, new String[]{"string", "string"}, null, null);

      for (int i = 0; i < _names.length; i++) {
        final String originalColName = _names[i];
        if(originalColName.equals(responseName())) continue;
        
        summary.set(i, 0, originalColName);
        summary.set(i, 1, originalColName + TargetEncoder.ENCODED_COLUMN_POSTFIX);
      }

      return summary;
    }
    
    private Map<String, Integer> createColumnNameToIndexMap(TargetEncoderParameters teParams) {
      Map<String, Integer> teColumnNameToIdx = new HashMap<>();
      String[] names = teParams.train().names().clone();
      String[] features = ArrayUtils.remove(names, teParams._response_column);
      for(Frame.VecSpecifier teColumn : teParams._encoded_columns) {
        teColumnNameToIdx.put(teColumn._column_name, ArrayUtils.find(features, teColumn)); 
      }
      return teColumnNameToIdx;
    }

    @Override
    public int nfeatures() {
      return super.nfeatures() - (_parms._fold_column == null ? 0 : 1);
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
            _parms._fold_column, _parms._blending, noiseLevel,false, seed);
  }

  /**
   * Transform with default noise of 0.01 
   */
  public Frame transform(Frame data, byte strategy, long seed){

    final TargetEncoder.DataLeakageHandlingStrategy leakageHandlingStrategy = TargetEncoder.DataLeakageHandlingStrategy.fromVal(strategy);
    return _targetEncoder.applyTargetEncoding(data, _parms._response_column, this._output._target_encoding_map, leakageHandlingStrategy,
            _parms._fold_column, _parms._blending,false, seed);
  }
  
  @Override
  protected double[] score0(double data[], double preds[]){
    throw new UnsupportedOperationException("TargetEncoderModel doesn't support scoring. Use `transform()` instead.");
  }

  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
    final TargetEncoder.DataLeakageHandlingStrategy leakageHandlingStrategy = TargetEncoder.DataLeakageHandlingStrategy.valueOf(_parms._data_leakage_handling);
    return _targetEncoder.applyTargetEncoding(fr, _parms._response_column, this._output._target_encoding_map, leakageHandlingStrategy,
            _parms._fold_column, _parms._blending, _parms._seed,false, Key.<Frame>make(destination_key));
  }
  

  @Override
  public ModelMojoWriter getMojo() {
    return new TargetEncoderMojoWriter(this);
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    TargetEncoderFrameHelper.encodingMapCleanUp(_output._target_encoding_map);
    _output.column_name_to_idx.clear();
    return super.remove_impl(fs, cascade);
  }
}
