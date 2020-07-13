package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoder.DataLeakageHandlingStrategy;
import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import water.Futures;
import water.H2O;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.udf.CFuncRef;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.TwoDimTable;

import java.util.Map;

public class TargetEncoderModel extends Model<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters, TargetEncoderModel.TargetEncoderOutput> {

  protected static final String ALGO_NAME = "TargetEncoder";

  private final TargetEncoder _targetEncoder;

  public TargetEncoderModel(Key<TargetEncoderModel> selfKey, TargetEncoderParameters parms, TargetEncoderOutput output, TargetEncoder tec) {
    super(selfKey, parms, output);
    _targetEncoder = tec;
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No Model Metrics for TargetEncoder.");
  }

  public static class TargetEncoderParameters extends Model.Parameters {
    public boolean _blending = false;
    public double _k = TargetEncoder.DEFAULT_BLENDING_PARAMS.getK();
    public double _f = TargetEncoder.DEFAULT_BLENDING_PARAMS.getF();
    public DataLeakageHandlingStrategy _data_leakage_handling = DataLeakageHandlingStrategy.None;
    public double _noise_level = 0.01;
    
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
      return 1;
    }

    public BlendingParams getBlendingParameters() {
      return _k!=0 && _f!=0 ? new BlendingParams(_k, _f) : TargetEncoder.DEFAULT_BLENDING_PARAMS;
    }
  }

  public static class TargetEncoderOutput extends Model.Output {
    
    public IcedHashMap<String, Frame> _target_encoding_map;
    public TargetEncoderParameters _parms;
    public IcedHashMap<String, Boolean> _te_column_to_hasNAs;
    public double _prior_mean;
    
    public TargetEncoderOutput(TargetEncoderBuilder b, IcedHashMap<String, Frame> teMap, double priorMean) {
      super(b);
      _target_encoding_map = teMap;
      _parms = b._parms;
      _model_summary = constructSummary();

      _te_column_to_hasNAs = buildCol2HasNAsMap();
      _prior_mean = priorMean;
    }

    private IcedHashMap<String, Boolean> buildCol2HasNAsMap() {
      final IcedHashMap<String, Boolean> col2HasNAs = new IcedHashMap<>();
      for (Map.Entry<String, Frame> entry : _target_encoding_map.entrySet()) {
        String teColumn = entry.getKey();
        Frame encodingsFrame = entry.getValue();
        boolean hasNAs = _parms.train().vec(teColumn).cardinality() < encodingsFrame.vec(teColumn).cardinality();
        col2HasNAs.put(teColumn, hasNAs);
      }
      return col2HasNAs;
    }
    
    private TwoDimTable constructSummary(){

      String[] columnsForSummary = ArrayUtils.difference(_names, new String[]{responseName(), foldName()});
      TwoDimTable summary = new TwoDimTable(
              "Target Encoder model summary.",
              "Summary for target encoder model",
              new String[columnsForSummary.length],
              new String[]{"Original name", "Encoded column name"},
              new String[]{"string", "string"},
              null,
              null
      );

      for (int i = 0; i < columnsForSummary.length; i++) {
        final String originalColName = columnsForSummary[i];

        summary.set(i, 0, originalColName);
        summary.set(i, 1, originalColName + TargetEncoder.ENCODED_COLUMN_POSTFIX);
      }

      return summary;
    }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.TargetEncoder;
    }
  }

  /**
   * Transform with noise
   * @param data Data to transform
   * @param strategy The strategy specifying how to prevent data leakage {@link DataLeakageHandlingStrategy}
   * @param blendingParams Parameters for blending. If null, blending parameters from models parameters are loaded. 
   *                       If those are not set, DEFAULT_BLENDING_PARAMS from TargetEncoder class are used.
   * @param noiseLevel Level of noise applied (use -1 for default noise level, 0 to disable noise).
   * @param seed
   * @return An instance of {@link Frame} with transformed data, registered in DKV.
   */
  public Frame transform(Frame data, DataLeakageHandlingStrategy strategy, BlendingParams blendingParams, double noiseLevel, long seed) {
    return _targetEncoder.applyTargetEncoding(
            data, 
            _parms._response_column, 
            _output._target_encoding_map, 
            strategy,
            _parms._fold_column, 
            blendingParams, 
            noiseLevel,
            seed
    );
  }

  @Override
  protected double[] score0(double data[], double preds[]){
    throw new UnsupportedOperationException("TargetEncoderModel doesn't support scoring on raw data. Use transform() or score() instead.");
  }

  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
    final BlendingParams blendingParams = _parms._blending ? _parms.getBlendingParameters() : null;
    final DataLeakageHandlingStrategy leakageHandlingStrategy = 
            _parms._data_leakage_handling != null ? _parms._data_leakage_handling : DataLeakageHandlingStrategy.None;
    
    return _targetEncoder.applyTargetEncoding(
            fr, 
            _parms._response_column, 
            _output._target_encoding_map, 
            leakageHandlingStrategy,
            _parms._fold_column, 
            blendingParams,
            _parms._noise_level, 
            _parms._seed, 
            Key.<Frame>make(destination_key)
    );
  }
  

  @Override
  public TargetEncoderMojoWriter getMojo() {
    return new TargetEncoderMojoWriter(this);
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    TargetEncoderFrameHelper.encodingMapCleanUp(_output._target_encoding_map);
    return super.remove_impl(fs, cascade);
  }
}
