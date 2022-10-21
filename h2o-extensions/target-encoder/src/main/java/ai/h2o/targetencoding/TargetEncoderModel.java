package ai.h2o.targetencoding;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.FillNAWithDoubleValueTask;
import org.apache.log4j.Logger;
import water.udf.CFuncRef;
import water.util.*;

import java.util.*;
import java.util.PrimitiveIterator.OfInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static ai.h2o.targetencoding.TargetEncoderHelper.*;
import static ai.h2o.targetencoding.interaction.InteractionSupport.addFeatureInteraction;

public class TargetEncoderModel extends Model<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters, TargetEncoderModel.TargetEncoderOutput> {

  public static final String ALGO_NAME = "TargetEncoder";
  public static final int NO_FOLD = -1;

  static final String NA_POSTFIX = "_NA";
  static final String TMP_COLUMN_POSTFIX = "_tmp";
  static final String ENCODED_COLUMN_POSTFIX = "_te";
  static final BlendingParams DEFAULT_BLENDING_PARAMS = new BlendingParams(10, 20);
  
  private static final Logger LOG = Logger.getLogger(TargetEncoderModel.class);

  public enum DataLeakageHandlingStrategy {
    LeaveOneOut,
    KFold,
    None,
  }

  public static class TargetEncoderParameters extends Model.Parameters {
    public String[][] _columns_to_encode;
    public boolean _blending = false;
    public double _inflection_point = DEFAULT_BLENDING_PARAMS.getInflectionPoint();
    public double _smoothing = DEFAULT_BLENDING_PARAMS.getSmoothing();
    public DataLeakageHandlingStrategy _data_leakage_handling = DataLeakageHandlingStrategy.None;
    public double _noise = 0.01;
    public boolean _keep_original_categorical_columns = true; // not a good default, but backwards compatible.
    
    boolean _keep_interaction_columns = false; // not exposed: convenient for testing.
    
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
    public ColumnsToSingleMapping[] _input_to_encoding_column; // maps input columns (or groups of columns) to the single column being effectively encoded (= key in _target_encoding_map).
    public ColumnsMapping[] _input_to_output_columns; // maps input columns (or groups of columns) to their corresponding encoded one(s).
    public IcedHashMap<String, Frame> _target_encoding_map;
    public IcedHashMap<String, Boolean> _te_column_to_hasNAs; //XXX: Map is a wrong choice for this, IcedHashSet would be perfect though
    
    
    public TargetEncoderOutput(TargetEncoder te) {
      super(te);
      _parms = te._parms;
      _nclasses = te.nclasses();
    }
    
    void init(IcedHashMap<String, Frame> teMap, ColumnsToSingleMapping[] columnsToEncodeMapping) {
      _target_encoding_map = teMap;
      _input_to_encoding_column = columnsToEncodeMapping;
      _input_to_output_columns = buildInOutColumnsMapping();
      _te_column_to_hasNAs = buildCol2HasNAsMap();
      _model_summary = constructSummary();
    }

    /**
     * builds the name of encoded columns
     */
    private ColumnsMapping[] buildInOutColumnsMapping() {
      ColumnsMapping[] encMapping = new ColumnsMapping[_input_to_encoding_column.length];
      for (int i=0; i < encMapping.length; i++) {
        ColumnsToSingleMapping toEncode = _input_to_encoding_column[i];
        String[] groupCols = toEncode.from();
        String columnToEncode = toEncode.toSingle();
        Frame encodings = _target_encoding_map.get(columnToEncode);
        String[] encodedColumns = listUsedTargetClasses().mapToObj(tc -> 
                encodedColumnName(columnToEncode, tc, encodings.vec(TARGETCLASS_COL))
        ).toArray(String[]::new);
        encMapping[i] = new ColumnsMapping(groupCols, encodedColumns);
      }
      return encMapping;
    }

    private IcedHashMap<String, Boolean> buildCol2HasNAsMap() {
      final IcedHashMap<String, Boolean> col2HasNAs = new IcedHashMap<>();
      for (Map.Entry<String, Frame> entry : _target_encoding_map.entrySet()) {
        String teColumn = entry.getKey();
        Frame encodingsFrame = entry.getValue();
        int teColCard = _parms.train().vec(teColumn) == null 
                ? -1  // justifies the >0 test below: if teColumn is a transient (generated for interactions and therefore not in train), it can't have NAs as they're all already encoded.
                : _parms.train().vec(teColumn).cardinality();
        boolean hasNAs = teColCard > 0 && teColCard < encodingsFrame.vec(teColumn).cardinality(); //XXX: _parms.train().vec(teColumn).naCnt() > 0 ?
        col2HasNAs.put(teColumn, hasNAs);
      }
      return col2HasNAs;
    }
    
    private IntStream listUsedTargetClasses()  {
      return _nclasses == 1 ? IntStream.of(NO_TARGET_CLASS) // regression
              : _nclasses == 2 ? IntStream.of(1)  // binary (use only positive target)
              : IntStream.range(1, _nclasses);     // multiclass (skip only the 0 target for symmetry with binary)
    }
    
    private TwoDimTable constructSummary(){
      TwoDimTable summary = new TwoDimTable(
              "Target Encoder model summary",
              "Summary for target encoder model",
              new String[_input_to_output_columns.length],
              new String[]{"Original name(s)", "Encoded column name(s)"},
              new String[]{"string", "string"},
              null,
              null
      );
      
      for (int i = 0; i < _input_to_output_columns.length; i++) {
        ColumnsMapping mapping = _input_to_output_columns[i];
        summary.set(i, 0, String.join(", ", mapping.from()));
        summary.set(i, 1, String.join(", ", mapping.to()));
      }

      return summary;
    }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.TargetEncoder;
    }
  }
  
  private static String encodedColumnName(String columnToEncode, int targetClass, Vec targetCol) {
    if (targetClass == NO_TARGET_CLASS || targetCol == null) {
      return columnToEncode + ENCODED_COLUMN_POSTFIX;
    } else {
      String targetClassName = targetCol.domain()[targetClass];
      return columnToEncode + "_" + StringUtils.sanitizeIdentifier(targetClassName) + ENCODED_COLUMN_POSTFIX;
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
    if (!canApplyTargetEncoding(fr)) return fr;
    try (Scope.Safe safe = Scope.safe(fr)) {
      Frame adaptFr = adaptForEncoding(fr);
      return Scope.untrack(applyTargetEncoding(
              adaptFr,
              asTraining,
              outOfFold, 
              blendingParams,
              noiseLevel,
              null
      ));
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
    if (!canApplyTargetEncoding(fr)) {
      Frame res = new Frame(Key.make(destination_key), fr.names(), fr.vecs());
      DKV.put(res);
      return res;
    }
    try (Scope.Safe safe = Scope.safe(fr)) {
      Frame adaptFr = adaptForEncoding(fr);
      return Scope.untrack(applyTargetEncoding(
              adaptFr, 
              false,
              NO_FOLD,
              _parms.getBlendingParameters(),
              _parms._noise, 
              Key.make(destination_key)
      ));
    }
  }
  
  private Frame adaptForEncoding(Frame fr) {
    Frame adaptFr = new Frame(fr);
    Map<String, Integer> nameToIdx = nameToIndex(fr);
    for (int i=0; i<_output._names.length; i++) {
      String col = _output._names[i];
      String[] domain = _output._domains[i];
      int toAdaptIdx;
      if (domain != null && (toAdaptIdx = nameToIdx.getOrDefault(col, -1)) >= 0) {
        Vec toAdapt = adaptFr.vec(toAdaptIdx);
        if (!Arrays.equals(toAdapt.domain(), domain)) {
          Vec adapted = toAdapt.adaptTo(domain);
          adaptFr.replace(toAdaptIdx, adapted);
        }
      }
    }
    return adaptFr;
  }
  
  private boolean canApplyTargetEncoding(Frame fr) {
    Set<String> frColumns = new HashSet<>(Arrays.asList(fr.names()));
    boolean canApply = Arrays.stream(_output._input_to_encoding_column)
            .map(m -> Arrays.asList(m.from()))
            .anyMatch(frColumns::containsAll);
    if (!canApply) {
      LOG.info("Frame "+fr._key+" has no columns to encode with TargetEncoder, skipping it: " +
              "columns="+Arrays.toString(fr.names())+", " +
              "target encoder columns="+Arrays.deepToString(Arrays.stream(_output._input_to_encoding_column).map(ColumnsMapping::from).toArray(String[][]::new))
      );
    }
    return canApply;
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
      LOG.warn("No noise level specified, using default noise level: "+noise);
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

    List<Keyed> tmps = new ArrayList<>();
    Frame workingFrame = null;
    Key<Frame> tmpKey;
    try {
      workingFrame = makeWorkingFrame(data);;
      tmpKey = workingFrame._key;

      for (ColumnsToSingleMapping columnsToEncode: _output._input_to_encoding_column) { // TODO: parallelize this, should mainly require change in naming of num/den columns
        String[] colGroup = columnsToEncode.from();
        String columnToEncode = columnsToEncode.toSingle();
        Frame encodings = _output._target_encoding_map.get(columnToEncode);
        assert encodings != null;
        
        // passing the interaction domain obtained during training:
        // - this ensures that the interaction column will have the same domain as in training (no need to call adaptTo on the new Vec).
        // - this improves speed when creating the interaction column (no need to extract the domain).
        // - unseen values/interactions are however represented as NAs in the new column, which is acceptable as TE encodes them in the same way anyway.
        int colIdx = addFeatureInteraction(workingFrame, colGroup, columnsToEncode.toDomain());
        if (colIdx < 0) {
          LOG.warn("Column "+Arrays.toString(colGroup)+" is missing in frame "+data._key);
          continue;
        }
        assert workingFrame.name(colIdx).equals(columnToEncode);
        
        // if not applying encodings to training data, then get rid of the foldColumn in encodings.
        if (dataLeakageHandlingStrategy != DataLeakageHandlingStrategy.KFold && encodings.find(foldColumn) >= 0) {
          encodings = groupEncodingsByCategory(encodings, encodings.find(columnToEncode));
          tmps.add(encodings);
        }
        
        imputeCategoricalColumn(workingFrame, colIdx, columnToEncode + NA_POSTFIX);

        for (OfInt it = _output.listUsedTargetClasses().iterator(); it.hasNext(); ) {
          int tc = it.next();
          try {
            workingFrame = strategy.apply(workingFrame, columnToEncode, encodings, tc);
          } finally {
            DKV.remove(tmpKey);
            tmpKey = workingFrame._key;
          }
        } // end for each target 
        
        if (!_parms._keep_interaction_columns && colGroup.length > 1)
          tmps.add(workingFrame.remove(colIdx));
      } // end for each columnToEncode
      
      if (!_parms._keep_original_categorical_columns) {
        Set<String> removed = new HashSet<>();
        for (ColumnsMapping columnsToEncode: _output._input_to_encoding_column) {
          for (String col: columnsToEncode.from()) {
            if (removed.contains(col)) continue;
            tmps.add(workingFrame.remove(col));
            removed.add(col);
          }
        }
      }

      DKV.remove(tmpKey);
      workingFrame._key = resultKey;
      reorderColumns(workingFrame);
      DKV.put(workingFrame);
      return workingFrame;
    } catch (Exception e) {
      if (workingFrame != null) workingFrame.delete();
      throw e;
    } finally {
      for (Keyed tmp : tmps) tmp.remove();
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

  /**
   * Ideally there should be no need to deep copy columns that are not listed as input in _input_to_output_columns.
   * However if we keep the original columns in the output, then they are deleted in the model integration: {@link hex.ModelBuilder#track}.
   * On the other side, if copied as a "ShallowVec" (extending WrappedVec) to prevent deletion of data in trackFrame, 
   *  then we expose WrappedVec to the client in all non-integration use cases, which is strongly discouraged.
   * Catch-22 situation, so keeping the deepCopy for now.
   * NOTE! New tracking keys logic should keep vecs from original training frame protected in any case (see {@link Scope#protect}), 
   * which should allow us to get rid of this deep copy, and always replace old vec by a new one when transforming it. 
   * @param fr
   * @return the working frame used to make predictions
   */
  private Frame makeWorkingFrame(Frame fr) {
    return fr.deepCopy(Key.make().toString());
  }

  /**
   * For model integration, we need to ensure that columns are offered to the model in a consistent order.
   * After TE encoding, columns are always in the following order:
   * <ol>
   *     <li>non-categorical predictors present in training frame</li>
   *     <li>TE-encoded predictors</li>
   *     <li>remaining categorical predictors present in training frame</li>
   *     <li>remaining predictors not present in training frame</li>
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
    String[] trainColumns = _output._names;
    Set<String> trainCols = new HashSet<>(Arrays.asList(trainColumns));
    String[] notInTrainColumns = Arrays.stream(fr.names())
            .filter(c -> !trainCols.contains(c))
            .toArray(String[]::new);
    int[] newOrder = new int[fr.numCols()];
    int offset = 0;
    for (String col : trainColumns) {
      if (nameToIdx.containsKey(col) && !ArrayUtils.contains(toTheEnd, col)) {
        int idx = nameToIdx.get(col);
        if (fr.vec(idx).isCategorical()) {
          toAppendAfterNumericals.add(idx); //first appending categoricals
        } else {
          newOrder[offset++] = idx; //adding all non-categoricals first
        }
      }
    }
    String[] encodedColumns = Arrays.stream(_output._input_to_output_columns)
            .flatMap(m -> Stream.of(m.to()))
            .toArray(String[]::new);
    Set<String> encodedCols = new HashSet<>(Arrays.asList(encodedColumns));
    for (String col : encodedColumns) { // TE-encoded cols
        if (nameToIdx.containsKey(col)) newOrder[offset++] = nameToIdx.get(col);
    }
    for (String col : notInTrainColumns) {
      if (!encodedCols.contains(col)) toAppendAfterNumericals.add(nameToIdx.get(col)); // appending columns only in fr
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
        Frame appliedEncodings;
        int tcIdx = encodings.find(TARGETCLASS_COL);
        if (tcIdx < 0) {
          appliedEncodings = encodings;
        } else {
          appliedEncodings = filterByValue(encodings, tcIdx, targetClass);
          Scope.track(appliedEncodings);
          appliedEncodings.remove(TARGETCLASS_COL);
        }
        String encodedColumn = encodedColumnName(columnToEncode, targetClass, tcIdx < 0 ? null : encodings.vec(tcIdx));
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
        if (LOG.isInfoEnabled())
          LOG.info(String.format("Frame with id = %s was imputed with posterior value = %f ( %d rows were affected)", fr._key, imputedValue, vec.naCnt()));
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
      Vec removedNumerator = fr.remove(NUMERATOR_COL);
      removedNumerator.remove();
      Vec removedDenominator = fr.remove(DENOMINATOR_COL);
      removedDenominator.remove();
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
      if (_outOfFold == NO_FOLD) {
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
      if (_outOfFold != NO_FOLD) {
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
