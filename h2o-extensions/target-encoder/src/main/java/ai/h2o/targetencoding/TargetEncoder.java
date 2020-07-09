package ai.h2o.targetencoding;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.FillNAWithDoubleValueTask;
import water.fvec.task.FillNAWithLongValueTask;
import water.rapids.Rapids;
import water.rapids.Val;
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

  public enum DataLeakageHandlingStrategy {
    LeaveOneOut((byte) 0),
    KFold((byte) 1),
    None((byte) 2);

    /***
     * The handling strategies of data leakage used to be represented as a simple byte. Transition to enum representation
     * while keeping the old API requires introduction of a mapping method of the old values into values found in {@link DataLeakageHandlingStrategy}
     * enum. This method does such mapping.
     *
     * @param val Older byte representation of values in this enum
     * @return Value from the {@link DataLeakageHandlingStrategy} enum corresponding to the older byte value.
     * @throws IllegalArgumentException When no value from the {@link DataLeakageHandlingStrategy} is mapped to given number.
     */
    public static DataLeakageHandlingStrategy fromVal(byte val) throws IllegalArgumentException {
      switch (val) {
        case 0:
          return LeaveOneOut;
        case 1:
          return KFold;
        case 2:
          return None;
        default:
          throw new IllegalArgumentException(String.format("Unknown DataLeakageHandlingStrategy corresponding to value: '%s'", val));
      }
    }

    DataLeakageHandlingStrategy(byte val) {
      this.val = val;
    }

    private final byte val;

    public byte getVal() {
      return val;
    }
  }

  public IcedHashMap<String, Frame> prepareEncodingMap(Frame data, String targetColumn, String foldColumn) {
    // Making imputation to be our only strategy since otherwise current implementation of merge will return unexpected results.
    boolean imputeNAsWithNewCategory = true;
    return prepareEncodingMap( data, targetColumn, foldColumn, imputeNAsWithNewCategory);
  }

  /**
   * @param targetColumn name of the target column
   * @param foldColumn name of the column that contains fold number the row is belong to
   * @param imputeNAsWithNewCategory set to `true` to impute NAs with new category.     // TODO probably we need to always set it to true bc we do not support null values on the right side of merge operation.
   */
  //TODO do we need to do this preparation before as a separate phase? because we are grouping twice.
  //TODO At least it seems that way in the case of KFold. But even if we need to preprocess for other types of TE calculations... we should not affect KFOLD case anyway.
  public IcedHashMap<String, Frame> prepareEncodingMap(Frame data,
                                                       String targetColumn,
                                                       String foldColumn,
                                                       boolean imputeNAsWithNewCategory) {
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
      //TODO Losing data here, we should use clustering to assign instances with some reasonable target values.
      dataWithoutTargetNAs = filterOutNAsFromTargetColumn(data, targetIdx);
      dataWithEncodedTarget = ensureTargetColumnIsBinaryCategorical(dataWithoutTargetNAs, targetColumn); // FIXME: this needs to work for non-binary problems

      IcedHashMap<String, Frame> columnToEncodings = new IcedHashMap<>();

      for (String columnToEncode : _columnNamesToEncode) { // TODO maybe we can do it in parallel
        imputeCategoricalColumn(dataWithEncodedTarget, columnToEncode, columnToEncode + NA_POSTFIX);
        Frame encodingsFrame = buildEncodingsFrame(dataWithEncodedTarget, columnToEncode, foldColumn, targetIdx);
        columnToEncodings.put(columnToEncode, encodingsFrame);
      }

      return columnToEncodings;
    } finally {
      if (dataWithoutTargetNAs != null) dataWithoutTargetNAs.delete();
      if (dataWithEncodedTarget != null) dataWithEncodedTarget.delete();
    }
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

  Frame ensureTargetColumnIsBinaryCategorical(Frame data, String targetColumnName) {
    Vec targetVec = data.vec(targetColumnName);
    if (!targetVec.isCategorical())
      throw new IllegalStateException("`target` must be a binary categorical vector. We do not support multi-class and continuous target case for now");
    if (targetVec.cardinality() != 2)
      throw new IllegalStateException("`target` must be a binary vector. We do not support multi-class target case for now");
    return data;
  }

  String[] getColumnNamesBy(Frame data, int[] columnIndexes) {
    String [] allColumnNames = data._names.clone();
    ArrayList<String> columnNames = new ArrayList<String>();

    for(int idx : columnIndexes) {
      columnNames.add(allColumnNames[idx]);
    }
    return columnNames.toArray(new String[columnIndexes.length]);
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
    long[] uniqueValuesArr = new long[length];
    for(int i = 0; i < uniqueValues.length(); i++) {
      uniqueValuesArr[i] = uniqueValues.at8(i);
    }
    uniqueValues.remove();
    return uniqueValuesArr;
  }

  Frame rBind(Frame a, Frame b) {
    if (a == null) {
      assert b != null;
      return b;
    } else {
      String tree = String.format("(rbind %s %s)", a._key, b._key);
      return execRapidsAndGetFrame(tree);
    }
  }

  private Frame execRapidsAndGetFrame(String astTree) {
    Val val = Rapids.exec(astTree);
    return register(val.getFrame());
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
    double denominatorForNALevel = encodingsFrame.vec(DENOMINATOR_COL).at(encodingsFrameRows - 1);
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
   * @param encodedColumn the new encoded column to compute and append to the original frame.
   * @param priorMean the global mean on .
   * @param blendingParams if provided, those params are used to blend the prior and posterior values when calculating the encoded value.
   * @return the index of the new encoded column
   */
  int calculateAndAppendEncodedColumn(Frame fr, String encodedColumn, double priorMean, final BlendingParams blendingParams) {
    int numeratorIdx = fr.find(NUMERATOR_COL);
    int denominatorIdx = numeratorIdx + 1; // enforced by the Broadcast join

    Vec zeroVec = fr.anyVec().makeCon(0);
    fr.add(encodedColumn, zeroVec);
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
  static class ApplyEncodings extends MRTask<ApplyEncodings> {
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

  public static class AddNoiseTask extends MRTask<AddNoiseTask> {
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

  public static class SubtractCurrentRowForLeaveOneOutTask extends MRTask<SubtractCurrentRowForLeaveOneOutTask> {
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
   * @param useBlending whether to apply blending or not.
   * @param noiseLevel amount of noise to add to the final encodings.
   * @param seed we might want to specify particular values for reproducibility in tests.
   * @param resultKey key of the result frame
   * @param blendingParams if `useBlending` is enabled, this provides parameters allowing to mitigate the effect 
   *                       caused by small observations of some categories when computing their encoded value.
   * @return copy of the `data` frame with encodings
   */
  public Frame applyTargetEncoding(Frame data,
                                   String targetColumn,
                                   Map<String, Frame> columnToEncodings,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   String foldColumn,
                                   boolean useBlending,
                                   double noiseLevel,
                                   long seed,
                                   Key<Frame> resultKey,
                                   BlendingParams blendingParams) {
    // get rid of the `useBlending` flag immediately to avoid having to carry it all along.
    if (!useBlending) 
      blendingParams = null;
    else if (blendingParams == null)
      blendingParams = DEFAULT_BLENDING_PARAMS;

    if (noiseLevel < 0 )
      throw new IllegalStateException("`_noiseLevel` must be non-negative");

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
        ensureTargetColumnIsBinaryCategorical(encodedFrame, targetColumn);

      for (String columnToEncode : _columnNamesToEncode) {

        imputeCategoricalColumn(encodedFrame, columnToEncode, columnToEncode + NA_POSTFIX);

        String encodedColumn = columnToEncode + ENCODED_COLUMN_POSTFIX;
        Frame encodings = columnToEncodings.get(columnToEncode);
        double priorMean = calculatePriorMean(encodings);
        int teColumnIdx = encodedFrame.find(columnToEncode);
        int encodingsTEColIdx = encodings.find(columnToEncode);

        Frame joinedFrame = null;
        switch (dataLeakageHandlingStrategy) {
          case KFold:
            try {
              Scope.enter();
              if (foldColumn == null)
                throw new IllegalStateException("`foldColumn` must be provided for dataLeakageHandlingStrategy = KFold");
              checkFoldColumnInEncodings(foldColumn, encodings);

              int encodingsFoldColIdx = -1;
              int foldColumnIdx = encodedFrame.find(foldColumn);
              long[] foldValues = getUniqueColumnValues(encodings, 1);

              //XXX: Following part is actually a preparation phase for KFold case. Maybe we should move it to prepareEncodingMap method.
              Frame holdoutEncodings = null;
              for (long foldValue : foldValues) {
                Frame outOfFoldEncodings = getOutOfFoldEncodings(encodings, foldColumn, foldValue);
                Scope.track(outOfFoldEncodings);
                Frame groupedEncodings = groupEncodingsByCategory(outOfFoldEncodings, encodingsTEColIdx);
                Scope.track(groupedEncodings);
                encodingsFoldColIdx = addCon(groupedEncodings, "kFoldColumn", foldValue);

                if (holdoutEncodings == null) {
                  holdoutEncodings = groupedEncodings;
                } else {
                  Frame newHoldoutEncodings = rBind(holdoutEncodings, groupedEncodings);
                  holdoutEncodings.delete();
                  holdoutEncodings = newHoldoutEncodings;
                }
              }
              Scope.track(holdoutEncodings);
              // End of the preparation phase

              int maxFoldValue = (int) ArrayUtils.maxValue(foldValues);
              joinedFrame = mergeEncodings(
                      encodedFrame, holdoutEncodings, 
                      teColumnIdx, foldColumnIdx, 
                      encodingsTEColIdx, encodingsFoldColIdx,
                      maxFoldValue
              );
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 
              
              calculateAndAppendEncodedColumn(joinedFrame, encodedColumn, priorMean, blendingParams);
              removeNumeratorAndDenominatorColumns(joinedFrame);
              
              applyNoise(joinedFrame, encodedColumn, noiseLevel, seed);

              // Cases when we can introduce NA's:
              // 1) if column is represented only in one fold then during computation of out-of-fold subsets we will get empty aggregations.
              //   When merging with the original dataset we will get NA'a on the right side
              // Note: since we create encoding based on training dataset and use KFold mainly when we apply encoding to the training set,
              // there is zero probability that we haven't seen some category.
              imputeMissingValues(joinedFrame, joinedFrame.find(encodedColumn), priorMean);
              encodedFrame = joinedFrame;
              Scope.untrack(encodedFrame.keys());
            } catch (Exception ex ) {
              if (joinedFrame != null) joinedFrame.delete();
              throw ex;
            } finally{
              Scope.exit();
            }
            break;
            
          case LeaveOneOut:
            try {
              Scope.enter();
              checkFoldColumnInEncodings(foldColumn, encodings);
              Frame groupedEncodings = groupEncodingsByCategory(encodings, encodingsTEColIdx, foldColumn != null);
              Scope.track(groupedEncodings);
              joinedFrame = mergeEncodings(encodedFrame, groupedEncodings, teColumnIdx, encodingsTEColIdx);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 
              
              subtractTargetValueForLOO(joinedFrame, targetColumn);
              calculateAndAppendEncodedColumn(joinedFrame, encodedColumn, priorMean, blendingParams); // do we really need to pass groupedEncodings again?
              removeNumeratorAndDenominatorColumns(joinedFrame);
              
              applyNoise(joinedFrame, encodedColumn, noiseLevel, seed);

              // Cases when we can introduce NA's:
              // 1) Only in case when our encoding map has not seen some category. //TODO move second parameter into the function
              imputeMissingValues(joinedFrame, joinedFrame.find(encodedColumn), priorMean);
              encodedFrame = joinedFrame;
              Scope.untrack(encodedFrame.keys());
            } catch (Exception ex) {
              if (joinedFrame != null) joinedFrame.delete();
              throw ex;
            } finally {
               Scope.exit();
            }
            break;
            
          case None:
            try {
              Scope.enter();
              checkFoldColumnInEncodings(foldColumn, encodings);
              Frame groupedEncodings = groupEncodingsByCategory(encodings, encodingsTEColIdx, foldColumn != null);
              Scope.track(groupedEncodings);
              joinedFrame = mergeEncodings(encodedFrame, groupedEncodings, teColumnIdx, encodingsTEColIdx);
              DKV.remove(encodedFrame._key); // don't need previous version of encoded frame anymore 

              calculateAndAppendEncodedColumn(joinedFrame, encodedColumn, priorMean, blendingParams);
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
            } catch (Exception ex) {
              if (joinedFrame != null) joinedFrame.delete();
              throw ex;
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

  //**** applyTargetEncoding overloads ****//
  // so many of them! probably not all necessary.
  // with/out noise level
  // with/out result key
  // with/out fold column
  
  public Frame applyTargetEncoding(Frame data,
                                   String targetColumn,
                                   Map<String, Frame> targetEncodingMap,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   String foldColumn,
                                   boolean withBlendedAvg,
                                   double noiseLevel,
                                   boolean imputeNAsWithNewCategory,
                                   final BlendingParams blendingParams,
                                   long seed) {
    return applyTargetEncoding(data, targetColumn, targetEncodingMap, dataLeakageHandlingStrategy, foldColumn, withBlendedAvg,
            noiseLevel, seed, null, blendingParams);
  }

  public Frame applyTargetEncoding(Frame data,
                                   String targetColumn,
                                   Map<String, Frame> targetEncodingMap,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   String foldColumn,
                                   boolean withBlendedAvg,
                                   boolean imputeNAsWithNewCategory,
                                   final BlendingParams blendingParams,
                                   long seed) {
    return applyTargetEncoding(data, targetColumn, targetEncodingMap, dataLeakageHandlingStrategy, foldColumn,
            withBlendedAvg, seed, imputeNAsWithNewCategory, null, blendingParams);
  }
  
  // Overloaded for the case when user had not specified the noise parameter
  public Frame applyTargetEncoding(Frame data,
                                   String targetColumn,
                                   Map<String, Frame> targetEncodingMap,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   String foldColumn,
                                   boolean withBlendedAvg,
                                   long seed,
                                   boolean imputeNAsWithNewCategory,
                                   final Key<Frame> resultKey,
                                   final BlendingParams blendingParams) {
    double defaultNoiseLevel = 0.01;
    int targetIndex = data.find(targetColumn);
    double noiseLevel = 0.0;
    // When noise is not provided and there is no response column in the `data` frame -> no noise will be added to transformations
    if (targetIndex >= 0) {
      Vec targetVec = data.vec(targetIndex);
      noiseLevel = targetVec.isNumeric() ? defaultNoiseLevel * (targetVec.max() - targetVec.min()) : defaultNoiseLevel;
    }
    return applyTargetEncoding(data, targetColumn, targetEncodingMap, dataLeakageHandlingStrategy, foldColumn,
            withBlendedAvg, noiseLevel, seed, resultKey, blendingParams);
  }

  public Frame applyTargetEncoding(Frame data,
                                   String targetColumn,
                                   Map<String, Frame> targetEncodingMap,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   boolean withBlendedAvg,
                                   boolean imputeNAsWithNewCategory,
                                   final BlendingParams blendingParams,
                                   long seed) {
    return applyTargetEncoding(data, targetColumn, targetEncodingMap, dataLeakageHandlingStrategy, null,
            withBlendedAvg, imputeNAsWithNewCategory, blendingParams, seed);
  }

  public Frame applyTargetEncoding(Frame data,
                                   String targetColumn,
                                   Map<String, Frame> targetEncodingMap,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   boolean withBlendedAvg,
                                   double noiseLevel,
                                   boolean imputeNAsWithNewCategory,
                                   final BlendingParams blendingParams,
                                   long seed) {
    assert !DataLeakageHandlingStrategy.KFold.equals(dataLeakageHandlingStrategy) : "Use another overloaded method for KFold dataLeakageHandlingStrategy.";
    return applyTargetEncoding(data, targetColumn, targetEncodingMap, dataLeakageHandlingStrategy, null,
            withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, blendingParams, seed);
  }

}
