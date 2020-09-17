package ai.h2o.targetencoding;

import water.*;
import water.fvec.CategoricalWrappedVec;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.FillNAWithLongValueTask;
import water.fvec.task.FilterByValueTask;
import water.fvec.task.IsNotNaTask;
import water.fvec.task.UniqTask;
import water.logging.Logger;
import water.logging.LoggerFactory;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.prims.advmath.AstKFold;
import water.rapids.ast.prims.mungers.AstGroup;
import water.rapids.ast.prims.mungers.AstGroup.NAHandling;
import water.rapids.ast.prims.mungers.AstMelt;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValNum;
import water.rapids.vals.ValStr;
import water.rapids.vals.ValStrs;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.TwoDimTable;

import java.util.Random;

import static ai.h2o.targetencoding.EncodingsComponents.NO_TARGET_CLASS;

/**
 * This is a helper class for target encoding related logic,
 * grouping mainly distributed tasks or other utility functions needed to generate and apply the target encoding maps.
 *
 */
public class TargetEncoderHelper extends Iced<TargetEncoderHelper>{

  static String NUMERATOR_COL = "numerator";
  static String DENOMINATOR_COL = "denominator";
  static String TARGETCLASS_COL = "targetclass";

  static String NA_POSTFIX = "_NA";
  
  private static final Logger logger = LoggerFactory.getLogger(TargetEncoderHelper.class);

  private TargetEncoderHelper() {}

  /**
   * @param frame
   * @param name name of the fold column
   * @param nfolds number of folds
   * @param seed
   * @return the index of the new column
   */
  public static int addKFoldColumn(Frame frame, String name, int nfolds, long seed) {
    Vec foldVec = frame.anyVec().makeZero();
    frame.add(name, AstKFold.kfoldColumn(foldVec, nfolds, seed == -1 ? new Random().nextLong() : seed));
    return frame.numCols() - 1;
  }

  static double computePriorMean(Frame encodings) {
    assert encodings.find(TARGETCLASS_COL) < 0;
    return computePriorMean(encodings, NO_TARGET_CLASS);
  }
  
  static double computePriorMean(Frame encodings, int targetClass) {
    int tcIdx = encodings.find(TARGETCLASS_COL);
    assert (targetClass == NO_TARGET_CLASS) == (tcIdx < 0);
    Frame fr = null;
    try {
      fr = tcIdx < 0 ? encodings : filterByValue(encodings, tcIdx, targetClass);
      Vec numeratorVec = fr.vec(NUMERATOR_COL);
      Vec denominatorVec = fr.vec(DENOMINATOR_COL);
      assert numeratorVec!=null;
      assert denominatorVec!=null;
      return numeratorVec.mean() / denominatorVec.mean();
    } finally {
      if (fr != null && fr != encodings) fr.delete();
    }
  }

  /**
   * If a fold column is provided, this produces a frame of shape
   * (unique(col, fold_col), 4) with columns [{col}, {fold_col}, numerator, denominator]
   * Otherwise, it produces a frame of shape
   * (unique(col), 3) with columns [{col}, numerator, denominator]
   * @param fr
   * @param columnToEncodeIdx
   * @param targetIdx
   * @param foldColumnIdx
   * @param nclasses: 1 for regression, 2 for binary, N for multiclass.
   * @return the frame used to compute TE posteriors for a given column to encode.
   */
  static Frame buildEncodingsFrame(Frame fr, int columnToEncodeIdx, int targetIdx, int foldColumnIdx, int nclasses) {
    try {
      Scope.enter();

      Frame result;
      AstGroup.AGG[] aggs;
      int[] groupBy = foldColumnIdx < 0
              ? new int[]{columnToEncodeIdx}
              : new int[]{columnToEncodeIdx, foldColumnIdx};

      if (nclasses > 2) { // multiclass
        String targetName = fr.name(targetIdx);
        Vec targetVec = fr.vec(targetIdx);
        
        // transform the target into multiple columns that each will be interpreted as a target
        // used to generate the new {targetclass}_te features
        Frame targetFr = new Frame(new String[]{targetName}, new Vec[]{targetVec});
        Frame oheTarget = new FrameUtils.CategoricalOneHotEncoder(targetFr, new String[]{}).exec().get();
        Scope.track(oheTarget);
        Frame expandedFr = new Frame(fr).add(oheTarget);
//        printFrame(expandedFr);
        
        // add one sum aggregator per targetclass -> this will produce a {targetclass} numerator.
        // add one single nrow aggregator for the shared denominator.
        aggs = new AstGroup.AGG[oheTarget.numCols() + 1];
        for (int i = 0; i < oheTarget.numCols(); i++) {
          int partialTargetIdx = fr.numCols() + i;
          aggs[i] = new AstGroup.AGG(AstGroup.FCN.sum, partialTargetIdx, NAHandling.ALL, -1);
        }
        aggs[aggs.length - 1] = new AstGroup.AGG(AstGroup.FCN.nrow, targetIdx, NAHandling.ALL, -1);
        result = new AstGroup().performGroupingWithAggregations(expandedFr, groupBy, aggs).getFrame();
        Scope.track(result);
        // renaming all those aggregation columns:
        // targetclass numerators get temporarily renamed into just the targetclass.
        // the denominator column gets its final name.
        String[] targetVals = new String[oheTarget.numCols()];
        for (int i = 0; i < oheTarget.names().length; i++) {
          String oheCol = oheTarget.name(i);
          String targetVal = oheCol.replaceFirst(targetName + ".", "");
          renameColumn(result, "sum_" + oheCol, targetVal);
          targetVals[i] = targetVal;
        }
        renameColumn(result, "nrow", DENOMINATOR_COL);

        // we don't want to carry around all those numerator columns, 
        // so, melting them into a single numerator column + a targetclass column holding the corresponding target values.
        String[] idVars= foldColumnIdx < 0
                ? new String[]{fr.name(columnToEncodeIdx), DENOMINATOR_COL}
                : new String[]{fr.name(columnToEncodeIdx), fr.name(foldColumnIdx), DENOMINATOR_COL};
        result = melt(result, idVars, targetVals, TARGETCLASS_COL, NUMERATOR_COL, true);
        // convert targetclass column to ensure it has the same domain as target
        CategoricalWrappedVec.updateDomain(result.vec(TARGETCLASS_COL), targetVec.domain());
//        printFrame(result);
        
      } else { // works for both binary and regression
        aggs = new AstGroup.AGG[2];
        aggs[0] = new AstGroup.AGG(AstGroup.FCN.sum, targetIdx, NAHandling.ALL, -1);
        aggs[1] = new AstGroup.AGG(AstGroup.FCN.nrow, targetIdx, NAHandling.ALL, -1);
        result = new AstGroup().performGroupingWithAggregations(fr, groupBy, aggs).getFrame();
        // change the default column names assigned by the aggregation task
        renameColumn(result, "sum_" + fr.name(targetIdx), NUMERATOR_COL);
        renameColumn(result, "nrow", DENOMINATOR_COL);
//        printFrame(result);
      }

      Scope.untrack(result);
      return result;
    } finally {
      Scope.exit();
    }
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
    assert numeratorIdx >= 0;
    int denominatorIdx = encodingsFrame.find(DENOMINATOR_COL);
    assert denominatorIdx >= 0;
    int classesIdx = encodingsFrame.find(TARGETCLASS_COL);
    
    int [] groupBy = classesIdx < 0 
            ? new int[]{teColumnIdx}
            : new int[]{teColumnIdx, classesIdx};
    
    AstGroup.AGG[] aggs = new AstGroup.AGG[2];
    aggs[0] = new AstGroup.AGG(AstGroup.FCN.sum, numeratorIdx, NAHandling.ALL, -1);
    aggs[1] = new AstGroup.AGG(AstGroup.FCN.sum, denominatorIdx, NAHandling.ALL, -1);

    Frame result = new AstGroup().performGroupingWithAggregations(encodingsFrame, groupBy, aggs).getFrame();
    //change the default column names assigned by the aggregation task
    renameColumn(result, "sum_"+ NUMERATOR_COL, NUMERATOR_COL);
    renameColumn(result, "sum_"+ DENOMINATOR_COL, DENOMINATOR_COL);
    return result;
  }
  
  static Frame groupEncodingsByCategory(Frame encodingsFrame, int teColumnIdx, boolean hasFolds) {
    if (hasFolds) {
      return groupEncodingsByCategory(encodingsFrame, teColumnIdx);
    } else {
      return encodingsFrame.deepCopy(Key.make().toString());  // XXX: is this really necessary? 
    }
  }

  /** FIXME: this method is modifying the original fr column in-place, one of the reasons why we currently need a complete deep-copy of the training frame... */
  static void imputeCategoricalColumn(Frame data, int columnIdx, String naCategory) {
    Vec currentVec = data.vec(columnIdx);
    int indexForNACategory = currentVec.cardinality(); // Warn: Cardinality returns int but it could be larger than int for big datasets
    FillNAWithLongValueTask task = new FillNAWithLongValueTask(columnIdx, indexForNACategory);
    task.doAll(data);
    if (task._imputationHappened) {
      String[] oldDomain = currentVec.domain();
      String[] newDomain = new String[indexForNACategory + 1];
      System.arraycopy(oldDomain, 0, newDomain, 0, oldDomain.length);
      newDomain[indexForNACategory] = naCategory;
      updateColumnDomain(data, columnIdx, newDomain);
    }
  }
    
  private static void updateColumnDomain(Frame fr, int columnIdx, String[] domain) {
    fr.write_lock();
    Vec updatedVec = fr.vec(columnIdx);
//    CategoricalWrappedVec.updateDomain(updatedVec, domain); // safer? remapping should be unnecessary in our use-case though
    updatedVec.setDomain(domain);
    DKV.put(updatedVec);
    fr.update();
    fr.unlock();
  }

  static long[] getUniqueColumnValues(Frame data, int columnIndex) {
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
  static double getBlendedValue(double posteriorMean, double priorMean, long numberOfRowsForCategory, BlendingParams blendingParams) {
    double lambda = 1.0 / (1 + Math.exp((blendingParams.getInflectionPoint() - numberOfRowsForCategory) / blendingParams.getSmoothing()));
    return lambda * posteriorMean + (1 - lambda) * priorMean;
  }
  
  /** merge the encodings by TE column */
  static Frame mergeEncodings(Frame leftFrame, Frame encodingsFrame,
                               int leftTEColumnIdx, int encodingsTEColumnIdx) {
    return mergeEncodings(leftFrame, encodingsFrame, leftTEColumnIdx, -1, encodingsTEColumnIdx, -1, 0);
  }

  /** merge the encodings by TE column + fold column */
  static Frame mergeEncodings(Frame leftFrame, Frame encodingsFrame,
                               int leftTEColumnIdx, int leftFoldColumnIdx,
                               int encodingsTEColumnIdx, int encodingsFoldColumnIdx,
                               int maxFoldValue) {
    return TargetEncoderBroadcastJoin.join(
            leftFrame, new int[]{leftTEColumnIdx}, leftFoldColumnIdx,
            encodingsFrame, new int[]{encodingsTEColumnIdx}, encodingsFoldColumnIdx,
            maxFoldValue);
  }
  
  /**
   * 
   * @param fr the frame
   * @param newEncodedColumnName the new encoded column to compute and append to the original frame.
   * @param priorMean the global mean.
   * @param blendingParams if provided, those params are used to blend the prior and posterior values when calculating the encoded value.
   * @return the index of the new encoded column
   */
  static int applyEncodings(Frame fr, String newEncodedColumnName, double priorMean, final BlendingParams blendingParams) {
    int numeratorIdx = fr.find(NUMERATOR_COL);
    assert numeratorIdx >= 0;
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
        if (num.isNA(i) || den.isNA(i)) { // 2 cases: category unseen during training, or not present in a given fold, shouldn't we make the distinction?
          encoded.setNA(i);
        } else if (den.at8(i) == 0) { //should never happen according to BroadcastJoiner, except after substracting target in LOO strategy.
          if (logger.isDebugEnabled())
            logger.debug("Denominator is zero for column index = " + _encodedColIdx + ". Imputing with _priorMean = " + _priorMean);
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
  static void addNoise(Frame fr, int columnIdx, double noiseLevel, long seed) {
    if (seed == -1) seed = new Random().nextLong();
    Vec zeroVec = fr.anyVec().makeCon(0);
    Vec randomVec = zeroVec.makeRand(seed);
    try {
      fr.add("runIf", randomVec);
      int runifIdx = fr.numCols() - 1;
      new AddNoiseTask(columnIdx, runifIdx, noiseLevel).doAll(fr);
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

  /** 
   * FIXME: this method is modifying the original fr column in-place, one of the reasons why we currently need a complete deep-copy of the training frame...
   * 
   * @param fr the frame with a numerator and denominator columns, which will be modified based on the value in the target column.
   * @param targetColumn the name of the target column.
   * @param targetClass for regression use {@value EncodingsComponents#NO_TARGET_CLASS}, 
   *                    for classification this is the target value to match in order to decrement the numerator.
   */
  static void subtractTargetValueForLOO(Frame fr, String targetColumn, int targetClass) {
    int numeratorIndex = fr.find(NUMERATOR_COL);
    int denominatorIndex = fr.find(DENOMINATOR_COL);
    int targetIndex = fr.find(targetColumn);
    assert numeratorIndex >= 0;
    assert denominatorIndex >= 0;
    assert targetIndex >= 0;

    new SubtractCurrentRowForLeaveOneOutTask(numeratorIndex, denominatorIndex, targetIndex, targetClass).doAll(fr);
  }

  private static class SubtractCurrentRowForLeaveOneOutTask extends MRTask<SubtractCurrentRowForLeaveOneOutTask> {
    private int _numeratorIdx;
    private int _denominatorIdx;
    private int _targetIdx;
    private int _targetClass;

    public SubtractCurrentRowForLeaveOneOutTask(int numeratorIdx, int denominatorIdx, int targetIdx, int targetClass) {
      _numeratorIdx = numeratorIdx;
      _denominatorIdx = denominatorIdx;
      _targetIdx = targetIdx;
      _targetClass = targetClass;
    }

    @Override
    public void map(Chunk cs[]) {
      Chunk num = cs[_numeratorIdx];
      Chunk den = cs[_denominatorIdx];
      Chunk target = cs[_targetIdx];
      for (int i = 0; i < num._len; i++) {
        if (!target.isNA(i)) {
          double ti = target.atd(i);
          
          if (_targetClass == NO_TARGET_CLASS)  // regression
            num.set(i, num.atd(i) - target.atd(i));
          else if (_targetClass == ti)          // classification
            num.set(i, num.atd(i) - 1);
          
          den.set(i, den.atd(i) - 1);
        }
      }
    }
  }
  
  static Frame melt(Frame fr, String[] idVars, String[] valueVars, String varCol, String valueCol, boolean skipNA) {
    Frame melted = new AstMelt().exec(new Val[]{
            null,
            new ValFrame(fr),
            new ValStrs(idVars),
            new ValStrs(valueVars),
            new ValStr(varCol),
            new ValStr(valueCol),
            new ValNum(skipNA ? 1 : 0)
    }).getFrame();
    return register(melted);
  }

  static Frame rBind(Frame a, Frame b) {
    if (a == null) {
      assert b != null;
      return b;
    } else {
      String tree = String.format("(rbind %s %s)", a._key, b._key);
      return execRapidsAndGetFrame(tree);
    }
  }

  private static Frame execRapidsAndGetFrame(String astTree) {
    Val val = Rapids.exec(astTree);
    return register(val.getFrame());
  }

  /**
   * expand the frame with constant vector Frame
   * @return the index of the new vector.
   **/
  static int addCon(Frame fr, String newColumnName, long constant) {
    Vec constVec = fr.anyVec().makeCon(constant);
    fr.add(newColumnName, constVec);
    return fr.numCols() - 1;
  }

  /**
   * @return frame without rows with NAs in `columnIndex` column
   */
  static Frame filterOutNAsInColumn(Frame fr, int columnIndex) {
    Frame oneColumnFrame = new Frame(fr.vec(columnIndex));
    Frame noNaPredicateFrame = new IsNotNaTask().doAll(1, Vec.T_NUM, oneColumnFrame).outputFrame();
    Frame filtered = selectByPredicate(fr, noNaPredicateFrame);
    noNaPredicateFrame.delete();
    return filtered;
  }

  /**
   * @return frame with all the rows except for those whose value in the `columnIndex' column equals to `value`
   */
  static Frame filterNotByValue(Frame fr, int columnIndex, double value) {
    return filterByValueBase(fr, columnIndex, value, true);
  }

  /**
   * @return frame with all the rows whose value in the `columnIndex' column equals to `value`
   */
  static Frame filterByValue(Frame fr, int columnIndex, double value) {
    return filterByValueBase(fr, columnIndex, value,false);
  }

  private static Frame filterByValueBase(Frame fr, int columnIndex, double value, boolean isInverted) {
    Frame predicateFrame = new FilterByValueTask(value, isInverted).doAll(1, Vec.T_NUM, new Frame(fr.vec(columnIndex))).outputFrame();
    Frame filtered = selectByPredicate(fr, predicateFrame);
    predicateFrame.delete();
    return filtered;
  }

  private static Frame selectByPredicate(Frame fr, Frame predicateFrame) {
    Vec predicate = predicateFrame.anyVec();
    Vec[] vecs = ArrayUtils.append(fr.vecs(), predicate);
    return new Frame.DeepSelect().doAll(fr.types(), vecs).outputFrame(Key.make(), fr._names, fr.domains());
  }

  /** return a frame with unique values from the specified column */
  static Frame uniqueValuesBy(Frame fr, int columnIndex) {
    Vec vec0 = fr.vec(columnIndex);
    Vec v;
    if (vec0.isCategorical()) {
      v = Vec.makeSeq(0, vec0.domain().length, true);
      v.setDomain(vec0.domain());
      DKV.put(v);
    } else {
      UniqTask t = new UniqTask().doAll(vec0);
      int nUniq = t._uniq.size();
      final AstGroup.G[] uniq = t._uniq.keySet().toArray(new AstGroup.G[nUniq]);
      v = Vec.makeZero(nUniq, vec0.get_type());
      new MRTask() {
        @Override
        public void map(Chunk c) {
          int start = (int) c.start();
          for (int i = 0; i < c._len; ++i) c.set(i, uniq[i + start]._gs[0]);
        }
      }.doAll(v);
    }
    return new Frame(v);
  }

  static void renameColumn(Frame fr, int colIndex, String newName) {
    String[] newNames = fr.names();
    newNames[colIndex] = newName;
    fr.setNames(newNames);
  }

  static void renameColumn(Frame fr, String oldName, String newName) {
    renameColumn(fr, fr.find(oldName), newName);
  }

  /**
   * @return Frame that is registered in DKV
   */
  static Frame register(Frame frame) {
    frame._key = Key.make();
    DKV.put(frame);
    return frame;
  }
  
  static void printFrame(Frame fr) {
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) fr.numRows(), false);
    System.out.println(twoDimTable.toString(2, true));
  }

}
