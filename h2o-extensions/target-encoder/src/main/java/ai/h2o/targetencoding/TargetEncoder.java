package ai.h2o.targetencoding;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.FillNAWithDoubleValueTask;
import water.fvec.task.FillNAWithLongValueTask;
import water.rapids.ast.prims.mungers.AstGroup;
import water.rapids.ast.prims.mungers.AstGroup.NAHandling;
import water.util.IcedHashMap;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import static ai.h2o.targetencoding.TargetEncoderFrameHelper.*;

/**
 * Status: alpha version
 * This is a core class for target encoding related logic.
 *
 * In general target encoding could be applied to three types of problems, namely:
 *      1) Binary classification (supported)
 *      2) Multi-class classification (not supported yet)
 *      3) Regression (not supported yet)
 *
 * In order to differentiate between above-mentioned types of problems at hand and enable algorithm to do encodings correctly
 * user should explicitly set corresponding type for a response column:
 *      1) Binary classification: response column should be of a categorical type with cardinality = 2
 *      2) Multi-class: response column should be of a categorical type with cardinality > 2
 *      3) Regression: response column should be of a numerical type
 *
 * Usage: see TargetEncodingTitanicBenchmark.java
 */
public class TargetEncoder extends Iced<TargetEncoder>{

  public enum DataLeakageHandlingStrategy {
    LeaveOneOut,
    KFold,
    None,
  }

  public static final String ENCODED_COLUMN_POSTFIX = "_te";
  public static final BlendingParams DEFAULT_BLENDING_PARAMS = new BlendingParams(10, 20);

  public static String NUMERATOR_COL = "numerator";
  public static String DENOMINATOR_COL = "denominator";

  private static String NA_POSTFIX = "_NA";

  private final String[] _columnNamesToEncode;

  /**
   *
   * @param columnsToEncode names of columns to apply target encoding to
   */
  public TargetEncoder(String[] columnsToEncode) {
    if (columnsToEncode == null || columnsToEncode.length == 0)
      throw new IllegalArgumentException("Argument 'columnsToEncode' is required.");

    _columnNamesToEncode = columnsToEncode;
  }

  /**
   * @param data the training data.
   * @param targetColumn name of the target column.
   * @param foldColumn name of the column used for folding.
   */
  public IcedHashMap<String, Frame> prepareEncodingMap(Frame data,
                                                       String targetColumn,
//                                                       DataLeakageHandlingStrategy leakageHandlingStrategy,
                                                       String foldColumn) {
    if (data == null)
      throw new IllegalArgumentException("Argument 'data' is missing, with no default");
    if (targetColumn == null || targetColumn.equals(""))
      throw new IllegalArgumentException("Argument 'target' is missing, with no default");
    if (Arrays.asList(_columnNamesToEncode).contains(targetColumn)) {
      throw new IllegalArgumentException("Columns for target encoding contain target column.");
    }
    validateColumnsToEncode(data, _columnNamesToEncode);

    Frame dataWithoutTargetNAs = null;
    Frame dataWithEncodedTarget = null;
    try {
      int targetIdx = data.find(targetColumn);
      //TODO Loosing data here, we should use clustering to assign instances with some reasonable target values.
      dataWithoutTargetNAs = filterOutNAsFromTargetColumn(data, targetIdx);
      dataWithEncodedTarget = ensureTargetColumnIsSupported(dataWithoutTargetNAs, targetColumn);

      IcedHashMap<String, Frame> columnToEncodings = new IcedHashMap<>();

      for (String columnToEncode : _columnNamesToEncode) { // TODO: parallelize
        imputeCategoricalColumn(dataWithEncodedTarget, columnToEncode, columnToEncode + NA_POSTFIX);
        Frame encodingsFrame = buildEncodingsFrame(dataWithEncodedTarget, columnToEncode, foldColumn, targetIdx);
        
        // FIXME encodings grouping should be applied here if current API didn't expose the leakageHandlingStrategy in the `TargetEncoderModel#transform` method (same with blending params, seed..).
//      Frame finalEncodingsFrame = applyLeakageStrategyToEncodings(encodingsFrame, columnToEncode, leakageHandlingStrategy, foldColumn);
//        DKV.remove(encodingsFrame._key);
//        encodingsFrame = finalEncodingsFrame;
        
        columnToEncodings.put(columnToEncode, encodingsFrame);
      }

      return columnToEncodings;
    } finally {
      if (dataWithoutTargetNAs != null) dataWithoutTargetNAs.delete();
      if (dataWithEncodedTarget != null) dataWithEncodedTarget.delete();
    }
  }
  
  private Frame applyLeakageStrategyToEncodings(Frame encodings, String columnToEncode, DataLeakageHandlingStrategy leakageHandlingStrategy, String foldColumn) {
    Frame groupedEncodings = null;
    int encodingsTEColIdx = encodings.find(columnToEncode);
    
    try {
      Scope.enter();
      switch (leakageHandlingStrategy) {
        case KFold:
          long[] foldValues = getUniqueColumnValues(encodings, encodings.find(foldColumn));
          for (long foldValue : foldValues) {
            Frame outOfFoldEncodings = getOutOfFoldEncodings(encodings, foldColumn, foldValue);
            Scope.track(outOfFoldEncodings);
            Frame tmpEncodings = groupEncodingsByCategory(outOfFoldEncodings, encodingsTEColIdx);
            Scope.track(tmpEncodings);
            addCon(tmpEncodings, foldColumn, foldValue); //groupingEncodingsByCategory always remove the foldColumn, so we can reuse the same name immediately

            if (groupedEncodings == null) {
              groupedEncodings = tmpEncodings;
            } else {
              Frame newHoldoutEncodings = rBind(groupedEncodings, tmpEncodings);
              groupedEncodings.delete();
              groupedEncodings = newHoldoutEncodings;
            }
            Scope.track(groupedEncodings);
          }
          break;

        case LeaveOneOut:
        case None:
          groupedEncodings = groupEncodingsByCategory(encodings, encodingsTEColIdx, foldColumn != null);
          break;
          
        default:
          throw new IllegalStateException("null or unsupported leakageHandlingStrategy");
      }
      Scope.untrack(groupedEncodings);
    } finally {
      Scope.exit();
    }
    
    return groupedEncodings;
  }

  private void validateColumnsToEncode(Frame data, String[] columnsToEncode)  {
    for (String columnName : columnsToEncode) {
      int columnIdx = data.find(columnName);
      assert columnIdx!=-1 : "Column name `" +  columnName + "` was not found in the provided data frame";
      if (! data.vec(columnIdx).isCategorical())
        throw new IllegalArgumentException("Argument 'columnsToEncode' should contain only names of categorical columns");
    }
  }

  /**
   * @see TargetEncoder#buildEncodingsFrame(Frame, String, String, int) for the expectations on the encodings frame.
   */
  private void checkFoldColumnInEncodings(String foldColumn, Frame encodingsFrame) {
    if (foldColumn == null && encodingsFrame.names().length > 3) {
      throw new IllegalStateException("Encodings frame expect a fold column. Please provide the fold column name.");
    }
    if (foldColumn != null && encodingsFrame.find(foldColumn) < 0) {
      throw new IllegalStateException("The provided fold column name is not available in the encodings frame.");
    }
  }

  /**
   * If a fold column is provided, this produces a frame of shape
   * (unique(col, fold_col), 4) with columns [{col}, {fold_col}, numerator, denominator]
   * Otherwise, it produces a frame of shape
   * (unique(col), 3) with columns [{col}, numerator, denominator]
   * @param fr
   * @param columnToEncode
   * @param foldColumn
   * @param targetIndex
   * @return the frame used to compute TE posteriors for a given column to encode.
   */
  Frame buildEncodingsFrame(Frame fr, String columnToEncode, String foldColumn, int targetIndex) {
    int columnToEncodeIdx = fr.find(columnToEncode);
    
    int[] groupBy = foldColumn == null
            ? new int[]{columnToEncodeIdx}
            : new int[]{columnToEncodeIdx, fr.find(foldColumn)};

    AstGroup.AGG[] aggs = new AstGroup.AGG[2];
    aggs[0] = new AstGroup.AGG(AstGroup.FCN.sum, targetIndex, NAHandling.ALL, -1);
    aggs[1] = new AstGroup.AGG(AstGroup.FCN.nrow, targetIndex, NAHandling.ALL, -1);

    Frame result = new AstGroup().performGroupingWithAggregations(fr, groupBy, aggs).getFrame();
    //change the default column names assigned by the aggregation task
    renameColumn(result, "sum_" + fr.name(targetIndex), NUMERATOR_COL);
    renameColumn(result, "nrow", DENOMINATOR_COL);
    return register(result);
  }

  /**
   * Group encodings by category (summing on all folds present in the frame).
   * Produces a frame of shape (unique(col), 3) with columns [{col}, numerator, denominator].
   * @param encodingsFrame
   * @param teColumnIdx
   * @return
   */
  static Frame groupEncodingsByCategory(Frame encodingsFrame, int teColumnIdx) {
    int numeratorIdx = encodingsFrame.find(NUMERATOR_COL);
    int denominatorIdx = numeratorIdx + 1; //enforced by buildEncodingsFrame
    
    int [] groupBy = new int[] {teColumnIdx};
    
    AstGroup.AGG[] aggs = new AstGroup.AGG[2];
    aggs[0] = new AstGroup.AGG(AstGroup.FCN.sum, numeratorIdx, NAHandling.ALL, -1);
    aggs[1] = new AstGroup.AGG(AstGroup.FCN.sum, denominatorIdx, NAHandling.ALL, -1);

    Frame result = new AstGroup().performGroupingWithAggregations(encodingsFrame, groupBy, aggs).getFrame();
    //change the default column names assigned by the aggregation task
    renameColumn(result, "sum_"+ NUMERATOR_COL, NUMERATOR_COL);
    renameColumn(result, "sum_"+ DENOMINATOR_COL, DENOMINATOR_COL);
    return register(result);
  }
  
  static Frame groupEncodingsByCategory(Frame encodingsFrame, int teColumnIdx, boolean hasFolds) {
    if (hasFolds) {
      return groupEncodingsByCategory(encodingsFrame, teColumnIdx);
    } else {
      Frame result = encodingsFrame.deepCopy(Key.make().toString());  // XXX: is this really necessary? 
      DKV.put(result);
      return result;
    }
  }

  Frame ensureTargetColumnIsSupported(Frame data, String targetColumnName) {
    Vec targetVec = data.vec(targetColumnName);
    if (!targetVec.isCategorical())
      throw new IllegalStateException("`target` must be a categorical vector. We do not support continuous target case for now");
    if (targetVec.cardinality() != 2)
      throw new IllegalStateException("`target` must be a binary vector. We do not support multi-class target case for now");
    return data;
  }

  //TODO We might want to introduce parameter that will change this behaviour. We can treat NA's as extra class.
  Frame filterOutNAsFromTargetColumn(Frame data, int targetColumnIndex) {
    return filterOutNAsInColumn(data, targetColumnIndex);
  }

  /** FIXME: this method is modifying the original fr column in-place, one of the reasons why we currently need a complete deep-copy of the training frame... */
  void imputeCategoricalColumn(Frame data, String categoricalColumn, String naCategory) {
    int columnIdx = data.find(categoricalColumn);
    Vec currentVec = data.vec(columnIdx);
    int indexForNACategory = currentVec.cardinality(); // Warn: Cardinality returns int but it could be larger than int for big datasets
    FillNAWithLongValueTask task = new FillNAWithLongValueTask(columnIdx, indexForNACategory);
    task.doAll(data);
    if (task._imputationHappened) {
      String[] oldDomain = currentVec.domain();
      String[] newDomain = new String[indexForNACategory + 1];
      System.arraycopy(oldDomain, 0, newDomain, 0, oldDomain.length);
      newDomain[indexForNACategory] = naCategory;
      updateColumnDomain(data, categoricalColumn, newDomain);
    }
  }
    
  private void updateColumnDomain(Frame fr, String categoricalColumn, String[] domain) {
    fr.write_lock();
    Vec updatedVec = fr.vec(categoricalColumn);
    updatedVec.setDomain(domain);
    DKV.put(updatedVec);
    fr.update();
    fr.unlock();
  }

  Frame getOutOfFoldEncodings(Frame encodingsFrame, String foldColumn, long foldValue)  {
    int foldColumnIdx = encodingsFrame.find(foldColumn);
    return filterNotByValue(encodingsFrame, foldColumnIdx, foldValue);
  }

  long[] getUniqueColumnValues(Frame data, int columnIndex) {
    Vec uniqueValues = uniqueValuesBy(data, columnIndex).vec(0);
    long numberOfUniqueValues = uniqueValues.length();
    assert numberOfUniqueValues <= Integer.MAX_VALUE : "Number of unique values exceeded Integer.MAX_VALUE";

    int length = (int) numberOfUniqueValues; // We assume that the column should not have that many different values and will fit into node's memory.
    long[] uniqueValuesArr = MemoryManager.malloc8(length);
    for (int i = 0; i < uniqueValues.length(); i++) {
      uniqueValuesArr[i] = uniqueValues.at8(i);
    }
    uniqueValues.remove();
    return uniqueValuesArr;
  }

  /** merge the encodings by TE column */
  Frame mergeEncodings(Frame leftFrame, Frame encodingsFrame,
                       int leftTEColumnIdx, int encodingsTEColumnIdx) {
    return mergeEncodings(leftFrame, encodingsFrame, leftTEColumnIdx, -1, encodingsTEColumnIdx, -1, 0);
  }

  /** merge the encodings by TE column + fold column */
  Frame mergeEncodings(Frame leftFrame, Frame encodingsFrame,
                       int leftTEColumnIdx, int leftFoldColumnIdx,
                       int encodingsTEColumnIdx, int encodingsFoldColumnIdx, 
                       int maxFoldValue) {
    return BroadcastJoinForTargetEncoder.join(
            leftFrame, new int[]{leftTEColumnIdx}, leftFoldColumnIdx,
            encodingsFrame, new int[]{encodingsTEColumnIdx}, encodingsFoldColumnIdx,
            maxFoldValue);
  }

  /** FIXME: this method is modifying the original fr column in-place, one of the reasons why we currently need a complete deep-copy of the training frame... */
  void imputeMissingValues(Frame fr, int columnIndex, double imputedValue) {
    Vec vec = fr.vec(columnIndex);
    assert vec.get_type() == Vec.T_NUM : "Imputation of missing value is supported only for numerical vectors.";
    long numberOfNAs = vec.naCnt();
    if (numberOfNAs > 0) {
      new FillNAWithDoubleValueTask(columnIndex, imputedValue).doAll(fr);
      Log.info(String.format("Frame with id = %s was imputed with posterior value = %f ( %d rows were affected)", fr._key, imputedValue, numberOfNAs));
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

  /**
   * Computes the blended prior and posterior probabilities:<pre>Pᵢ = 𝝺(nᵢ) ȳᵢ + (1 - 𝝺(nᵢ)) ȳ</pre>
   * Note that in case of regression problems, these prior/posterior values should be simply read as mean values without the need to change the formula.
   * The shrinkage factor lambda is a parametric logistic function defined as <pre>𝝺(n) = 1 / ( 1 + e^((k - n)/f) )</pre>
   * @param posteriorMean the posterior mean ( ȳᵢ ) for a given category.
   * @param priorMean the prior mean ( ȳ ).
   * @param numberOfRowsForCategory (nᵢ).
   * @param blendingParams the parameters (k and f) for the shrinkage function.
   * @return
   */
  private static double getBlendedValue(double posteriorMean, double priorMean, long numberOfRowsForCategory, BlendingParams blendingParams) {
    double lambda = 1.0 / (1 + Math.exp((blendingParams.getK() - numberOfRowsForCategory) / blendingParams.getF()));
    return lambda * posteriorMean + (1 - lambda) * priorMean;
  }

  double calculatePriorMean(Frame fr) {
    Vec numeratorVec = fr.vec(NUMERATOR_COL);
    Vec denominatorVec = fr.vec(DENOMINATOR_COL);
    return numeratorVec.mean() / denominatorVec.mean();
  }
  
  /**
   * 
   * @param fr the frame
   * @param newEncodedColumnName the new encoded column to compute and append to the original frame.
   * @param priorMean the global mean on .
   * @param blendingParams if provided, those params are used to blend the prior and posterior values when calculating the encoded value.
   * @return the index of the new encoded column
   */
  int applyEncodings(Frame fr, String newEncodedColumnName, double priorMean, final BlendingParams blendingParams) {
    int numeratorIdx = fr.find(NUMERATOR_COL);
    int denominatorIdx = numeratorIdx + 1; // enforced by the Broadcast join

    Vec zeroVec = fr.anyVec().makeCon(0);
    fr.add(newEncodedColumnName, zeroVec);
    int encodedColumnIdx = fr.numCols() - 1;
    new ApplyEncodings(encodedColumnIdx, numeratorIdx, denominatorIdx, priorMean, blendingParams).doAll(fr);
    return encodedColumnIdx;
  }
  
  /**
   * Distributed task setting the encoded value on a specific column, 
   * given 2 numerator and denominator columns already present on the frame 
   * and additional pre-computations needed to compute the encoded value.
   * 
   * Note that the encoded value will use blending iff `blendingParams` are provided.
   */
  private static class ApplyEncodings extends MRTask<ApplyEncodings> {
    private int _encodedColIdx;
    private int _numeratorIdx;
    private int _denominatorIdx;
    private double _priorMean;
    private BlendingParams _blendingParams;

    ApplyEncodings(int encodedColIdx, int numeratorIdx, int denominatorIdx, double priorMean, BlendingParams blendingParams) {
      _encodedColIdx = encodedColIdx;
      _numeratorIdx = numeratorIdx;
      _denominatorIdx = denominatorIdx;
      _priorMean = priorMean;
      _blendingParams = blendingParams;
    }

    @Override
    public void map(Chunk cs[]) {
      Chunk num = cs[_numeratorIdx];
      Chunk den = cs[_denominatorIdx];
      Chunk encoded = cs[_encodedColIdx];
      boolean useBlending = _blendingParams != null;
      for (int i = 0; i < num._len; i++) {
        if (num.isNA(i) || den.isNA(i)) {
          encoded.setNA(i);
        } else if (den.at8(i) == 0) {
          Log.info("Denominator is zero for column index = " + _encodedColIdx + ". Imputing with _priorMean = " + _priorMean);
          encoded.set(i, _priorMean);
        } else {
          double posteriorMean = num.atd(i) / den.atd(i);
          double encodedValue;
          if (useBlending) {
            long numberOfRowsInCurrentCategory = den.at8(i);  // works for all type of problems
            encodedValue = getBlendedValue(posteriorMean, _priorMean, numberOfRowsInCurrentCategory, _blendingParams);
          } else {
            encodedValue = posteriorMean;
          }
          encoded.set(i, encodedValue);
        }
      }
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

  /** FIXME: this method is modifying the original fr column in-place, one of the reasons why we currently need a complete deep-copy of the training frame... */
  void addNoise(Frame fr, String columnName, double noiseLevel, long seed) {
    int columnIndex = fr.find(columnName);
    if (seed == -1) seed = new Random().nextLong();
    Vec zeroVec = fr.anyVec().makeCon(0);
    Vec randomVec = zeroVec.makeRand(seed);
    try {
      fr.add("runIf", randomVec);
      int runifIdx = fr.numCols() - 1;
      new AddNoiseTask(columnIndex, runifIdx, noiseLevel).doAll(fr);
      fr.remove(runifIdx);
      
//      Vec[] vecs = ArrayUtils.append(fr.vecs(), randomVec);  
//      return new AddNoiseTask(columnIndex, fr.numCols(), noiseLevel).doAll(vecs).outputFrame();
    } finally {
      randomVec.remove();
      zeroVec.remove();
    }
  }

  private static class AddNoiseTask extends MRTask<AddNoiseTask> {
    private int _columnIdx;
    private int _runifIdx;
    private double _noiseLevel;

    public AddNoiseTask(int columnIdx, int runifIdx, double noiseLevel) {
      _columnIdx = columnIdx;
      _runifIdx = runifIdx;
      _noiseLevel = noiseLevel;
    }

    @Override
    public void map(Chunk cs[]) {
      Chunk column = cs[_columnIdx];
      Chunk runifCol = cs[_runifIdx];
      for (int i = 0; i < column._len; i++) {
        if (!column.isNA(i)) {
          column.set(i, column.atd(i) + (runifCol.atd(i) * 2 * _noiseLevel - _noiseLevel));
        }
      }
    }
  }

  /** FIXME: this method is modifying the original fr column in-place, one of the reasons why we currently need a complete deep-copy of the training frame... */
  void subtractTargetValueForLOO(Frame data, String targetColumnName) {
    int numeratorIndex = data.find(NUMERATOR_COL);
    int denominatorIndex = data.find(DENOMINATOR_COL);
    int targetIndex = data.find(targetColumnName);

    new SubtractCurrentRowForLeaveOneOutTask(numeratorIndex, denominatorIndex, targetIndex).doAll(data);
  }

  private static class SubtractCurrentRowForLeaveOneOutTask extends MRTask<SubtractCurrentRowForLeaveOneOutTask> {
    private int _numeratorIdx;
    private int _denominatorIdx;
    private int _targetIdx;

    public SubtractCurrentRowForLeaveOneOutTask(int numeratorIdx, int denominatorIdx, int targetIdx) {
      _numeratorIdx = numeratorIdx;
      _denominatorIdx = denominatorIdx;
      _targetIdx = targetIdx;
    }

    @Override
    public void map(Chunk cs[]) {
      Chunk num = cs[_numeratorIdx];
      Chunk den = cs[_denominatorIdx];
      Chunk target = cs[_targetIdx];
      for (int i = 0; i < num._len; i++) {
        if (!target.isNA(i)) {
          num.set(i, num.atd(i) - target.atd(i));
          den.set(i, den.atd(i) - 1);
        }
      }
    }
  }

  /**
   * Core method for applying pre-calculated encodings to the dataset. There are multiple overloaded methods that we will
   * probably be able to get rid off if we are not going to expose Java API for TE.
   * We can just stick to one signature that will suit internal representations  of the AutoML's pipeline.
   *
   * @param data dataset that will be used as a base for creation of encodings .
   * @param targetColumn name of the column with respect to which we were computing encodings.
   * @param columnToEncodings map of the prepared encodings with the keys being the names of the columns.
   * @param dataLeakageHandlingStrategy see TargetEncoder.DataLeakageHandlingStrategy //TODO use common interface for stronger type safety.
   * @param foldColumn name of the column used for folding.
   * @param blendingParams this provides parameters allowing to mitigate the effect 
   *                       caused by small observations of some categories when computing their encoded value.
   *                       Use null to disable blending.
   * @param noiseLevel amount of noise to add to the final encodings.
   *                   Use 0 to disable noise.
   *                   Use -1 to use the default noise level computed from the target.
   * @param seed we might want to specify particular values for reproducibility in tests.
   * @param resultKey key of the result frame
   * @return a new frame with the encoded columns.
   */
  public Frame applyTargetEncoding(Frame data,
                                   String targetColumn,
                                   Map<String, Frame> columnToEncodings,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   String foldColumn,
                                   BlendingParams blendingParams, 
                                   double noiseLevel,
                                   long seed,
                                   Key<Frame> resultKey) {
    
    if (noiseLevel < 0 ) {
      noiseLevel = defaultNoiseLevel(data, data.find(targetColumn));
      Log.warn("No noise level specified, using default noise level: "+noiseLevel);
    }

    Frame encodedFrame = null;
    try {
      if (resultKey == null){
        resultKey = Key.make();
      }

      encodedFrame = data.deepCopy(Key.make().toString()); // FIXME: why do we need a deep copy of the whole frame? TE is not supposed to modify existing columns 

      // Note: for KFold strategy we don't need targetColumn so that we can exclude values from
      // current fold - as everything is already precomputed and stored in encoding map.
      // This is not the case with LeaveOneOut when we need to subtract current row's value and for that
      // we need to make sure that response column is provided and is a binary categorical column.
      if (dataLeakageHandlingStrategy == DataLeakageHandlingStrategy.LeaveOneOut)
        ensureTargetColumnIsSupported(encodedFrame, targetColumn);

      for (String columnToEncode : _columnNamesToEncode) { // TODO: parallelize this, should mainly require change in naming of num/den columns

        imputeCategoricalColumn(encodedFrame, columnToEncode, columnToEncode + NA_POSTFIX);

        String encodedColumn = columnToEncode + ENCODED_COLUMN_POSTFIX;
        Frame encodings = columnToEncodings.get(columnToEncode);
        double priorMean = calculatePriorMean(encodings);
        int teColumnIdx = encodedFrame.find(columnToEncode);
        int encodingsTEColIdx = encodings.find(columnToEncode);

        switch (dataLeakageHandlingStrategy) {
          case KFold:
            try {
              Scope.enter();
              if (foldColumn == null)
                throw new IllegalStateException("`foldColumn` must be provided for dataLeakageHandlingStrategy = KFold");
              checkFoldColumnInEncodings(foldColumn, encodings);
              Frame holdoutEncodings = applyLeakageStrategyToEncodings(encodings, columnToEncode, dataLeakageHandlingStrategy, foldColumn);
              Scope.track(holdoutEncodings);

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
              
              applyEncodings(joinedFrame, encodedColumn, priorMean, blendingParams);
              removeNumeratorAndDenominatorColumns(joinedFrame);
              
              applyNoise(joinedFrame, encodedColumn, noiseLevel, seed);

              // Cases when we can introduce NA's:
              // 1) if category is present only in one fold then when applying the leakage strategy, this category will be missing in the result frame.
              //    When merging with the original dataset we will therefore get NA's for numerator and denominator, and then to encoded column.
              // Note: since we create encoding based on training dataset and use KFold mainly when we apply encoding to the training set,
              // there is zero probability that we haven't seen some category.
              imputeMissingValues(joinedFrame, joinedFrame.find(encodedColumn), priorMean);
              encodedFrame = joinedFrame;
              Scope.untrack(encodedFrame);
            } finally {
              Scope.exit();
            }
            break;
            
          case LeaveOneOut:
            try {
              Scope.enter();
              checkFoldColumnInEncodings(foldColumn, encodings);
              Frame groupedEncodings = applyLeakageStrategyToEncodings(encodings, columnToEncode, dataLeakageHandlingStrategy, foldColumn);
              Scope.track(groupedEncodings);
              
              Frame joinedFrame = mergeEncodings(encodedFrame, groupedEncodings, teColumnIdx, encodingsTEColIdx);
              Scope.track(joinedFrame);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 
              
              subtractTargetValueForLOO(joinedFrame, targetColumn);
              applyEncodings(joinedFrame, encodedColumn, priorMean, blendingParams); // do we really need to pass groupedEncodings again?
              removeNumeratorAndDenominatorColumns(joinedFrame);
              
              applyNoise(joinedFrame, encodedColumn, noiseLevel, seed);

              // Cases when we can introduce NA's:
              // 1) Only when our encoding map has not seen some category.
              imputeMissingValues(joinedFrame, joinedFrame.find(encodedColumn), priorMean);
              encodedFrame = joinedFrame;
              Scope.untrack(encodedFrame.keys());
            } finally {
               Scope.exit();
            }
            break;
            
          case None:
            try {
              Scope.enter();
              checkFoldColumnInEncodings(foldColumn, encodings);
              Frame groupedEncodings = applyLeakageStrategyToEncodings(encodings, columnToEncode, dataLeakageHandlingStrategy, foldColumn);
              Scope.track(groupedEncodings);
              
              Frame joinedFrame = mergeEncodings(encodedFrame, groupedEncodings, teColumnIdx, encodingsTEColIdx);
              Scope.track(joinedFrame);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 

              applyEncodings(joinedFrame, encodedColumn, priorMean, blendingParams);
              removeNumeratorAndDenominatorColumns(joinedFrame);
              
              applyNoise(joinedFrame, encodedColumn, noiseLevel, seed);
              // In cases when encoding has not seen some levels we will impute NAs with mean computed from training set. Mean is a data leakage btw.
              // Note: In case of creating encoding map based on the holdout set we'd better use stratified sampling.
              // Maybe even choose size of holdout taking into account size of the minimal set that represents all levels.
              // Otherwise there are higher chances to get NA's for unseen categories.
              double valueForImputation = valueForImputation(columnToEncode, groupedEncodings, priorMean, blendingParams);
              imputeMissingValues(joinedFrame, joinedFrame.find(encodedColumn), valueForImputation);
              encodedFrame = joinedFrame;
              Scope.untrack(encodedFrame.keys());
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

  private void applyNoise(Frame frame, String column, double noiseLevel, long seed) {
    if (noiseLevel > 0) addNoise(frame, column, noiseLevel, seed);
  }

  void removeNumeratorAndDenominatorColumns(Frame fr) {
    Vec removedNumeratorNone = fr.remove(NUMERATOR_COL);
    removedNumeratorNone.remove();
    Vec removedDenominatorNone = fr.remove(DENOMINATOR_COL);
    removedDenominatorNone.remove();
  }

  /** 
   * @see #applyTargetEncoding(Frame, String, Map, DataLeakageHandlingStrategy, String, BlendingParams, double, long, Key) 
   **/
  public Frame applyTargetEncoding(Frame data,
                                   String targetColumn,
                                   Map<String, Frame> columnsToEncodings,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   String foldColumn,
                                   final BlendingParams blendingParams, 
                                   double noiseLevel,
                                   long seed) {
    return applyTargetEncoding(
            data,
            targetColumn,
            columnsToEncodings,
            dataLeakageHandlingStrategy,
            foldColumn,
            blendingParams,
            noiseLevel,
            seed,
            null
    );
  }

  /**
   * @see #applyTargetEncoding(Frame, String, Map, DataLeakageHandlingStrategy, String, BlendingParams, double, long, Key)
   **/
  public Frame applyTargetEncoding(Frame data,
                                   String targetColumn,
                                   Map<String, Frame> columnsToEncodings,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   String foldColumn,
                                   final BlendingParams blendingParams,
                                   long seed) {
    return applyTargetEncoding(
            data,
            targetColumn,
            columnsToEncodings,
            dataLeakageHandlingStrategy,
            foldColumn,
            blendingParams,
            -1,
            seed,
            null
    );
  }
  
}
