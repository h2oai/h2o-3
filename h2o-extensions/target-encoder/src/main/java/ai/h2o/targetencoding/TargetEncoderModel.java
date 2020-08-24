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
      return _blending 
              ? _inflection_point!=0 && _smoothing!=0 ? new BlendingParams(_inflection_point, _smoothing) : TargetEncoderHelper.DEFAULT_BLENDING_PARAMS
              : null;
    }

    @Override
    protected boolean defaultDropConsCols() {
      return false;
    }
  }

  public static class TargetEncoderOutput extends Model.Output {

    public final TargetEncoderParameters _parms;
    public final int _nclasses;
    public final IcedHashMap<String, Frame> _target_encoding_map;
    public final IcedHashMap<String, Boolean> _te_column_to_hasNAs;
    
    public TargetEncoderOutput(TargetEncoder b, IcedHashMap<String, Frame> teMap) {
      super(b);
      _parms = b._parms;
      _nclasses = b.nclasses();
      _target_encoding_map = teMap;
      _model_summary = constructSummary();

      _te_column_to_hasNAs = buildCol2HasNAsMap();
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

  public Frame transformTraining(Frame fr) {
    return transform(fr, _parms.getBlendingParameters(), _parms._noise, true);
  }
  
  public Frame transform(Frame fr) {
    return transform(fr, _parms.getBlendingParameters(), _parms._noise);
  }
  
  public Frame transform(Frame fr, BlendingParams blendingParams, double noiseLevel) {
    return transform(fr, blendingParams, noiseLevel, false);  
  }
  
  /**
   * Applies target encoding to unseen data during training.
   * This means that DataLeakageHandlingStrategy is enforced to None.
   * 
   *  In the context of Target Encoding, {@link #transform(Frame, BlendingParams, double, boolean)} should be used to encode new data.
   *  Whereas {@link #score(Frame)} should be mainly used to encode training data.
   * 
   * @param fr Data to transform
   * @param blendingParams Parameters for blending. If null, blending parameters from models parameters are loaded. 
   *                       If those are not set, DEFAULT_BLENDING_PARAMS from TargetEncoder class are used.
   * @param noiseLevel Level of noise applied (use -1 for default noise level, 0 to disable noise).
   * @param asTraining true iff transforming training data.
   * @return An instance of {@link Frame} with transformed fr, registered in DKV.
   */
  public Frame transform(Frame fr, BlendingParams blendingParams, double noiseLevel, boolean asTraining) {
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
            asTraining,
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

  /**
   * {@link #score(Frame)} always encodes as if the data were new (ie. not training data).
   */
  @Override
  public Frame score(Frame fr, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) throws IllegalArgumentException {
    return applyTargetEncoding(
            fr, 
            false,
            _parms.getBlendingParameters(),
            _parms._noise, 
            Key.make(destination_key)
    );
  }
  
  
  /**
   * Core method for applying pre-calculated encodings to the dataset. 
   *
   * @param data dataset that will be used as a base for creation of encodings .
   * @param asTraining is true, the original dataLeakageStrategy is applied, otherwise this is forced to {@link DataLeakageHandlingStrategy#None}.
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
                            boolean asTraining,
                            BlendingParams blendingParams,
                            double noiseLevel,
                            Key<Frame> resultKey) {
    
    final String targetColumn = _parms._response_column;
    final String foldColumn = _parms._fold_column;
    final DataLeakageHandlingStrategy dataLeakageHandlingStrategy = asTraining ? _parms._data_leakage_handling : DataLeakageHandlingStrategy.None;
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

      //FIXME: why do we need a deep copy of the whole frame? TE is not supposed to modify existing columns
      // a simple solution would be to duplicate only the columnToEncode in the loop below, 
      // this would allow us to modify it inplace using the current MRTasks and use it to generate the encoded column,
      // finally, we can drop this tmp column.
      encodedFrame = data.deepCopy(Key.make().toString());
        
      final Map<String, Frame> columnToEncodings = _output._target_encoding_map;
      for (String columnToEncode : columnToEncodings.keySet()) { // TODO: parallelize this, should mainly require change in naming of num/den columns
        int colIdx = encodedFrame.find(columnToEncode);
        imputeCategoricalColumn(encodedFrame, colIdx, columnToEncode + NA_POSTFIX);

        String encodedColumn = columnToEncode + ENCODED_COLUMN_POSTFIX;
        Frame encodings = columnToEncodings.get(columnToEncode);
        double priorMean = TargetEncoderHelper.computePriorMean(encodings);
        int teColumnIdx = encodedFrame.find(columnToEncode);
        int encodingsTEColIdx = encodings.find(columnToEncode);

        switch (dataLeakageHandlingStrategy) {
          case KFold:
            try {
              Scope.enter();
              int foldColIdx = encodedFrame.find(foldColumn);
              int encodingsFoldColIdx = encodings.find(foldColumn);
              long[] foldValues = getUniqueColumnValues(encodings, encodings.find(foldColumn));
              int maxFoldValue = (int) ArrayUtils.maxValue(foldValues);

              Frame joinedFrame = mergeEncodings(
                      encodedFrame, encodings,
                      teColumnIdx, foldColIdx,
                      encodingsTEColIdx, encodingsFoldColIdx,
                      maxFoldValue
              );
              Scope.track(joinedFrame);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 

              //XXX: the priorMean here is computed on the entire training set, regardless of the folding structure, therefore it introduces a data leakage.
              // Shouldn't we instead provide a priorMean per fold? 
              // We should be able to compute those k priorMeans directly from the encodings Frame, so it doesn't require any change in the Mojo.
              // However, applyEncodings would also need an additional arg for the foldColumn.
              int encodedColIdx = applyEncodings(joinedFrame, encodedColumn, priorMean, blendingParams);
              applyNoise(joinedFrame, encodedColIdx, noiseLevel, seed);
              // Cases when we can introduce NAs in the encoded column:
              // - if the column to encode contains categories unseen during training (including NA): 
              //   however this is very unlikely as KFold strategy is usually used when applying TE on the training frame.
              // - if during training, a category is present only in one fold, 
              //   then this couple (category, fold) will be missing in the encodings frame,
              //   and mergeEncodings will put NAs for both num and den, turning into a NA in the encoded column after applyEncodings.
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
              Frame joinedFrame = mergeEncodings(encodedFrame, encodings, teColumnIdx, encodingsTEColIdx);
              Scope.track(joinedFrame);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 

              subtractTargetValueForLOO(joinedFrame, targetColumn);
              
              int encodedColIdx = applyEncodings(joinedFrame, encodedColumn, priorMean, blendingParams);
              applyNoise(joinedFrame, encodedColIdx, noiseLevel, seed);
              // Cases when we can introduce NAs in the encoded column:
              // - only when the column to encode contains categories unseen during training (including NA).
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
              if (!asTraining && encodings.find(foldColumn) >= 0) { // applying TE trained with KFold strategy
                encodings = groupEncodingsByCategory(encodings, encodingsTEColIdx);
                Scope.track(encodings);
              }
              Frame joinedFrame = mergeEncodings(encodedFrame, encodings, teColumnIdx, encodingsTEColIdx);
              Scope.track(joinedFrame);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 

              int encodedColIdx = applyEncodings(joinedFrame, encodedColumn, priorMean, blendingParams);
              applyNoise(joinedFrame, encodedColIdx, noiseLevel, seed);
              // Cases when we can introduce NAs in the encoded column:
              // - only when the column to encode contains categories unseen during training (including NA).
              // We impute NAs with mean computed from training set, which is a data leakage.
              // Note: In case of creating encoding map based on the holdout set we'd better use stratified sampling.
              // Maybe even choose size of holdout taking into account size of the minimal set that represents all levels.
              // Otherwise there are higher chances to get NA's for unseen categories.
              double valueForImputation = valueForImputation(columnToEncode, encodings, priorMean, blendingParams);
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
  
  //XXX: usage of this method is confusing:
  // - if there was no NAs during training, we can only impute missing values on encoded column with priorMean (there's no posterior to blend with).
  // - if there was NAs during training, then encodings must contain entries for the NA category, so true NAs will be properly encoded at this point.
  //   Therefore, we impute only unseen categories, which we can decide to encode with priorMean (after all we don't have posterior for this new category),
  //   or as if these were true NAs: this is what the code below does, but it doesn't look fully justified, except maybe to reduce the leakage from priorMean.
  // If this is useful to reduce leakage, shouldn't we also use it in LOO + KFold strategies? 
  private double valueForImputation(String columnToEncode, Frame encodings,
                                    double priorMean, BlendingParams blendingParams) {
    assert encodings.name(0).equals(columnToEncode);
    int nRows = (int) encodings.numRows();
    String lastDomain = encodings.domains()[0][nRows - 1];
    boolean hadMissingValues = lastDomain.equals(columnToEncode + NA_POSTFIX);

    double numeratorNA = encodings.vec(NUMERATOR_COL).at(nRows - 1);
    long denominatorNA = encodings.vec(DENOMINATOR_COL).at8(nRows - 1);
    double posteriorNA = numeratorNA / denominatorNA;
    boolean useBlending = blendingParams != null;
    return !hadMissingValues ? priorMean  // no NA during training, so no posterior, the new (unseen) cat can only be encoded using priorMean.
            : useBlending ? getBlendedValue(posteriorNA, priorMean, denominatorNA, blendingParams)  // consider new (unseen) cat as a true NA + apply blending.
            : posteriorNA; // consider new (unseen) cat as a true NA + no blending.
  }

  /** FIXME: this method is modifying the original fr column in-place, one of the reasons why we currently need a complete deep-copy of the training frame... */
  private void imputeMissingValues(Frame fr, int columnIndex, double imputedValue) {
    Vec vec = fr.vec(columnIndex);
    assert vec.get_type() == Vec.T_NUM : "Imputation of missing value is supported only for numerical vectors.";
    if (vec.naCnt() > 0) {
      new FillNAWithDoubleValueTask(columnIndex, imputedValue).doAll(fr);
      if (logger.isInfoEnabled())
        logger.info(String.format("Frame with id = %s was imputed with posterior value = %f ( %d rows were affected)", fr._key, imputedValue, vec.naCnt()));
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
