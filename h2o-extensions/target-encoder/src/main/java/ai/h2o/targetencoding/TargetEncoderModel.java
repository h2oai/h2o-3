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
import water.util.StringUtils;
import water.util.TwoDimTable;

import java.util.*;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.IntStream;

import static ai.h2o.targetencoding.EncodingsComponents.NO_TARGET_CLASS;
import static ai.h2o.targetencoding.TargetEncoderHelper.*;

public class TargetEncoderModel extends Model<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters, TargetEncoderModel.TargetEncoderOutput> {

  public static final String ALGO_NAME = "TargetEncoder";
  public static final int NO_FOLD = -1;

  static final String TMP_COLUMN_POSTFIX = "_tmp";
  static final String ENCODED_COLUMN_POSTFIX = "_te";
  static final BlendingParams DEFAULT_BLENDING_PARAMS = new BlendingParams(10, 20);
  
  private static final Logger logger = LoggerFactory.getLogger(TargetEncoderModel.class);

  public enum DataLeakageHandlingStrategy {
    LeaveOneOut,
    KFold,
    None,
  }

  public static class TargetEncoderParameters extends Model.Parameters {
    public boolean _blending = false;
    public double _inflection_point = DEFAULT_BLENDING_PARAMS.getInflectionPoint();
    public double _smoothing = DEFAULT_BLENDING_PARAMS.getSmoothing();
    public DataLeakageHandlingStrategy _data_leakage_handling = DataLeakageHandlingStrategy.None;
    public double _noise = 0.01;
    public boolean _keep_original_categorical_columns = true; // not a good default, but backwards compatible.
    
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
              ? _inflection_point!=0 && _smoothing!=0 ? new BlendingParams(_inflection_point, _smoothing) : DEFAULT_BLENDING_PARAMS
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
    public final IcedHashMap<String, Boolean> _te_column_to_hasNAs; //XXX: Map is a wrong choice for this, IcedHashSet would be perfect though
    
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
        boolean hasNAs = _parms.train().vec(teColumn).cardinality() < encodingsFrame.vec(teColumn).cardinality(); //XXX: _parms.train().vec(teColumn).naCnt() > 0 ?
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
    return transformTraining(fr, NO_FOLD);
  }
  
  public Frame transformTraining(Frame fr, int outOfFold) {
    assert outOfFold == NO_FOLD || _parms._data_leakage_handling == DataLeakageHandlingStrategy.KFold;
    return transform(fr, true, outOfFold, _parms.getBlendingParameters(), _parms._noise);
  }
  
  public Frame transform(Frame fr) {
    return transform(fr, _parms.getBlendingParameters(), _parms._noise);
  }
  
  public Frame transform(Frame fr, BlendingParams blendingParams, double noiseLevel) {
    return transform(fr, false, NO_FOLD, blendingParams, noiseLevel);  
  }
  
  /**
   * Applies target encoding to unseen data during training.
   * This means that DataLeakageHandlingStrategy is enforced to None.
   * 
   * In the context of Target Encoding, {@link #transform(Frame, BlendingParams, double)} should be used to encode new data.
   * Whereas {@link #transformTraining(Frame)} should be used to encode training data.
   * 
   * @param fr Data to transform
   * @param asTraining true iff transforming training data.
   * @param outOfFold if provided (if not = {@value NO_FOLD}), if asTraining=true, and if the model was trained with Kfold strategy,
   *                    then the frame will be encoded by aggregating encodings from all folds except this one.
   *                    This is mainly used during cross-validation.
   * @param blendingParams Parameters for blending. If null, blending parameters from models parameters are loaded. 
   *                       If those are not set, DEFAULT_BLENDING_PARAMS from TargetEncoder class are used.
   * @param noiseLevel Level of noise applied (use -1 for default noise level, 0 to disable noise).
   * @return An instance of {@link Frame} with transformed fr, registered in DKV.
   */
  public Frame transform(Frame fr, boolean asTraining, int outOfFold, BlendingParams blendingParams, double noiseLevel) {
    Frame adaptFr = null;
    try {
      adaptFr = adaptForEncoding(fr);
      return applyTargetEncoding(
              adaptFr,
              asTraining,
              outOfFold, 
              blendingParams,
              noiseLevel,
              null
      );
    } finally {
      if (adaptFr != null)
        Frame.deleteTempFrameAndItsNonSharedVecs(adaptFr, fr);
    }
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
    Frame adaptFr = null;
    try {
      adaptFr = adaptForEncoding(fr);
      return applyTargetEncoding(
              adaptFr, 
              false,
              NO_FOLD,
              _parms.getBlendingParameters(),
              _parms._noise, 
              Key.make(destination_key)
      );
    } finally {
      if (adaptFr != null)
        Frame.deleteTempFrameAndItsNonSharedVecs(adaptFr, fr);
    }
  }
  
  private Frame adaptForEncoding(Frame fr) {
    Frame adaptFr = new Frame(fr);
    for (int i=0; i<_output._names.length; i++) {
      String col = _output._names[i];
      String[] domain = _output._domains[i];
      if (domain != null && ArrayUtils.contains(adaptFr.names(), col)) {
        int toAdaptIdx = adaptFr.find(col);
        Vec toAdapt = adaptFr.vec(toAdaptIdx);
        if (!Arrays.equals(toAdapt.domain(), domain)) {
          Vec adapted = toAdapt.adaptTo(domain);
          adaptFr.replace(toAdaptIdx, adapted);
        }
      }
    }
    return adaptFr;
  }
  
  
  /**
   * Core method for applying pre-calculated encodings to the dataset. 
   *
   * @param data dataset that will be used as a base for creation of encodings .
   * @param asTraining is true, the original dataLeakageStrategy is applied, otherwise this is forced to {@link DataLeakageHandlingStrategy#None}.
   * @param blendingParams this provides parameters allowing to mitigate the effect 
   *                       caused by small observations of some categories when computing their encoded value.
   *                       Use null to disable blending.
   * @param noise amount of noise to add to the final encodings.
   *                   Use 0 to disable noise.
   *                   Use -1 to use the default noise level computed from the target.
   * @param resultKey key of the result frame
   * @return a new frame with the encoded columns.
   */
  Frame applyTargetEncoding(Frame data,
                            boolean asTraining,
                            int outOfFold, 
                            BlendingParams blendingParams,
                            double noise,
                            Key<Frame> resultKey) {
    final String targetColumn = _parms._response_column;
    final String foldColumn = _parms._fold_column;
    final DataLeakageHandlingStrategy dataLeakageHandlingStrategy = asTraining ? _parms._data_leakage_handling : DataLeakageHandlingStrategy.None;
    final long seed = _parms._seed;

    assert outOfFold == NO_FOLD || dataLeakageHandlingStrategy == DataLeakageHandlingStrategy.KFold;
    
    // early check on frame requirements
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

    // applying defaults
    if (noise < 0 ) {
      noise = defaultNoiseLevel(data, data.find(targetColumn));
      logger.warn("No noise level specified, using default noise level: "+noise);
    }
    if (resultKey == null){
      resultKey = Key.make();
    }

    EncodingStrategy strategy;
    switch (dataLeakageHandlingStrategy) {
      case KFold:
        strategy = new KFoldEncodingStrategy(foldColumn, outOfFold, blendingParams, noise, seed);
        break;
      case LeaveOneOut:
        strategy = new LeaveOneOutEncodingStrategy(targetColumn, blendingParams, noise, seed);
        break;
      case None:
      default:
        strategy = new DefaultEncodingStrategy(blendingParams, noise, seed);
        break;
    }

    Frame workingFrame = null;
    Key<Frame> tmpKey;
    try {
      //FIXME: why do we need a deep copy of the whole frame? TE is not supposed to modify existing columns
      // a simple solution would be to duplicate only the columnToEncode in the loop below, 
      // this would allow us to modify it inplace using the current MRTasks and use it to generate the encoded column,
      // finally, we can drop this tmp column.
      workingFrame = data.deepCopy(Key.make().toString());
      tmpKey = workingFrame._key;

      final Map<String, Frame> columnToEncodings = sortByColumnIndex(_output._target_encoding_map); //ensures that new columns are added in a predictable way.
      
      for (Map.Entry<String, Frame> kv: columnToEncodings.entrySet()) { // TODO: parallelize this, should mainly require change in naming of num/den columns
        String columnToEncode = kv.getKey();
        Frame encodings = kv.getValue();

        // if not applying encodings to training data, then get rid of the foldColumn in encodings.
        if (dataLeakageHandlingStrategy != DataLeakageHandlingStrategy.KFold && encodings.find(foldColumn) >= 0) {
          encodings = groupEncodingsByCategory(encodings, encodings.find(columnToEncode));
        }
        
        int colIdx = workingFrame.find(columnToEncode);
        imputeCategoricalColumn(workingFrame, colIdx, columnToEncode + NA_POSTFIX);

        IntStream posTargetClasses = _output.nclasses() == 1 ? IntStream.of(NO_TARGET_CLASS) // regression
                : _output.nclasses() == 2 ? IntStream.of(1)  // binary (use only positive target)
                : IntStream.range(1, _output._nclasses);     // multiclass (skip only the 0 target for symmetry with binary)

        for (OfInt it = posTargetClasses.iterator(); it.hasNext(); ) {
          int tc = it.next();
          try {
            workingFrame = strategy.apply(workingFrame, columnToEncode, encodings, tc);
          } finally {
            DKV.remove(tmpKey);
            tmpKey = workingFrame._key;
          }
        } // end for each target 
        
        if (!_parms._keep_original_categorical_columns)
          workingFrame.remove(colIdx);
      } // end for each columnToEncode

      DKV.remove(tmpKey);
      workingFrame._key = resultKey;
      reorderColumns(workingFrame);
      DKV.put(workingFrame);
      return workingFrame;
    } catch (Exception e) {
      if (workingFrame != null) workingFrame.delete();
      throw e;
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

  Map<String, Frame> sortByColumnIndex(final Map<String, Frame> encodingMap) {
    Map<String, Integer> nameToIdx = nameToIndex(_output._names);
    Map<String, Frame> sorted = new TreeMap<>(Comparator.comparingInt(nameToIdx::get));
    sorted.putAll(encodingMap);
    return sorted;
  }

  /**
   * For model integration, we need to ensure that columns are offered to the model in a consistent order.
   * After TE encoding, columns are always in the following order:
   * <ol>
   *     <li>non-categorical predictors</li>
   *     <li>TE-encoded predictors</li>
   *     <li>remaining categorical predictors</li>
   *     <li>non-predictors</li>
   * </ol>
   * This way, categorical encoder can later encode the remaining categorical predictors 
   * without changing the index of TE cols: somehow necessary when integrating TE in the model Mojo.
   * 
   * @param fr the frame whose columns need to be reordered.
   */
  private void reorderColumns(Frame fr) {
    String[] toTheEnd = _parms.getNonPredictors();
    Map<String, Integer> nameToIdx = nameToIndex(fr);
    List<Integer> toAppendAfterNumericals = new ArrayList<>();
    String[] columns = fr.names();
    int[] newOrder = new int[columns.length];
    int offset = 0;
    for (int i=0; i<columns.length; i++) {
      if (ArrayUtils.contains(toTheEnd, columns[i])) continue;
      Vec vec = fr.vec(i);
      if (vec.isCategorical()) {
        toAppendAfterNumericals.add(i); //first appending categoricals
      } else {
        newOrder[offset++] = i; //adding all non-categoricals first
      }
    }
    for (String col : toTheEnd) { // then appending the trailing columns
      if (nameToIdx.containsKey(col)) toAppendAfterNumericals.add(nameToIdx.get(col));
    }
    for (int idx : toAppendAfterNumericals) newOrder[offset++] = idx;
    fr.reOrder(newOrder);
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
  
  
  private static abstract class EncodingStrategy {

    BlendingParams _blendingParams;
    double _noise;
    long _seed;
    
    public EncodingStrategy(BlendingParams blendingParams, double noise, long seed) {
      _blendingParams = blendingParams;
      _noise = noise;
      _seed = seed;
    }
    
    public Frame apply(Frame fr, String columnToEncode, Frame encodings, int targetClass) {
      try {
        Scope.enter();
        String encodedColumn;
        Frame appliedEncodings;
        int tcIdx = encodings.find(TARGETCLASS_COL);
        if (tcIdx < 0) {
          encodedColumn = columnToEncode+ENCODED_COLUMN_POSTFIX;  //note: this naming convention is also used for TE integration with algos Mojos.
          appliedEncodings = encodings;
        } else {
          String targetClassName = encodings.vec(tcIdx).domain()[targetClass];
          encodedColumn = columnToEncode + "_" + StringUtils.sanitizeIdentifier(targetClassName) + ENCODED_COLUMN_POSTFIX;  //note: this naming convention is also used for TE integration with algos Mojos. 
          appliedEncodings = filterByValue(encodings, tcIdx, targetClass);
          Scope.track(appliedEncodings);
          appliedEncodings.remove(TARGETCLASS_COL);
        }
        Frame encoded = doApply(fr, columnToEncode, appliedEncodings, encodedColumn, targetClass);
        Scope.untrack(encoded);
        return encoded;
      } finally {
        Scope.exit();
      }
    }
    
    public abstract Frame doApply(Frame fr, String columnToEncode, Frame encodings, String encodedColumn, int targetClass);
    
    protected void applyNoise(Frame frame, int columnIdx, double noiseLevel, long seed) {
      if (noiseLevel > 0) addNoise(frame, columnIdx, noiseLevel, seed);
    }
    
    /** FIXME: this method is modifying the original fr column in-place, one of the reasons why we currently need a complete deep-copy of the training frame... */
    protected void imputeMissingValues(Frame fr, int columnIndex, double imputedValue) {
      Vec vec = fr.vec(columnIndex);
      assert vec.get_type() == Vec.T_NUM : "Imputation of missing value is supported only for numerical vectors.";
      if (vec.naCnt() > 0) {
        new FillNAWithDoubleValueTask(columnIndex, imputedValue).doAll(fr);
        if (logger.isInfoEnabled())
          logger.info(String.format("Frame with id = %s was imputed with posterior value = %f ( %d rows were affected)", fr._key, imputedValue, vec.naCnt()));
      }
    }
    
    //XXX: usage of this method is confusing:
    // - if there was no NAs during training, we can only impute missing values on encoded column with priorMean (there's no posterior to blend with).
    // - if there was NAs during training, then encodings must contain entries for the NA category, so true NAs will be properly encoded at this point.
    //   Therefore, we impute only unseen categories, which we can decide to encode with priorMean (after all we don't have posterior for this new category),
    //   or as if these were true NAs: this is what the code below does, but it doesn't look fully justified, except maybe to reduce the leakage from priorMean.
    // If this is useful to reduce leakage, shouldn't we also use it in LOO + KFold strategies? 
    protected double valueForImputation(String columnToEncode, Frame encodings,
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
    
    protected void removeNumeratorAndDenominatorColumns(Frame fr) {
      Vec removedNumeratorNone = fr.remove(NUMERATOR_COL);
      removedNumeratorNone.remove();
      Vec removedDenominatorNone = fr.remove(DENOMINATOR_COL);
      removedDenominatorNone.remove();
    }

  }

  private static class KFoldEncodingStrategy extends EncodingStrategy {

    String _foldColumn;
    int _outOfFold;
    
    public KFoldEncodingStrategy(String foldColumn, int outOfFold,
                                 BlendingParams blendingParams, double noise, long seed) {
      super(blendingParams, noise, seed);
      _foldColumn = foldColumn;
      _outOfFold = outOfFold;
    }

    @Override
    public Frame doApply(Frame fr, String columnToEncode, Frame encodings, String encodedColumn, int targetClass) {
      Frame workingFrame = fr;
      int teColumnIdx = fr.find(columnToEncode);
      int foldColIdx;
      if (_outOfFold== NO_FOLD) {
        foldColIdx = fr.find(_foldColumn);
      } else {
        workingFrame = new Frame(fr);
        Vec tmpFoldCol = workingFrame.anyVec().makeCon(_outOfFold);
        Scope.track(tmpFoldCol);
        workingFrame.add(new String[] {_foldColumn+TMP_COLUMN_POSTFIX}, new Vec[]{tmpFoldCol});
        foldColIdx = workingFrame.numCols()-1;
      }
      int encodingsFoldColIdx = encodings.find(_foldColumn);
      int encodingsTEColIdx = encodings.find(columnToEncode);
      long[] foldValues = getUniqueColumnValues(encodings, encodingsFoldColIdx);
      int maxFoldValue = (int) ArrayUtils.maxValue(foldValues);
      double priorMean = computePriorMean(encodings); //FIXME: we want prior for the outOfFold encodings 

      Frame joinedFrame = mergeEncodings(
              workingFrame, encodings,
              teColumnIdx, foldColIdx,
              encodingsTEColIdx, encodingsFoldColIdx,
              maxFoldValue
      );
      Scope.track(joinedFrame);
      if (_outOfFold!= NO_FOLD) {
        joinedFrame.remove(foldColIdx);
      }

      //XXX: the priorMean here is computed on the entire training set, regardless of the folding structure, therefore it introduces a data leakage.
      // Shouldn't we instead provide a priorMean per fold? 
      // We should be able to compute those k priorMeans directly from the encodings Frame, so it doesn't require any change in the Mojo.
      // However, applyEncodings would also need an additional arg for the foldColumn.
      int encodedColIdx = applyEncodings(joinedFrame, encodedColumn, priorMean, _blendingParams);
      applyNoise(joinedFrame, encodedColIdx, _noise, _seed);
      // Cases when we can introduce NAs in the encoded column:
      // - if the column to encode contains categories unseen during training (including NA): 
      //   however this is very unlikely as KFold strategy is usually used when applying TE on the training frame.
      // - if during training, a category is present only in one fold, 
      //   then this couple (category, fold) will be missing in the encodings frame,
      //   and mergeEncodings will put NAs for both num and den, turning into a NA in the encoded column after applyEncodings.
      imputeMissingValues(joinedFrame, encodedColIdx, priorMean); //XXX: same concern as above regarding priorMean.
      removeNumeratorAndDenominatorColumns(joinedFrame);
      return joinedFrame;
    }
  }

  private static class LeaveOneOutEncodingStrategy extends EncodingStrategy {

    String _targetColumn;
    
    public LeaveOneOutEncodingStrategy(String targetColumn,
                                       BlendingParams blendingParams, double noise, long seed) {
      super(blendingParams, noise, seed);
      _targetColumn = targetColumn;
    }

    @Override
    public Frame doApply(Frame fr, String columnToEncode, Frame encodings, String encodedColumn, int targetClass) {
      int teColumnIdx = fr.find(columnToEncode);
      int encodingsTEColIdx = encodings.find(columnToEncode);
      double priorMean = computePriorMean(encodings);
      
      Frame joinedFrame = mergeEncodings(fr, encodings, teColumnIdx, encodingsTEColIdx);
      Scope.track(joinedFrame);

      subtractTargetValueForLOO(joinedFrame, _targetColumn, targetClass);

      int encodedColIdx = applyEncodings(joinedFrame, encodedColumn, priorMean, _blendingParams);
      applyNoise(joinedFrame, encodedColIdx, _noise, _seed);
      // Cases when we can introduce NAs in the encoded column:
      // - only when the column to encode contains categories unseen during training (including NA).
      imputeMissingValues(joinedFrame, encodedColIdx, priorMean);
      removeNumeratorAndDenominatorColumns(joinedFrame);
      return joinedFrame;
    }
  }

  private static class DefaultEncodingStrategy extends EncodingStrategy {

    public DefaultEncodingStrategy(BlendingParams blendingParams, double noise, long seed) {
      super(blendingParams, noise, seed);
    }

    @Override
    public Frame doApply(Frame fr, String columnToEncode, Frame encodings, String encodedColumn, int targetClass) {
      int teColumnIdx = fr.find(columnToEncode);
      int encodingsTEColIdx = encodings.find(columnToEncode);
      double priorMean = computePriorMean(encodings);
      
      Frame joinedFrame = mergeEncodings(fr, encodings, teColumnIdx, encodingsTEColIdx);
      Scope.track(joinedFrame);

      int encodedColIdx = applyEncodings(joinedFrame, encodedColumn, priorMean, _blendingParams);
      applyNoise(joinedFrame, encodedColIdx, _noise, _seed);
      // Cases when we can introduce NAs in the encoded column:
      // - only when the column to encode contains categories unseen during training (including NA).
      // We impute NAs with mean computed from training set, which is a data leakage.
      // Note: In case of creating encoding map based on the holdout set we'd better use stratified sampling.
      // Maybe even choose size of holdout taking into account size of the minimal set that represents all levels.
      // Otherwise there are higher chances to get NA's for unseen categories.
      double valueForImputation = valueForImputation(columnToEncode, encodings, priorMean, _blendingParams);
      imputeMissingValues(joinedFrame, encodedColIdx, valueForImputation);
      removeNumeratorAndDenominatorColumns(joinedFrame);
      return joinedFrame;
    }
  }

}
