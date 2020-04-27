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
import water.util.IcedHashMapGeneric;
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

    public static String NUMERATOR_COL_NAME = "numerator";
    public static String DENOMINATOR_COL_NAME = "denominator";

    private final String[] _columnNamesToEncode;

  /**
   *
   * @param columnNamesToEncode names of columns to apply target encoding to
   */
  public TargetEncoder(String[] columnNamesToEncode) {

      if(columnNamesToEncode == null || columnNamesToEncode.length == 0)
        throw new IllegalStateException("Argument 'columnsToEncode' is not defined or empty");

      _columnNamesToEncode = columnNamesToEncode;
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

    /**
     * @param targetColumnName name of the target column
     * @param foldColumnName name of the column that contains fold number the row is belong to
     * @param imputeNAsWithNewCategory set to `true` to impute NAs with new category.     // TODO probably we need to always set it to true bc we do not support null values on the right side of merge operation.
     */
    //TODO do we need to do this preparation before as a separate phase? because we are grouping twice.
    //TODO At least it seems that way in the case of KFold. But even if we need to preprocess for other types of TE calculations... we should not affect KFOLD case anyway.
    public IcedHashMapGeneric<String, Frame> prepareEncodingMap(Frame data,
                                                                String targetColumnName,
                                                                String foldColumnName,
                                                                boolean imputeNAsWithNewCategory) {

        // Validate input data. Not sure whether we should check some of these.
        // It will become clear when we decide if TE is going to be exposed to user or only integrated into AutoML's pipeline

        if(data == null) throw new IllegalStateException("Argument 'data' is missing, with no default");

        if(targetColumnName == null || targetColumnName.equals(""))
            throw new IllegalStateException("Argument 'target' is missing, with no default");

        if(! checkAllTEColumnsExistAndAreCategorical(data, _columnNamesToEncode))
            throw new IllegalStateException("Argument 'columnsToEncode' should contain only names of categorical columns");

        if(Arrays.asList(_columnNamesToEncode).contains(targetColumnName)) {
            throw new IllegalArgumentException("Columns for target encoding contain target column.");
        }

        int targetIndex = data.find(targetColumnName);

        Frame dataWithoutNAsForTarget = null;
        Frame dataWithEncodedTarget = null;

        try {
          //TODO Losing data here, we should use clustering to assign instances with some reasonable target values.
          dataWithoutNAsForTarget = filterOutNAsFromTargetColumn(data, targetIndex);

          dataWithEncodedTarget = ensureTargetColumnIsBinaryCategorical(dataWithoutNAsForTarget, targetColumnName);

          IcedHashMapGeneric<String, Frame> columnToEncodingMap = new IcedHashMapGeneric<>();

          for (String teColumnName : _columnNamesToEncode) { // TODO maybe we can do it in parallel
            Frame teColumnFrame = null;

            imputeNAsForColumn(dataWithEncodedTarget, teColumnName, teColumnName + "_NA");

            teColumnFrame = groupThenAggregateForNumeratorAndDenominator(dataWithEncodedTarget, teColumnName, foldColumnName, targetIndex);

            renameColumn(teColumnFrame,"sum_" + targetColumnName, NUMERATOR_COL_NAME);
            renameColumn(teColumnFrame,"nrow", DENOMINATOR_COL_NAME);

            columnToEncodingMap.put(teColumnName, teColumnFrame);
          }

          dataWithoutNAsForTarget.delete();
          dataWithEncodedTarget.delete();

          return columnToEncodingMap;
        } finally {
          if( dataWithoutNAsForTarget != null) dataWithoutNAsForTarget.delete();
          if( dataWithEncodedTarget != null) dataWithEncodedTarget.delete();
        }
    }

    Frame groupThenAggregateForNumeratorAndDenominator(Frame fr, String teColumnName, String foldColumnName, int targetIndex) {
      int teColumnIndex = fr.find(teColumnName);
      int[] groupByColumns = null;
      if (foldColumnName == null) {
        groupByColumns = new int[]{teColumnIndex};
      }
      else {
        int foldColumnIndex = fr.find(foldColumnName);
        groupByColumns = new int[]{teColumnIndex, foldColumnIndex};
      }

      AstGroup.AGG[] aggs = new AstGroup.AGG[2];

      AstGroup.NAHandling na = AstGroup.NAHandling.ALL;
      aggs[0] = new AstGroup.AGG(AstGroup.FCN.sum, targetIndex, na, -1);
      aggs[1] = new AstGroup.AGG(AstGroup.FCN.nrow, targetIndex, na, -1);

      Frame result = new AstGroup().performGroupingWithAggregations(fr, groupByColumns, aggs).getFrame();
      return register(result);
    }

    Frame ensureTargetColumnIsBinaryCategorical(Frame data, String targetColumnName) {
        Vec targetVec = data.vec(targetColumnName);
        if (!targetVec.isCategorical())
          throw new IllegalStateException("`target` must be a binary categorical vector. We do not support multi-class and continuos target case for now");
        if (targetVec.cardinality() != 2)
          throw new IllegalStateException("`target` must be a binary vector. We do not support multi-class target case for now");
        return data;
    }


  public IcedHashMapGeneric<String, Frame> prepareEncodingMap(Frame data, String targetColumnName, String foldColumnName) {
    // Making imputation to be our only strategy since otherwise current implementation of merge will return unexpected results.
    boolean imputeNAsWithNewCategory = true;
    return prepareEncodingMap( data, targetColumnName, foldColumnName, imputeNAsWithNewCategory);
  }

    String[] getColumnNamesBy(Frame data, int[] columnIndexes) {
        String [] allColumnNames = data._names.clone();
        ArrayList<String> columnNames = new ArrayList<String>();

        for(int idx : columnIndexes) {
            columnNames.add(allColumnNames[idx]);
        }
        return columnNames.toArray(new String[columnIndexes.length]);
    }

    private Frame execRapidsAndGetFrame(String astTree) {
        Val val = Rapids.exec(astTree);
        return register(val.getFrame());
    }

    //TODO We might want to introduce parameter that will change this behaviour. We can treat NA's as extra class.
    Frame filterOutNAsFromTargetColumn(Frame data, int targetColumnIndex) {
        return filterOutNAsInColumn(data, targetColumnIndex);
    }

    Frame imputeNAsForColumn(Frame data, String teColumnName, String strToImpute) {
      int columnIndex = data.find(teColumnName);
      Vec currentVec = data.vec(columnIndex);
      int indexForNACategory = currentVec.cardinality(); // Warn: Cardinality returns int but it could be larger than it for big datasets
      FillNAWithLongValueTask task = new FillNAWithLongValueTask(columnIndex, indexForNACategory);
      task.doAll(data);
      if(task._imputationHappened) {
        String[] oldDomain = currentVec.domain();
        String[] newDomain = new String[indexForNACategory + 1];
        System.arraycopy(oldDomain, 0, newDomain, 0, oldDomain.length);
        newDomain[indexForNACategory] = strToImpute;
        updateDomainGlobally(data, teColumnName, newDomain);
      }
      return data;
    }
    
    private void updateDomainGlobally(Frame fr, String teColumnName, String[] domain) {
      fr.write_lock();
      Vec updatedVec = fr.vec(teColumnName);
      updatedVec.setDomain(domain);
      DKV.put(updatedVec);
      fr.update();
      fr.unlock();
    }

    Frame getOutOfFoldData(Frame encodingMap, String foldColumnName, long currentFoldValue)  {
        int foldColumnIndexInEncodingMap = encodingMap.find(foldColumnName);
        return filterNotByValue(encodingMap, foldColumnIndexInEncodingMap, currentFoldValue);
    }

    long[] getUniqueValuesOfTheFoldColumn(Frame data, int columnIndex) {
        Vec uniqueValues = uniqueValuesBy(data, columnIndex).vec(0);
        long numberOfUniqueValues = uniqueValues.length();
        assert numberOfUniqueValues <= Integer.MAX_VALUE : "Number of unique values exceeded Integer.MAX_VALUE";

        int length = (int) numberOfUniqueValues; // We assume that fold column should not has that many different values and we will fit into node's memory.
        long[] uniqueValuesArr = new long[length];
        for(int i = 0; i < uniqueValues.length(); i++) {
            uniqueValuesArr[i] = uniqueValues.at8(i);
        }
        uniqueValues.remove();
        return uniqueValuesArr;
    }

    private boolean checkAllTEColumnsExistAndAreCategorical(Frame data, String[] columnsToEncode)  {
        for( String columnName : columnsToEncode) {
            int columnIndex = data.find(columnName);
            assert columnIndex!=-1 : "Column name `" +  columnName + "` was not found in the provided data frame";
            if(! data.vec(columnIndex).isCategorical()) return false;
        }
        return true;
    }

    static Frame groupByTEColumnAndAggregate(Frame data, int teColumnIndex) {
      int numeratorColumnIndex = data.find(NUMERATOR_COL_NAME);
      int denominatorColumnIndex = data.find(DENOMINATOR_COL_NAME);
      AstGroup.AGG[] aggs = new AstGroup.AGG[2];

      AstGroup.NAHandling na = AstGroup.NAHandling.ALL;
      aggs[0] = new AstGroup.AGG(AstGroup.FCN.sum, numeratorColumnIndex, na, -1);
      aggs[1] = new AstGroup.AGG(AstGroup.FCN.sum, denominatorColumnIndex, na, -1);

      Frame result = new AstGroup().performGroupingWithAggregations(data, new int[]{teColumnIndex}, aggs).getFrame();
      return register(result);
    }

    Frame rBind(Frame a, Frame b) {
        if(a == null) {
            assert b != null;
            return b;
        } else {
            String tree = String.format("(rbind %s %s)", a._key, b._key);
            return execRapidsAndGetFrame(tree);
        }
    }

    Frame mergeByTEAndFoldColumns(Frame leftFrame, Frame holdoutEncodeMap, int teColumnIndexOriginal, int foldColumnIndexOriginal, int teColumnIndex, int maxFoldValue) {
      addNumeratorAndDenominatorTo(leftFrame);

      int foldColumnIndexInEncodingMap = holdoutEncodeMap.find("foldValueForMerge");
      return BroadcastJoinForTargetEncoder.join(leftFrame, new int[]{teColumnIndexOriginal}, foldColumnIndexOriginal, holdoutEncodeMap, new int[]{teColumnIndex}, foldColumnIndexInEncodingMap, maxFoldValue);
    }

    Frame mergeByTEColumn(Frame leftFrame, Frame holdoutEncodeMap, int teColumnIndexOriginal, int teColumnIndex) {
      addNumeratorAndDenominatorTo(leftFrame);
      return BroadcastJoinForTargetEncoder.join(leftFrame, new int[]{teColumnIndexOriginal}, -1, holdoutEncodeMap, new int[]{teColumnIndex}, -1, 0);
      }
  
    private void addNumeratorAndDenominatorTo(Frame leftFrame) {
      Vec emptyNumerator = leftFrame.anyVec().makeCon(0);
      leftFrame.add(NUMERATOR_COL_NAME, emptyNumerator);
      Vec emptyDenominator = leftFrame.anyVec().makeCon(0);
      leftFrame.add(DENOMINATOR_COL_NAME, emptyDenominator);
    }

    Frame imputeWithMean(Frame fr, int columnIndex, double mean) {
      Vec vecWithEncodings = fr.vec(columnIndex);
      assert vecWithEncodings.get_type() == Vec.T_NUM : "Imputation of mean value is supported only for numerical vectors.";
      long numberOfNAs = vecWithEncodings.naCnt();
      if (numberOfNAs > 0) {
        new FillNAWithDoubleValueTask(columnIndex, mean).doAll(fr);
        Log.info(String.format("Frame with id = %s was imputed with mean = %f ( %d rows were affected)", fr._key, mean, numberOfNAs));
      }
      return fr;
    }
    
    Frame imputeWithPosteriorForNALevelOrWithPrior(String teColumnName, Frame fr, int columnIndex, Frame encodingMapForCurrentTEColumn,
                                                   boolean useBlending, BlendingParams blendingParams, double priorMean) {
      int numberOfRowsInEncodingMap = (int) encodingMapForCurrentTEColumn.numRows();
      String lastDomain = encodingMapForCurrentTEColumn.domains()[0][numberOfRowsInEncodingMap - 1];
      boolean missingValuesWerePresent = lastDomain.equals(teColumnName + "_NA");

      double numeratorForNALevel = encodingMapForCurrentTEColumn.vec(NUMERATOR_COL_NAME).at(numberOfRowsInEncodingMap - 1);
      double denominatorForNALevel = encodingMapForCurrentTEColumn.vec(DENOMINATOR_COL_NAME).at(numberOfRowsInEncodingMap - 1);
      double posteriorForNALevel = numeratorForNALevel / denominatorForNALevel;
      double valueForImputation = missingValuesWerePresent ? (useBlending ? getBlendedValue(posteriorForNALevel, priorMean, numeratorForNALevel, blendingParams) : posteriorForNALevel) : priorMean;
      Vec vecWithEncodings = fr.vec(columnIndex);
      assert vecWithEncodings.get_type() == Vec.T_NUM : "Imputation of mean value is supported only for numerical vectors.";
      long numberOfNAs = vecWithEncodings.naCnt();
      if (numberOfNAs > 0) {
        new FillNAWithDoubleValueTask(columnIndex, valueForImputation).doAll(fr);
        Log.info(String.format("Frame with id = %s was imputed with posterior mean from NA level = %f ( %d rows were affected)", fr._key, valueForImputation, numberOfNAs));
      }
      return fr;
    }

    double calculatePriorMean(Frame fr) {
        Vec numeratorVec = fr.vec(NUMERATOR_COL_NAME);
        Vec denominatorVec = fr.vec(DENOMINATOR_COL_NAME);
        return numeratorVec.mean() / denominatorVec.mean();
    }

    Frame calculateAndAppendBlendedTEEncoding(Frame fr, Frame encodingMap, String appendedColumnName, final BlendingParams blendingParams) {
      int numeratorIndex = fr.find(NUMERATOR_COL_NAME);
      int denominatorIndex = fr.find(DENOMINATOR_COL_NAME);

      double globalMeanForTargetClass = calculatePriorMean(encodingMap); // TODO since target column is the same for all categorical columns we are trying to encode we can compute global mean only once.
      Log.info("Global mean for blending = " + globalMeanForTargetClass);

      Vec zeroVec = fr.anyVec().makeCon(0);
      fr.add(appendedColumnName, zeroVec);
      int encodingsColumnIdx = fr.find(appendedColumnName);
      new CalcEncodingsWithBlending(numeratorIndex, denominatorIndex, globalMeanForTargetClass, blendingParams, encodingsColumnIdx).doAll(fr);
      return fr;
    }

    static class CalcEncodingsWithBlending extends MRTask<CalcEncodingsWithBlending> {
      private double _priorMean;
      private int _numeratorIdx;
      private int _denominatorIdx;
      private int _encodingsIdx;
      private BlendingParams _blendingParams;

      CalcEncodingsWithBlending(int numeratorIdx, int denominatorIdx, double priorMean, BlendingParams blendingParams, int encodingsIdx) {
        _numeratorIdx = numeratorIdx;
        _denominatorIdx = denominatorIdx;
        _priorMean = priorMean;
        _blendingParams = blendingParams;
        _encodingsIdx = encodingsIdx;
      }

      @Override
      public void map(Chunk cs[]) {
        Chunk num = cs[_numeratorIdx];
        Chunk den = cs[_denominatorIdx];
        Chunk encodings = cs[_encodingsIdx];
        for (int i = 0; i < num._len; i++) {
          if (num.isNA(i) || den.isNA(i))
            encodings.setNA(i);
          else if (den.at8(i) == 0) {
            Log.info("Denominator is zero for column index = " + _encodingsIdx + ". Imputing with _priorMean = " + _priorMean);
            encodings.set(i, _priorMean);
          } else {
            double numberOfRowsInCurrentCategory = den.atd(i);
            double posteriorMean = num.atd(i) / den.atd(i);
            double blendedValue = getBlendedValue(posteriorMean, _priorMean, numberOfRowsInCurrentCategory, _blendingParams);
            encodings.set(i, blendedValue);
          }
        }
      }
    }

    private static double getBlendedValue(double posteriorMean, double priorMean, double numberOfRowsInCurrentCategory, BlendingParams blendingParams) {
      double lambda = 1.0 / (1 + Math.exp((blendingParams.getK() - numberOfRowsInCurrentCategory) / blendingParams.getF()));
      return lambda * posteriorMean + (1 - lambda) * priorMean;
    }

    Frame calculateAndAppendTEEncoding(Frame fr, Frame encodingMap, String appendedColumnName) {
      int numeratorIndex = fr.find(NUMERATOR_COL_NAME);
      int denominatorIndex = fr.find(DENOMINATOR_COL_NAME);

      double globalMeanForTargetClass = calculatePriorMean(encodingMap); // we can only operate on encodingsMap because `fr` could not have target column at all

      Vec zeroVec = fr.anyVec().makeCon(0);
      fr.add(appendedColumnName, zeroVec);
      int encodingsColumnIdx = fr.find(appendedColumnName);
      new CalcEncodings(numeratorIndex, denominatorIndex, globalMeanForTargetClass, encodingsColumnIdx).doAll( fr);
      return fr;
    }


    static class CalcEncodings extends MRTask<CalcEncodings> {
      private double _priorMean;
      private int _numeratorIdx;
      private int _denominatorIdx;
      private int _encodingsIdx;

      CalcEncodings(int numeratorIdx, int denominatorIdx, double priorMean, int encodingsIdx) {
        _numeratorIdx = numeratorIdx;
        _denominatorIdx = denominatorIdx;
        _priorMean = priorMean;
        _encodingsIdx = encodingsIdx;
      }

      @Override
      public void map(Chunk cs[]) {
        Chunk num = cs[_numeratorIdx];
        Chunk den = cs[_denominatorIdx];
        Chunk encodings = cs[_encodingsIdx];
        for (int i = 0; i < num._len; i++) {
          if (num.isNA(i) || den.isNA(i))
            encodings.setNA(i);
          else if (den.at8(i) == 0) {
            encodings.set(i, _priorMean);
          } else {
            double posteriorMean = num.atd(i) / den.atd(i);
            encodings.set(i, posteriorMean);
          }
        }
      }
    }

    Frame addNoise(Frame fr, String applyToColumnName, double noiseLevel, long seed) {
      int appyToColumnIndex = fr.find(applyToColumnName);
      if (seed == -1) seed = new Random().nextLong();
      Vec zeroVec = fr.anyVec().makeCon(0);
      Vec randomVec = zeroVec.makeRand(seed);
      Vec runif = fr.add("runif", randomVec);
      int runifIdx = fr.find("runif");
      new AddNoiseTask(appyToColumnIndex, runifIdx, noiseLevel).doAll(fr);

      fr.remove("runif");
      randomVec.remove();
      zeroVec.remove();
      runif.remove();
      return fr;
    }

    public static class AddNoiseTask extends MRTask<AddNoiseTask> {
      private int _applyToColumnIdx;
      private int _runifIdx;
      private double _noiseLevel;

      public AddNoiseTask(int applyToColumnIdx, int runifIdx, double noiseLevel) {
        _applyToColumnIdx = applyToColumnIdx;
        _runifIdx = runifIdx;
        _noiseLevel = noiseLevel;
      }

      @Override
      public void map(Chunk cs[]) {
        Chunk column = cs[_applyToColumnIdx];
        Chunk runifCol = cs[_runifIdx];
        for (int i = 0; i < column._len; i++) {
          if (!column.isNA(i)) {
            column.set(i, column.atd(i) + (runifCol.atd(i) * 2 * _noiseLevel - _noiseLevel));
          }
        }
      }
    }

    Frame subtractTargetValueForLOO(Frame data, String targetColumnName) {
      int numeratorIndex = data.find(NUMERATOR_COL_NAME);
      int denominatorIndex = data.find(DENOMINATOR_COL_NAME);
      int targetIndex = data.find(targetColumnName);

      new SubtractCurrentRowForLeaveOneOutTask(numeratorIndex, denominatorIndex, targetIndex).doAll(data);
      return data;
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

  public Frame applyTargetEncoding(Frame data,
                                   String targetColumnName,
                                   Map<String, Frame> columnToEncodingMap,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   String foldColumnName,
                                   boolean withBlendedAvg,
                                   double noiseLevel,
                                   boolean imputeNAsWithNewCategory,
                                   final BlendingParams blendingParams,
                                   long seed) {
      return applyTargetEncoding(data, targetColumnName, columnToEncodingMap, dataLeakageHandlingStrategy, foldColumnName, withBlendedAvg,
              noiseLevel, seed, null, blendingParams);
  }

    /**
     * Core method for applying pre-calculated encodings to the dataset. There are multiple overloaded methods that we will
     * probably be able to get rid off if we are not going to expose Java API for TE.
     * We can just stick to one signature that will suit internal representations  of the AutoML's pipeline.
     *
     * @param data dataset that will be used as a base for creation of encodings .
     * @param targetColumnName name of the column with respect to which we were computing encodings.
     * @param columnToEncodingMap map of the prepared encodings with the keys being the names of the columns.
     * @param dataLeakageHandlingStrategy see TargetEncoding.DataLeakageHandlingStrategy //TODO use common interface for stronger type safety.
     * @param foldColumnName column's name that contains fold number the row is belong to.
     * @param useBlending whether to apply blending or not.
     * @param noiseLevel amount of noise to add to the final encodings.
     * @param seed we might want to specify particular values for reproducibility in tests.
     * @return copy of the `data` frame with encodings
     */
    public Frame applyTargetEncoding(Frame data,
                                     String targetColumnName,
                                     Map<String, Frame> columnToEncodingMap,
                                     DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                     String foldColumnName,
                                     boolean useBlending,
                                     double noiseLevel,
                                     long seed,
                                     Key<Frame> encodedFrameKey,
                                     BlendingParams blendingParams) {
      if (blendingParams == null) blendingParams = DEFAULT_BLENDING_PARAMS;

        if(noiseLevel < 0 )
            throw new IllegalStateException("`_noiseLevel` must be non-negative");

        Frame dataWithAllEncodings = null;
        try {
          
          if(encodedFrameKey == null){
            encodedFrameKey = Key.make();
          }

          dataWithAllEncodings = data.deepCopy(encodedFrameKey.toString());
          DKV.put(dataWithAllEncodings);

          // Note: for KFold strategy we don't need targetColumnName so that we can exclude values from 
          // current fold - as everything is already precomputed and stored in encoding map.
          // This is not the case with LeaveOneOut when we need to subtract current row's value and for that 
          // we need to make sure that response column is provided and is a binary categorical column.
          if(dataLeakageHandlingStrategy == DataLeakageHandlingStrategy.LeaveOneOut)
            ensureTargetColumnIsBinaryCategorical(dataWithAllEncodings, targetColumnName);

          for (String teColumnName : _columnNamesToEncode) {

            imputeNAsForColumn(dataWithAllEncodings, teColumnName, teColumnName + "_NA");

            String newEncodedColumnName = teColumnName + ENCODED_COLUMN_POSTFIX;

            Frame encodingMapForCurrentTEColumn = columnToEncodingMap.get(teColumnName);
            double priorMeanFromTrainingDataset = calculatePriorMean(encodingMapForCurrentTEColumn);

            int teColumnIndex = dataWithAllEncodings.find(teColumnName);

            switch (dataLeakageHandlingStrategy) {
              case KFold:
                Frame holdoutEncodeMap = null;
                Frame dataWithMergedAggregationsK = null;
                try {
                  if (foldColumnName == null)
                    throw new IllegalStateException("`foldColumn` must be provided for dataLeakageHandlingStrategy = KFold");

                  int teColumnIndexInEncodingMap = encodingMapForCurrentTEColumn.find(teColumnName);

                  int foldColumnIndex = dataWithAllEncodings.find(foldColumnName);
                  long[] foldValues = getUniqueValuesOfTheFoldColumn(encodingMapForCurrentTEColumn, 1);

                  Scope.enter();

                  // Following part is actually a preparation phase for KFold case. Maybe we should move it to prepareEncodingMap method.
                  try {
                    for (long foldValue : foldValues) {
                      Frame outOfFoldData = getOutOfFoldData(encodingMapForCurrentTEColumn, foldColumnName, foldValue);

                      Frame groupedByTEColumnAndAggregate = groupByTEColumnAndAggregate(outOfFoldData, teColumnIndexInEncodingMap);

                      renameColumn(groupedByTEColumnAndAggregate, "sum_numerator", NUMERATOR_COL_NAME);
                      renameColumn(groupedByTEColumnAndAggregate, "sum_denominator", DENOMINATOR_COL_NAME);

                      Frame groupedWithAppendedFoldColumn = addCon(groupedByTEColumnAndAggregate, "foldValueForMerge", foldValue);

                      if (holdoutEncodeMap == null) {
                        holdoutEncodeMap = groupedWithAppendedFoldColumn;
                      } else {
                        Frame newHoldoutEncodeMap = rBind(holdoutEncodeMap, groupedWithAppendedFoldColumn);
                        holdoutEncodeMap.delete();
                        holdoutEncodeMap = newHoldoutEncodeMap;
                      }

                      outOfFoldData.delete();
                      Scope.track(groupedWithAppendedFoldColumn);
                    }
                  } finally {
                    Scope.exit();
                  }
                  // End of the preparation phase

                  int maxFoldValue = (int) ArrayUtils.maxValue(foldValues); 

                  dataWithMergedAggregationsK = mergeByTEAndFoldColumns(dataWithAllEncodings, holdoutEncodeMap, teColumnIndex, foldColumnIndex, teColumnIndexInEncodingMap, maxFoldValue);

                  Frame withEncodingsFrameK = calculateEncoding(dataWithMergedAggregationsK, encodingMapForCurrentTEColumn, newEncodedColumnName, useBlending, blendingParams);

                  Frame withAddedNoiseEncodingsFrameK = applyNoise(withEncodingsFrameK, newEncodedColumnName, noiseLevel, seed);

                  // Cases when we can introduce NA's:
                  // 1) if column is represented only in one fold then during computation of out-of-fold subsets we will get empty aggregations.
                  //   When merging with the original dataset we will get NA'a on the right side
                  // Note: since we create encoding based on training dataset and use KFold mainly when we apply encoding to the training set,
                  // there is zero probability that we haven't seen some category.
                  Frame imputedEncodingsFrameK = imputeWithMean(withAddedNoiseEncodingsFrameK, withAddedNoiseEncodingsFrameK.find(newEncodedColumnName), priorMeanFromTrainingDataset);

                  removeNumeratorAndDenominatorColumns(imputedEncodingsFrameK);
                  dataWithAllEncodings = imputedEncodingsFrameK;
                  
                } catch (Exception ex ) {
                  if (dataWithMergedAggregationsK != null) dataWithMergedAggregationsK.delete();
                  throw ex;
                } finally{
                  if (holdoutEncodeMap != null) holdoutEncodeMap.delete();
                }
                break;
              case LeaveOneOut:
                Frame groupedTargetEncodingMap = null;
                Frame dataWithMergedAggregationsL = null;
                try {
                  foldColumnIsInEncodingMapCheck(foldColumnName, encodingMapForCurrentTEColumn);

                  groupedTargetEncodingMap = groupingIgnoringFoldColumn(foldColumnName, encodingMapForCurrentTEColumn, teColumnName);

                  int teColumnIndexInGroupedEncodingMap = groupedTargetEncodingMap.find(teColumnName);
                  dataWithMergedAggregationsL = mergeByTEColumn(dataWithAllEncodings, groupedTargetEncodingMap, teColumnIndex, teColumnIndexInGroupedEncodingMap);

                  Frame subtractedFrameL = subtractTargetValueForLOO(dataWithMergedAggregationsL, targetColumnName);

                  Frame withEncodingsFrameL = calculateEncoding(subtractedFrameL, groupedTargetEncodingMap, newEncodedColumnName, useBlending, blendingParams); // do we really need to pass groupedTargetEncodingMap again?

                  Frame withAddedNoiseEncodingsFrameL = applyNoise(withEncodingsFrameL, newEncodedColumnName, noiseLevel, seed);

                  // Cases when we can introduce NA's:
                  // 1) Only in case when our encoding map has not seen some category. //TODO move second parameter into the function
                  Frame imputedEncodingsFrameL = imputeWithMean(withAddedNoiseEncodingsFrameL, withAddedNoiseEncodingsFrameL.find(newEncodedColumnName), priorMeanFromTrainingDataset);

                  removeNumeratorAndDenominatorColumns(imputedEncodingsFrameL);

                  dataWithAllEncodings = imputedEncodingsFrameL;
                } catch (Exception ex) {
                  if (dataWithMergedAggregationsL != null) dataWithMergedAggregationsL.delete();
                  throw ex;
                } finally {
                  if (groupedTargetEncodingMap != null) groupedTargetEncodingMap.delete();
                }

                break;
              case None:
                Frame groupedTargetEncodingMapForNone = null;
                Frame dataWithMergedAggregationsN = null;
                try {
                  foldColumnIsInEncodingMapCheck(foldColumnName, encodingMapForCurrentTEColumn);
                  groupedTargetEncodingMapForNone = groupingIgnoringFoldColumn(foldColumnName, encodingMapForCurrentTEColumn, teColumnName);
                  int teColumnIndexInGroupedEncodingMapNone = groupedTargetEncodingMapForNone.find(teColumnName);
                  dataWithMergedAggregationsN = mergeByTEColumn(dataWithAllEncodings, groupedTargetEncodingMapForNone, teColumnIndex, teColumnIndexInGroupedEncodingMapNone);

                  Frame withEncodingsFrameN = calculateEncoding(dataWithMergedAggregationsN, groupedTargetEncodingMapForNone, newEncodedColumnName, useBlending, blendingParams);

                  Frame withAddedNoiseEncodingsFrameN = applyNoise(withEncodingsFrameN, newEncodedColumnName, noiseLevel, seed);
                  // In cases when encoding has not seen some levels we will impute NAs with mean computed from training set. Mean is a dataleakage btw.
                  // Note: In case of creating encoding map based on the holdout set we'd better use stratified sampling.
                  // Maybe even choose size of holdout taking into account size of the minimal set that represents all levels.
                  // Otherwise there are higher chances to get NA's for unseen categories.
                  Frame imputedEncodingsFrameN = imputeWithPosteriorForNALevelOrWithPrior(teColumnName, withAddedNoiseEncodingsFrameN, 
                          withAddedNoiseEncodingsFrameN.find(newEncodedColumnName), groupedTargetEncodingMapForNone, useBlending, blendingParams, priorMeanFromTrainingDataset);

                  removeNumeratorAndDenominatorColumns(imputedEncodingsFrameN);

                  dataWithAllEncodings = imputedEncodingsFrameN;

                } catch (Exception ex) {
                  if (dataWithMergedAggregationsN != null) dataWithMergedAggregationsN.delete();
                  throw ex;
                } finally {
                  if (groupedTargetEncodingMapForNone != null) groupedTargetEncodingMapForNone.delete();
                }
            }
          }
          
          DKV.remove(dataWithAllEncodings._key);
          DKV.put(encodedFrameKey, dataWithAllEncodings);
          dataWithAllEncodings._key = encodedFrameKey;
          return dataWithAllEncodings;
        } catch (Exception ex) {
          if (dataWithAllEncodings != null) dataWithAllEncodings.delete();
          throw ex;
        }
    }

    Frame calculateEncoding(Frame preparedFrame, Frame encodingMap, String newEncodedColumnName, boolean withBlendedAvg, final BlendingParams blendingParams) {
        if (withBlendedAvg) {
            return calculateAndAppendBlendedTEEncoding(preparedFrame, encodingMap, newEncodedColumnName, blendingParams);
        } else {
            return calculateAndAppendTEEncoding(preparedFrame, encodingMap, newEncodedColumnName);
        }
    }

    private Frame applyNoise(Frame frameWithEncodings, String newEncodedColumnName, double noiseLevel, long seed) {
        return noiseLevel > 0 ? addNoise(frameWithEncodings, newEncodedColumnName, noiseLevel, seed) : frameWithEncodings;
    }

    void removeNumeratorAndDenominatorColumns(Frame fr) {
        Vec removedNumeratorNone = fr.remove(NUMERATOR_COL_NAME);
        removedNumeratorNone.remove();
        Vec removedDenominatorNone = fr.remove(DENOMINATOR_COL_NAME);
        removedDenominatorNone.remove();
    }

    void foldColumnIsInEncodingMapCheck(String foldColumnName, Frame targetEncodingMap) {
        if(foldColumnName == null && targetEncodingMap.names().length > 3) {
            throw new IllegalStateException("Passed along encoding map possibly contains fold column. Please provide fold column name so that it becomes possible to regroup (by ignoring folds).");
        }
    }

    public static Frame groupingIgnoringFoldColumn(String foldColumnName, Frame targetEncodingMap, String teColumnName) {
        if (foldColumnName != null) {
            int teColumnIndex = targetEncodingMap.find(teColumnName);

            Frame newTargetEncodingMap = groupByTEColumnAndAggregate(targetEncodingMap, teColumnIndex);
            renameColumn(newTargetEncodingMap,  "sum_" + NUMERATOR_COL_NAME, NUMERATOR_COL_NAME);
            renameColumn(newTargetEncodingMap,  "sum_" + DENOMINATOR_COL_NAME, DENOMINATOR_COL_NAME);
            return newTargetEncodingMap;
        } else {
            Frame targetEncodingMapCopy = targetEncodingMap.deepCopy(Key.make().toString());
            DKV.put(targetEncodingMapCopy);
            return targetEncodingMapCopy;
        }
    }

  public Frame applyTargetEncoding(Frame data,
                                   String targetColumnName,
                                   Map<String, Frame> targetEncodingMap,
                                   DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                   String foldColumn,
                                   boolean withBlendedAvg,
                                   boolean imputeNAsWithNewCategory,
                                   final BlendingParams blendingParams,
                                   long seed) {
  return applyTargetEncoding(data, targetColumnName, targetEncodingMap, dataLeakageHandlingStrategy, foldColumn,
          withBlendedAvg, seed, imputeNAsWithNewCategory, null, blendingParams);
  }
    // Overloaded for the case when user had not specified the noise parameter
    public Frame applyTargetEncoding(Frame data,
                                     String targetColumnName,
                                     Map<String, Frame> targetEncodingMap,
                                     DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                     String foldColumn,
                                     boolean withBlendedAvg,
                                     long seed,
                                     boolean imputeNAsWithNewCategory,
                                     final Key<Frame> encodedColumnName,
                                     final BlendingParams blendingParams) {
        double defaultNoiseLevel = 0.01;
        int targetIndex = data.find(targetColumnName);
        double   noiseLevel = 0.0;
        // When noise is not provided and there is no response column in the `data` frame -> no noise will be added to transformations
        if(targetIndex != -1) {
          Vec targetVec = data.vec(targetIndex);
          noiseLevel = targetVec.isNumeric() ? defaultNoiseLevel * (targetVec.max() - targetVec.min()) : defaultNoiseLevel;
        }
        return applyTargetEncoding(data, targetColumnName, targetEncodingMap, dataLeakageHandlingStrategy, foldColumn,
                withBlendedAvg, noiseLevel, seed, encodedColumnName, blendingParams);
    }

    public Frame applyTargetEncoding(Frame data,
                                     String targetColumnName,
                                     Map<String, Frame> targetEncodingMap,
                                     DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                     boolean withBlendedAvg,
                                     boolean imputeNAsWithNewCategory,
                                     final BlendingParams blendingParams,
                                     long seed) {
      return applyTargetEncoding(data, targetColumnName, targetEncodingMap, dataLeakageHandlingStrategy, null,
              withBlendedAvg, imputeNAsWithNewCategory, blendingParams, seed);
    }

    public Frame applyTargetEncoding(Frame data,
                                     String targetColumnName,
                                     Map<String, Frame> targetEncodingMap,
                                     DataLeakageHandlingStrategy dataLeakageHandlingStrategy,
                                     boolean withBlendedAvg,
                                     double noiseLevel,
                                     boolean imputeNAsWithNewCategory,
                                     final BlendingParams blendingParams,
                                     long seed) {
      assert !DataLeakageHandlingStrategy.KFold.equals(dataLeakageHandlingStrategy) : "Use another overloaded method for KFold dataLeakageHandlingStrategy.";
      return applyTargetEncoding(data, targetColumnName, targetEncodingMap, dataLeakageHandlingStrategy, null,
              withBlendedAvg, noiseLevel, imputeNAsWithNewCategory, blendingParams, seed);
    }

}
