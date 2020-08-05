package ai.h2o.targetencoding;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.FillNAWithDoubleValueTask;
import water.logging.Logger;
import water.logging.LoggerFactory;
import water.udf.CFuncRef;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.TwoDimTable;

import java.util.Map;

import static ai.h2o.targetencoding.TargetEncoderHelper.*;

public class TargetEncoderModel extends Model<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters, TargetEncoderModel.TargetEncoderOutput> {

  public static final String ALGO_NAME = "TargetEncoder";
  private static final Logger logger = LoggerFactory.getLogger(TargetEncoderModel.class);

  public enum DataLeakageHandlingStrategy {
    LeaveOneOut,
    KFold,
    None,
  }

  public static class TargetEncoderParameters extends Model.Parameters {
    public boolean _blending = false;
    public double _inflection_point = TargetEncoderHelper.DEFAULT_BLENDING_PARAMS.getInflectionPoint();
    public double _smoothing = TargetEncoderHelper.DEFAULT_BLENDING_PARAMS.getSmoothing();
    public DataLeakageHandlingStrategy _data_leakage_handling = DataLeakageHandlingStrategy.None;
    public double _noise = 0.01;
    
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
      return _inflection_point!=0 && _smoothing!=0 ? new BlendingParams(_inflection_point, _smoothing) : TargetEncoderHelper.DEFAULT_BLENDING_PARAMS;
    }

    @Override
    protected boolean defaultDropConsCols() {
      return false;
    }
  }

  public static class TargetEncoderOutput extends Model.Output {
    
    public IcedHashMap<String, Frame> _target_encoding_map;
    public TargetEncoderParameters _parms;
    public IcedHashMap<String, Boolean> _te_column_to_hasNAs;
    public double _prior_mean;
    
    public TargetEncoderOutput(TargetEncoder b, IcedHashMap<String, Frame> teMap, double priorMean) {
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
        summary.set(i, 1, originalColName + ENCODED_COLUMN_POSTFIX);
      }

      return summary;
    }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.TargetEncoder;
    }
  }
  
  
  /******* Start TargetEncoderModel per se *******/


  public TargetEncoderModel(Key<TargetEncoderModel> selfKey, TargetEncoderParameters parms, TargetEncoderOutput output) {
    super(selfKey, parms, output);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    throw H2O.unimpl("No Model Metrics for TargetEncoder.");
  }

  /**
   * Transform with noise
   * @param fr Data to transform
   * @param blendingParams Parameters for blending. If null, blending parameters from models parameters are loaded. 
   *                       If those are not set, DEFAULT_BLENDING_PARAMS from TargetEncoder class are used.
   * @param noiseLevel Level of noise applied (use -1 for default noise level, 0 to disable noise).
   * @return An instance of {@link Frame} with transformed fr, registered in DKV.
   */
  public Frame transform(Frame fr, BlendingParams blendingParams, double noiseLevel) {
    //XXX: commented out logic for PUBDEV-7714: need to properly test separately
    Frame adaptFr = fr;
//    Frame adaptFr = new Frame(fr);
//    String[] msgs = adaptTestForTrain(adaptFr,true, false); //ensure that domains are compatible with training ones.
    // we only log the warnings messages here and ignore the warningP logic designed for scoring only 
//    for (String msg : msgs) {
//      logger.warn(msg);
//    }
    Frame transformed = applyTargetEncoding(
            adaptFr, 
            blendingParams, 
            noiseLevel,
            null
    );
//    Frame.deleteTempFrameAndItsNonSharedVecs(adaptFr, fr);
    return transformed;
  }

  @Override
  protected double[] score0(double data[], double preds[]){
    throw new UnsupportedOperationException("TargetEncoderModel doesn't support scoring on raw data. Use transform() or score() instead.");
  }

  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
    final BlendingParams blendingParams = _parms._blending ? _parms.getBlendingParameters() : null;
    return applyTargetEncoding(
            fr, 
            blendingParams,
            _parms._noise, 
            Key.make(destination_key)
    );
  }
  
  
  /**
   * Core method for applying pre-calculated encodings to the dataset. 
   *
   * @param data dataset that will be used as a base for creation of encodings .
   * @param blendingParams this provides parameters allowing to mitigate the effect 
   *                       caused by small observations of some categories when computing their encoded value.
   *                       Use null to disable blending.
   * @param noiseLevel amount of noise to add to the final encodings.
   *                   Use 0 to disable noise.
   *                   Use -1 to use the default noise level computed from the target.
   * @param resultKey key of the result frame
   * @return a new frame with the encoded columns.
   */
  Frame applyTargetEncoding(Frame data,
                            BlendingParams blendingParams,
                            double noiseLevel,
                            Key<Frame> resultKey) {
    
    final String targetColumn = _parms._response_column;
    final String foldColumn = _parms._fold_column;
    final DataLeakageHandlingStrategy dataLeakageHandlingStrategy = _parms._data_leakage_handling;
    final long seed = _parms._seed;

    // check requirements on frame
    switch (dataLeakageHandlingStrategy) {
      case KFold:
        if (data.find(foldColumn) < 0)
          throw new H2OIllegalArgumentException("KFold strategy requires a fold column `"+_parms._fold_column+"` like the one used during training.");
        break;
      case LeaveOneOut:
        if (data.find(targetColumn) < 0)
          // Note: for KFold strategy we don't need targetColumn so that we can exclude values from
          // current fold - as everything is already precomputed and stored in encoding map.
          // This is not the case with LeaveOneOut when we need to subtract current row's value and for that
          // we need to make sure that response column is provided and is a binary categorical column.
          throw new H2OIllegalArgumentException("LeaveOneOut strategy requires a response column `"+_parms._response_column+"` like the one used during training.");
        break;
    }

    if (noiseLevel < 0 ) {
      noiseLevel = defaultNoiseLevel(data, data.find(targetColumn));
      logger.warn("No noise level specified, using default noise level: "+noiseLevel);
    }

    Frame encodedFrame = null;
    try {
      if (resultKey == null){
        resultKey = Key.make();
      }

      encodedFrame = data.deepCopy(Key.make().toString()); // FIXME: why do we need a deep copy of the whole frame? TE is not supposed to modify existing columns 
        
      final Map<String, Frame> columnToEncodings = _output._target_encoding_map;
      for (String columnToEncode : columnToEncodings.keySet()) { // TODO: parallelize this, should mainly require change in naming of num/den columns
        int colIdx = encodedFrame.find(columnToEncode);
        imputeCategoricalColumn(encodedFrame, colIdx, columnToEncode + NA_POSTFIX);

        String encodedColumn = columnToEncode + ENCODED_COLUMN_POSTFIX;
        Frame encodings = columnToEncodings.get(columnToEncode);
        double priorMean = calculatePriorMean(encodings);
        int teColumnIdx = encodedFrame.find(columnToEncode);
        int encodingsTEColIdx = encodings.find(columnToEncode);

        switch (dataLeakageHandlingStrategy) {
          case KFold:
            try {
              Scope.enter();
//              checkFoldColumnInEncodings(foldColumn, encodings);
//              Frame holdoutEncodings = applyLeakageStrategyToEncodings(encodings, columnToEncode, dataLeakageHandlingStrategy, foldColumn);
//              Scope.track(holdoutEncodings);
              Frame holdoutEncodings = encodings;

              int foldColIdx = encodedFrame.find(foldColumn);
              int encodingsFoldColIdx = holdoutEncodings.find(foldColumn);
              long[] foldValues = getUniqueColumnValues(encodings, encodings.find(foldColumn));
              int maxFoldValue = (int) ArrayUtils.maxValue(foldValues);

              Frame joinedFrame = mergeEncodings(
                      encodedFrame, holdoutEncodings,
                      teColumnIdx, foldColIdx,
                      encodingsTEColIdx, encodingsFoldColIdx,
                      maxFoldValue
              );
              Scope.track(joinedFrame);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 

              //XXX: the priorMean here is computed on the entire training set, regardless of the folding structure, therefore it introduces a data leakage.
              // Shouldn't we instead provide a priorMean per fold? 
              // This could easily be computed from the encodings Frame, but applyEncodings would need to know the foldColumn as well.
              int encodedColIdx = applyEncodings(joinedFrame, encodedColumn, priorMean, blendingParams);
              applyNoise(joinedFrame, encodedColIdx, noiseLevel, seed);
              // Cases when we can introduce NA's:
              // 1) if category is present only in one fold then when applying the leakage strategy, this category will be missing in the result frame.
              //    When merging with the original dataset we will therefore get NA's for numerator and denominator, and then to encoded column.
              // Note: since we create encoding based on training dataset and use KFold mainly when we apply encoding to the training set,
              // there is zero probability that we haven't seen some category.
              imputeMissingValues(joinedFrame, encodedColIdx, priorMean); //XXX: same concern as above regarding priorMean.
              removeNumeratorAndDenominatorColumns(joinedFrame);
              encodedFrame = joinedFrame;
              Scope.untrack(encodedFrame);
            } finally {
              Scope.exit();
            }
            break;

          case LeaveOneOut:
            try {
              Scope.enter();
//              checkFoldColumnInEncodings(foldColumn, encodings);
//              Frame groupedEncodings = applyLeakageStrategyToEncodings(encodings, columnToEncode, dataLeakageHandlingStrategy, foldColumn);
//              Scope.track(groupedEncodings);
              Frame groupedEncodings = encodings;

              Frame joinedFrame = mergeEncodings(encodedFrame, groupedEncodings, teColumnIdx, encodingsTEColIdx);
              Scope.track(joinedFrame);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 

              subtractTargetValueForLOO(joinedFrame, targetColumn);
              
              int encodedColIdx = applyEncodings(joinedFrame, encodedColumn, priorMean, blendingParams);
              applyNoise(joinedFrame, encodedColIdx, noiseLevel, seed);
              // Cases when we can introduce NA's:
              // 1) Only when our encoding map has not seen some category.
              imputeMissingValues(joinedFrame, encodedColIdx, priorMean);
              removeNumeratorAndDenominatorColumns(joinedFrame);
              encodedFrame = joinedFrame;
              Scope.untrack(encodedFrame);
            } finally {
              Scope.exit();
            }
            break;

          case None:
            try {
              Scope.enter();
//              checkFoldColumnInEncodings(foldColumn, encodings);
//              Frame groupedEncodings = applyLeakageStrategyToEncodings(encodings, columnToEncode, dataLeakageHandlingStrategy, foldColumn);
//              Scope.track(groupedEncodings);
              Frame groupedEncodings = encodings;

              Frame joinedFrame = mergeEncodings(encodedFrame, groupedEncodings, teColumnIdx, encodingsTEColIdx);
              Scope.track(joinedFrame);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 

              int encodedColIdx = applyEncodings(joinedFrame, encodedColumn, priorMean, blendingParams);
              applyNoise(joinedFrame, encodedColIdx, noiseLevel, seed);
              // In cases when encoding has not seen some levels we will impute NAs with mean computed from training set. Mean is a data leakage btw.
              // Note: In case of creating encoding map based on the holdout set we'd better use stratified sampling.
              // Maybe even choose size of holdout taking into account size of the minimal set that represents all levels.
              // Otherwise there are higher chances to get NA's for unseen categories.
              double valueForImputation = valueForImputation(columnToEncode, groupedEncodings, priorMean, blendingParams);
              imputeMissingValues(joinedFrame, encodedColIdx, valueForImputation);
              removeNumeratorAndDenominatorColumns(joinedFrame);
              encodedFrame = joinedFrame;
              Scope.untrack(encodedFrame);
            } finally {
              Scope.exit();
            }
            break;
        } // end switch on strategy
      } // end for each columnToEncode

      encodedFrame._key = resultKey;
      DKV.put(resultKey, encodedFrame);
      return encodedFrame;
    } catch (Exception ex) {
      if (encodedFrame != null) encodedFrame.delete();
      throw ex;
    }
  }
  
  private double valueForImputation(String columnToEncode, Frame encodingsFrame,
                                    double priorMean, BlendingParams blendingParams) {
    int encodingsFrameRows = (int) encodingsFrame.numRows();
    String lastDomain = encodingsFrame.domains()[0][encodingsFrameRows - 1];
    boolean hadMissingValues = lastDomain.equals(columnToEncode + NA_POSTFIX);

    double numeratorForNALevel = encodingsFrame.vec(NUMERATOR_COL).at(encodingsFrameRows - 1);
    long denominatorForNALevel = encodingsFrame.vec(DENOMINATOR_COL).at8(encodingsFrameRows - 1);
    double posteriorForNALevel = numeratorForNALevel / denominatorForNALevel;
    long countNACategory = (long)numeratorForNALevel; //FIXME: this is true only for binary problems
    boolean useBlending = blendingParams != null;
    return !hadMissingValues ? priorMean
            : useBlending ? getBlendedValue(posteriorForNALevel, priorMean, countNACategory, blendingParams)
            : posteriorForNALevel;
  }

  /** FIXME: this method is modifying the original fr column in-place, one of the reasons why we currently need a complete deep-copy of the training frame... */
  private void imputeMissingValues(Frame fr, int columnIndex, double imputedValue) {
    Vec vec = fr.vec(columnIndex);
    assert vec.get_type() == Vec.T_NUM : "Imputation of missing value is supported only for numerical vectors.";
    long numberOfNAs = vec.naCnt();
    if (numberOfNAs > 0) {
      new FillNAWithDoubleValueTask(columnIndex, imputedValue).doAll(fr);
      logger.info(String.format("Frame with id = %s was imputed with posterior value = %f ( %d rows were affected)", fr._key, imputedValue, numberOfNAs));
    }
  }


  private double defaultNoiseLevel(Frame fr, int targetIndex) {
    double defaultNoiseLevel = 0.01;
    double noiseLevel = 0.0;
    // When noise is not provided and there is no response column in the `data` frame -> no noise will be added to transformations
    if (targetIndex >= 0) {
      Vec targetVec = fr.vec(targetIndex);
      noiseLevel = targetVec.isNumeric() ? defaultNoiseLevel * (targetVec.max() - targetVec.min()) : defaultNoiseLevel;
    }
    return noiseLevel;
  }
  
  private void applyNoise(Frame frame, int columnIdx, double noiseLevel, long seed) {
    if (noiseLevel > 0) addNoise(frame, columnIdx, noiseLevel, seed);
  }
  
  void removeNumeratorAndDenominatorColumns(Frame fr) {
    Vec removedNumeratorNone = fr.remove(NUMERATOR_COL);
    removedNumeratorNone.remove();
    Vec removedDenominatorNone = fr.remove(DENOMINATOR_COL);
    removedDenominatorNone.remove();
  }
  
  @Override
  public TargetEncoderMojoWriter getMojo() {
    return new TargetEncoderMojoWriter(this);
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    if (_output._target_encoding_map != null) {
      for (Frame encodings : _output._target_encoding_map.values()) {
        encodings.delete();
      }
    }
    return super.remove_impl(fs, cascade);
  }

}
