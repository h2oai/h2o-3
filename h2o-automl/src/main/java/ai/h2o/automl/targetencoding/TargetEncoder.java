package ai.h2o.automl.targetencoding;

import water.*;
import water.fvec.*;
import water.fvec.task.FillNAWithLongValueTask;
import water.fvec.task.FillNAWithDoubleValueTask;
import water.rapids.Merge;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.FrameUtils;
import water.util.Log;
import static ai.h2o.automl.targetencoding.TargetEncoderFrameHelper.*;

import java.util.*;

/**
 * Status: alpha version
 * This is a core class for target encoding related logic.
 *
 * In general target encoding could be applied to three types of problems, namely:
 *      1) Binary classification (supported)
 *      2) Multi-class classification (not supported yet)
 *      3) Regression (not supported yet)
 *
 * In order to differentiate between abovementioned types of problems at hand and enable algorithm to do encodings correctly
 * user should explicitly set corresponding type for a response column:
 *      1) Binary classification: response column should be of a categorical type with cardinality = 2
 *      2) Multi-class: response column should be of a categorical type with cardinality > 2
 *      3) Regression: response column should be of a numerical type
 *
 * Usage: see TargetEncodingTitanicBenchmark.java
 */
public class TargetEncoder {

    private BlendingParams _blendingParams;
    private String[] _columnNamesToEncode;

  /**
   *
   * @param columnNamesToEncode names of columns to apply target encoding to
   * @param blendingParams
   */
    public TargetEncoder (String[] columnNamesToEncode,
                          BlendingParams blendingParams) {

      if(columnNamesToEncode == null || columnNamesToEncode.length == 0)
        throw new IllegalStateException("Argument 'columnsToEncode' is not defined or empty");

      _columnNamesToEncode = columnNamesToEncode;
      _blendingParams = blendingParams;
    }

    public TargetEncoder (String[] columnNamesToEncode) {
      this(columnNamesToEncode, new BlendingParams(20, 10));
    }

    public static class DataLeakageHandlingStrategy {
        public static final byte LeaveOneOut  =  0;
        public static final byte KFold  =  1;
        public static final byte None  =  2;
    }

    /**
     * @param targetColumnName name of the target column
     * @param foldColumnName name of the column that contains fold number the row is belong to
     * @param imputeNAsWithNewCategory set to `true` to impute NAs with new category.     // TODO probably we need to always set it to true bc we do not support null values on the right side of merge operation.
     */
    //TODO do we need to do this preparation before as a separate phase? because we are grouping twice.
    //TODO At least it seems that way in the case of KFold. But even if we need to preprocess for other types of TE calculations... we should not affect KFOLD case anyway.
    public Map<String, Frame> prepareEncodingMap(Frame data,
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

          Map<String, Frame> columnToEncodingMap = new HashMap<>();

          for (String teColumnName : _columnNamesToEncode) { // TODO maybe we can do it in parallel
            Frame teColumnFrame = null;

            imputeNAsForColumn(dataWithEncodedTarget, teColumnName, teColumnName + "_NA");

            teColumnFrame = groupThenAggregateForNumeratorAndDenominator(dataWithEncodedTarget, teColumnName, foldColumnName, targetIndex);

            renameColumn(teColumnFrame,"sum_" + targetColumnName, "numerator");
            renameColumn(teColumnFrame,"nrow", "denominator");

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
      aggs[0] = new AstGroup.AGG(AstGroup.FCN.sum, targetIndex, na, (int) fr.vec(targetIndex).max() + 1);
      aggs[1] = new AstGroup.AGG(AstGroup.FCN.nrow, targetIndex, na, (int) fr.vec(targetIndex).max() + 1);

      Frame result = new AstGroup().performGroupingWithAggregations(fr, groupByColumns, aggs, -1).getFrame();
      return register(result);
    }

    // This method is mutating data frame
    Frame ensureTargetColumnIsBinaryCategorical(Frame data, String targetColumnName) {
        int targetIndex = data.find(targetColumnName);
        if (data.vec(targetIndex).isCategorical()){
            Vec targetVec = data.vec(targetIndex);
            if(targetVec.cardinality() == 2) {
                return data;
            }
            else {
                throw new IllegalStateException("`target` must be a binary vector. We do not support multi-class target case for now");
            }
        }
        else {
          throw new IllegalStateException("`target` must be a binary categorical vector. We do not support multi-class and continuos target case for now");
        }
    };


  public Map<String, Frame> prepareEncodingMap(Frame data, String targetColumnName, String foldColumnName) {
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
      new FillNAWithLongValueTask(columnIndex, indexForNACategory).doAll(data);
      String[] oldDomain = currentVec.domain();
      String[] newDomain = new String[indexForNACategory + 1];
      System.arraycopy(oldDomain, 0, newDomain, 0, oldDomain.length);
      newDomain[indexForNACategory] = strToImpute;
      currentVec.setDomain(newDomain);
      return data;
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

    Frame groupByTEColumnAndAggregate(Frame data, int teColumnIndex) {
      int numeratorColumnIndex = data.find("numerator");
      int denominatorColumnIndex = data.find("denominator");
      AstGroup.AGG[] aggs = new AstGroup.AGG[2];

      AstGroup.NAHandling na = AstGroup.NAHandling.ALL;
      aggs[0] = new AstGroup.AGG(AstGroup.FCN.sum, numeratorColumnIndex, na, (int) data.vec(numeratorColumnIndex).max() + 1);
      aggs[1] = new AstGroup.AGG(AstGroup.FCN.sum, denominatorColumnIndex, na, (int) data.vec(denominatorColumnIndex).max() + 1);

      Frame result = new AstGroup().performGroupingWithAggregations(data, new int[]{teColumnIndex}, aggs, -1).getFrame();
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

    Frame mergeByTEAndFoldColumns(Frame a, Frame holdoutEncodeMap, int teColumnIndexOriginal, int foldColumnIndexOriginal, int teColumnIndex) {
      int foldColumnIndexInEncodingMap = holdoutEncodeMap.find("foldValueForMerge");
      return merge(a, holdoutEncodeMap, new int[]{teColumnIndexOriginal, foldColumnIndexOriginal}, new int[]{teColumnIndex, foldColumnIndexInEncodingMap});
    }

    static class GCForceTask extends MRTask<GCForceTask> {
      @Override
      protected void setupLocal() {
        System.gc();
      }
    }

    // Custom extract from AstMerge's implementation for a particular case `(merge l r TRUE FALSE [] [] 'auto' )`
    Frame merge(Frame l, Frame r, int[] byLeft, int[] byRite) {
      boolean allLeft = true;
      // See comments in the original implementation in AstMerge.java
      new GCForceTask().doAllNodes();

      int ncols = byLeft.length;
      l.moveFirst(byLeft);
      r.moveFirst(byRite);

      int[][] id_maps = new int[ncols][];
      for (int i = 0; i < ncols; i++) {
        Vec lv = l.vec(i);
        Vec rv = r.vec(i);

        if (lv.isCategorical()) {
          assert rv.isCategorical();
          id_maps[i] = CategoricalWrappedVec.computeMap(lv.domain(), rv.domain());
        }
      }
      int cols[] = new int[ncols];
      for (int i = 0; i < ncols; i++) cols[i] = i;
      return register(Merge.merge(l, r, cols, cols, allLeft, id_maps));
    }

    Frame mergeByTEColumn(Frame a, Frame b, int teColumnIndexOriginal, int teColumnIndex) {
      return merge(a, b, new int[]{teColumnIndexOriginal}, new int[]{teColumnIndex});
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

    double calculatePriorMean(Frame fr) {
        Vec numeratorVec = fr.vec("numerator");
        Vec denominatorVec = fr.vec("denominator");
        return numeratorVec.mean() / denominatorVec.mean();
    }

    Frame calculateAndAppendBlendedTEEncoding(Frame fr, Frame encodingMap, String targetColumnName, String appendedColumnName) {
      int numeratorIndex = fr.find("numerator");
      int denominatorIndex = fr.find("denominator");

      double globalMeanForTargetClass = calculatePriorMean(encodingMap); // TODO since target column is the same for all categorical columns we are trying to encode we can compute global mean only once.
      Log.info("Global mean for blending = " + globalMeanForTargetClass);

      Vec zeroVec = Vec.makeZero(fr.numRows());
      fr.add(appendedColumnName, zeroVec);
      int encodingsColumnIdx = fr.find(appendedColumnName);
      new CalcEncodingsWithBlending(numeratorIndex, denominatorIndex, globalMeanForTargetClass, _blendingParams, encodingsColumnIdx).doAll(fr);
      zeroVec.remove();
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
            Log.info("Denominator is zero. Imputing with _priorMean = " + _priorMean);
            encodings.set(i, _priorMean);
          } else {
            double numberOfRowsInCurrentCategory = den.atd(i);
            double lambda = 1.0 / (1 + Math.exp((_blendingParams.getK() - numberOfRowsInCurrentCategory) / _blendingParams.getF()));
            double posteriorMean = num.atd(i) / den.atd(i);
            double blendedValue = lambda * posteriorMean + (1 - lambda) * _priorMean;
            encodings.set(i, blendedValue);
          }
        }
      }
    }

    Frame calculateAndAppendTEEncoding(Frame fr, Frame encodingMap, String targetColumnName, String appendedColumnName) {
      int numeratorIndex = fr.find("numerator");
      int denominatorIndex = fr.find("denominator");

      double globalMeanForTargetClass = calculatePriorMean(encodingMap); // we can only operate on encodingsMap because `fr` could not have target column at all

      Vec zeroVec = Vec.makeZero(fr.numRows());
      fr.add(appendedColumnName, zeroVec);
      int encodingsColumnIdx = fr.find(appendedColumnName);
      new CalcEncodings(numeratorIndex, denominatorIndex, globalMeanForTargetClass, encodingsColumnIdx).doAll( fr);
      zeroVec.remove();
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

    //TODO think about what value could be used for substitution ( maybe even taking into account target's value)
    /*private String getDenominatorIsZeroSubstitutionTerm(Frame fr, String targetColumnName, double globalMeanForTargetClass) {
      // This should happen only for Leave-One-Out case:
      // These groups have this singleness in common and we probably want to represent it somehow.
      // If we choose just global average then we just lose difference between single-row-groups that have different target values.
      // We can: 1) Group is so small that we even don't want to care about te_column's values.... just use Prior average.
      //         2) Count single-row-groups and calculate    #of_single_rows_with_target0 / #all_single_rows  ;  (and the same for target1)
      //TODO Introduce parameter for algorithm that will choose the way of calculating of the value that is being imputed.
      String denominatorIsZeroSubstitutionTerm;

      if(targetColumnName == null) { // When we calculating encodings for instances without target values.
        denominatorIsZeroSubstitutionTerm = String.format("%s", globalMeanForTargetClass);
      } else {
        int targetColumnIndex = fr.find(targetColumnName);
        double globalMeanForNonTargetClass = 1 - globalMeanForTargetClass;  // This is probably a bad idea to use frequencies for `0` class when we use frequencies for `1` class elsewhere
        denominatorIsZeroSubstitutionTerm = String.format("ifelse ( == (cols %s [%d]) 1) %f  %f", fr._key, targetColumnIndex, globalMeanForTargetClass, globalMeanForNonTargetClass);
      }
      return denominatorIsZeroSubstitutionTerm;
    }*/

    Frame addNoise(Frame fr, String applyToColumnName, double noiseLevel, long seed) {
      int appyToColumnIndex = fr.find(applyToColumnName);
      if (seed == -1) seed = new Random().nextLong();
      Vec zeroVec = Vec.makeZero(fr.numRows());
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
      int numeratorIndex = data.find("numerator");
      int denominatorIndex = data.find("denominator");
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
     * @param withBlendedAvg whether to apply blending or not.
     * @param noiseLevel amount of noise to add to the final encodings.
     * @param imputeNAsWithNewCategory set to `true` to impute NAs with new category.
     * @param seed we might want to specify particular values for reproducibility in tests.
     * @param isTrainOrValidSet `true` if we are applying target encoding to training/validation set and `false` for test set.
     * @return copy of the `data` frame with encodings
     */
    public Frame applyTargetEncoding(Frame data,
                                     String targetColumnName,
                                     Map<String, Frame> columnToEncodingMap,
                                     byte dataLeakageHandlingStrategy,
                                     String foldColumnName,
                                     boolean withBlendedAvg,
                                     double noiseLevel,
                                     boolean imputeNAsWithNewCategory,
                                     long seed,
                                     boolean isTrainOrValidSet) {

        if(noiseLevel < 0 )
            throw new IllegalStateException("`_noiseLevel` must be non-negative");

        Frame dataWithAllEncodings = null;
        try {

          dataWithAllEncodings = data.deepCopy(Key.make().toString());
          DKV.put(dataWithAllEncodings);

          if (isTrainOrValidSet) {
            ensureTargetColumnIsBinaryCategorical(dataWithAllEncodings, targetColumnName);
          }

          for (String teColumnName : _columnNamesToEncode) {

            imputeNAsForColumn(dataWithAllEncodings, teColumnName, teColumnName + "_NA");

            String newEncodedColumnName = teColumnName + "_te";

            Frame encodingMapForCurrentTEColumn = columnToEncodingMap.get(teColumnName);
            double priorMeanFromTrainingDataset = calculatePriorMean(encodingMapForCurrentTEColumn);

            int teColumnIndex = dataWithAllEncodings.find(teColumnName);

            switch (dataLeakageHandlingStrategy) {
              case DataLeakageHandlingStrategy.KFold:
                Frame holdoutEncodeMap = null;
                Frame dataWithMergedAggregationsK = null;
                try {
                  assert isTrainOrValidSet : "Following calculations assume we can access target column but we can do this only on training and validation sets.";

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

                      renameColumn(groupedByTEColumnAndAggregate, "sum_numerator", "numerator");
                      renameColumn(groupedByTEColumnAndAggregate, "sum_denominator", "denominator");

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

                  dataWithMergedAggregationsK = mergeByTEAndFoldColumns(dataWithAllEncodings, holdoutEncodeMap, teColumnIndex, foldColumnIndex, teColumnIndexInEncodingMap);

                  Frame withEncodingsFrameK = calculateEncoding(dataWithMergedAggregationsK, encodingMapForCurrentTEColumn, targetColumnName, newEncodedColumnName, withBlendedAvg);

                  Frame withAddedNoiseEncodingsFrameK = applyNoise(withEncodingsFrameK, newEncodedColumnName, noiseLevel, seed);

                  // Cases when we can introduce NA's:
                  // 1) if column is represented only in one fold then during computation of out-of-fold subsets we will get empty aggregations.
                  //   When merging with the original dataset we will get NA'a on the right side
                  // Note: since we create encoding based on training dataset and use KFold mainly when we apply encoding to the training set,
                  // there is zero probability that we haven't seen some category.
                  Frame imputedEncodingsFrameK = imputeWithMean(withAddedNoiseEncodingsFrameK, withAddedNoiseEncodingsFrameK.find(newEncodedColumnName), priorMeanFromTrainingDataset);

                  removeNumeratorAndDenominatorColumns(imputedEncodingsFrameK);

                  dataWithAllEncodings.delete();
                  dataWithAllEncodings = imputedEncodingsFrameK;

                } catch (Exception ex ) {
                  if (dataWithMergedAggregationsK != null) dataWithMergedAggregationsK.delete();
                  throw ex;
                } finally{
                  if (holdoutEncodeMap != null) holdoutEncodeMap.delete();
                }
                break;
              case DataLeakageHandlingStrategy.LeaveOneOut:
                Frame groupedTargetEncodingMap = null;
                Frame dataWithMergedAggregationsL = null;
                try {
                  assert isTrainOrValidSet : "Following calculations assume we can access target column but we can do this only on training and validation sets.";
                  foldColumnIsInEncodingMapCheck(foldColumnName, encodingMapForCurrentTEColumn);

                  groupedTargetEncodingMap = groupingIgnoringFordColumn(foldColumnName, encodingMapForCurrentTEColumn, teColumnName);

                  int teColumnIndexInGroupedEncodingMap = groupedTargetEncodingMap.find(teColumnName);
                  dataWithMergedAggregationsL = mergeByTEColumn(dataWithAllEncodings, groupedTargetEncodingMap, teColumnIndex, teColumnIndexInGroupedEncodingMap);

                  Frame subtractedFrameL = subtractTargetValueForLOO(dataWithMergedAggregationsL, targetColumnName);

                  Frame withEncodingsFrameL = calculateEncoding(subtractedFrameL, groupedTargetEncodingMap, targetColumnName, newEncodedColumnName, withBlendedAvg); // do we really need to pass groupedTargetEncodingMap again?

                  Frame withAddedNoiseEncodingsFrameL = applyNoise(withEncodingsFrameL, newEncodedColumnName, noiseLevel, seed);

                  // Cases when we can introduce NA's:
                  // 1) Only in case when our encoding map has not seen some category. //TODO move second parameter into the function
                  Frame imputedEncodingsFrameL = imputeWithMean(withAddedNoiseEncodingsFrameL, withAddedNoiseEncodingsFrameL.find(newEncodedColumnName), priorMeanFromTrainingDataset);

                  removeNumeratorAndDenominatorColumns(imputedEncodingsFrameL);

                  dataWithAllEncodings.delete();
                  dataWithAllEncodings = imputedEncodingsFrameL;

                } catch (Exception ex) {
                  if (dataWithMergedAggregationsL != null) dataWithMergedAggregationsL.delete();
                  throw ex;
                } finally {
                  if (groupedTargetEncodingMap != null) groupedTargetEncodingMap.delete();
                }

                break;
              case DataLeakageHandlingStrategy.None:
                Frame groupedTargetEncodingMapForNone = null;
                Frame dataWithMergedAggregationsN = null;
                try {
                  foldColumnIsInEncodingMapCheck(foldColumnName, encodingMapForCurrentTEColumn);
                  groupedTargetEncodingMapForNone = groupingIgnoringFordColumn(foldColumnName, encodingMapForCurrentTEColumn, teColumnName);
                  int teColumnIndexInGroupedEncodingMapNone = groupedTargetEncodingMapForNone.find(teColumnName);
                  dataWithMergedAggregationsN = mergeByTEColumn(dataWithAllEncodings, groupedTargetEncodingMapForNone, teColumnIndex, teColumnIndexInGroupedEncodingMapNone);

                  Frame withEncodingsFrameN = calculateEncoding(dataWithMergedAggregationsN, groupedTargetEncodingMapForNone, isTrainOrValidSet ? targetColumnName : null, newEncodedColumnName, withBlendedAvg);

                  Frame withAddedNoiseEncodingsFrameN = applyNoise(withEncodingsFrameN, newEncodedColumnName, noiseLevel, seed);
                  // In cases when encoding has not seen some levels we will impute NAs with mean computed from training set. Mean is a dataleakage btw.
                  // Note: In case of creating encoding map based on the holdout set we'd better use stratified sampling.
                  // Maybe even choose size of holdout taking into account size of the minimal set that represents all levels.
                  // Otherwise there are higher chances to get NA's for unseen categories.
                  Frame imputedEncodingsFrameN = imputeWithMean(withAddedNoiseEncodingsFrameN, withAddedNoiseEncodingsFrameN.find(newEncodedColumnName), priorMeanFromTrainingDataset);

                  removeNumeratorAndDenominatorColumns(imputedEncodingsFrameN);

                  dataWithAllEncodings.delete();
                  dataWithAllEncodings = imputedEncodingsFrameN;

                } catch (Exception ex) {
                  if (dataWithMergedAggregationsN != null) dataWithMergedAggregationsN.delete();
                  throw ex;
                } finally {
                  if (groupedTargetEncodingMapForNone != null) groupedTargetEncodingMapForNone.delete();
                }
            }
          }

          return dataWithAllEncodings;
        } catch (Exception ex) {
          if (dataWithAllEncodings != null) dataWithAllEncodings.delete();
          throw ex;
        }
    }

    Frame calculateEncoding(Frame preparedFrame, Frame encodingMap, String targetColumnName, String newEncodedColumnName, boolean withBlendedAvg) {
        if (withBlendedAvg) {
            return calculateAndAppendBlendedTEEncoding(preparedFrame, encodingMap, targetColumnName, newEncodedColumnName);
        } else {
            return calculateAndAppendTEEncoding(preparedFrame, encodingMap, targetColumnName, newEncodedColumnName);
        }
    }

    private Frame applyNoise(Frame frameWithEncodings, String newEncodedColumnName, double noiseLevel, long seed) {
        return noiseLevel > 0 ? addNoise(frameWithEncodings, newEncodedColumnName, noiseLevel, seed) : frameWithEncodings;
    }

    void removeNumeratorAndDenominatorColumns(Frame fr) {
        Vec removedNumeratorNone = fr.remove("numerator");
        removedNumeratorNone.remove();
        Vec removedDenominatorNone = fr.remove("denominator");
        removedDenominatorNone.remove();
    }

    void foldColumnIsInEncodingMapCheck(String foldColumnName, Frame targetEncodingMap) {
        if(foldColumnName == null && targetEncodingMap.names().length > 3) {
            throw new IllegalStateException("Passed along encoding map possibly contains fold column. Please provide fold column name so that it becomes possible to regroup (by ignoring folds).");
        }
    }

    Frame groupingIgnoringFordColumn(String foldColumnName, Frame targetEncodingMap, String teColumnName) {
        if (foldColumnName != null) {
            int teColumnIndex = targetEncodingMap.find(teColumnName);

            Frame newTargetEncodingMap = groupByTEColumnAndAggregate(targetEncodingMap, teColumnIndex);
            renameColumn(newTargetEncodingMap,  "sum_numerator", "numerator");
            renameColumn(newTargetEncodingMap,  "sum_denominator", "denominator");
            return newTargetEncodingMap;
        } else {
            Frame targetEncodingMapCopy = targetEncodingMap.deepCopy(Key.make().toString());
            DKV.put(targetEncodingMapCopy);
            return targetEncodingMapCopy;
        }
    }

    // Overloaded for the case when user had not specified the noise parameter
    public Frame applyTargetEncoding(Frame data,
                                     String targetColumnName,
                                     Map<String, Frame> targetEncodingMap,
                                     byte dataLeakageHandlingStrategy,
                                     String foldColumn,
                                     boolean withBlendedAvg,
                                     boolean imputeNAs,
                                     long seed,
                                     boolean isTrainOrValidSet) {
        double defaultNoiseLevel = 0.01;
        int targetIndex = data.find(targetColumnName);
        Vec targetVec = data.vec(targetIndex);
        double   noiseLevel = targetVec.isNumeric()  ?   defaultNoiseLevel * (targetVec.max() - targetVec.min()) : defaultNoiseLevel;
        return applyTargetEncoding(data, targetColumnName, targetEncodingMap, dataLeakageHandlingStrategy, foldColumn, withBlendedAvg, noiseLevel, true, seed, isTrainOrValidSet);
    }

    public Frame applyTargetEncoding(Frame data,
                                     String targetColumnName,
                                     Map<String, Frame> targetEncodingMap,
                                     byte dataLeakageHandlingStrategy,
                                     boolean withBlendedAvg,
                                     boolean imputeNAs,
                                     long seed,
                                     boolean isTrainOrValidSet) {
        return applyTargetEncoding(data, targetColumnName, targetEncodingMap, dataLeakageHandlingStrategy, null, withBlendedAvg, true, seed, isTrainOrValidSet);
    }

    public Frame applyTargetEncoding(Frame data,
                                     String targetColumnName,
                                     Map<String, Frame> targetEncodingMap,
                                     byte dataLeakageHandlingStrategy,
                                     boolean withBlendedAvg,
                                     double noiseLevel,
                                     boolean imputeNAs,
                                     long seed,
                                     boolean isTrainOrValidSet) {
        assert dataLeakageHandlingStrategy != DataLeakageHandlingStrategy.KFold : "Use another overloaded method for KFold dataLeakageHandlingStrategy.";
        return applyTargetEncoding(data, targetColumnName, targetEncodingMap, dataLeakageHandlingStrategy, null, withBlendedAvg, noiseLevel, true, seed, isTrainOrValidSet);
    }

}
