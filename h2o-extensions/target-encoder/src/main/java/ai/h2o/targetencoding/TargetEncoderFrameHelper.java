package ai.h2o.targetencoding;

import hex.Model;
import hex.genmodel.algos.targetencoder.EncodingMap;
import hex.genmodel.algos.targetencoder.EncodingMaps;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.FilterByValueTask;
import water.fvec.task.IsNotNaTask;
import water.fvec.task.UniqTask;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.prims.advmath.AstKFold;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.IcedHashMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TargetEncoderFrameHelper {

  static public Frame rBind(Frame a, Frame b) {
    if(a == null) {
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

  /** @return the expanded with constant vector Frame, for flow-coding */
  static Frame addCon(Frame fr, String appendedColumnName, long constant ) { Vec constVec = fr.anyVec().makeCon(constant);fr.add(appendedColumnName, constVec); return fr; }

  /**
   * @return frame without rows with NAs in `columnIndex` column
   */
  static Frame filterOutNAsInColumn(Frame fr, int columnIndex) {
    Frame oneColumnFrame = new Frame(fr.vec(columnIndex));
    Frame noNaPredicateFrame = new IsNotNaTask().doAll(1, Vec.T_NUM, oneColumnFrame).outputFrame();
    return selectByPredicate(fr, noNaPredicateFrame);
  }

  /**
   * @return frame with all the rows except for those whose value in the `columnIndex' column equals to `value`
   */
  public static Frame filterNotByValue(Frame fr, int columnIndex, double value) {
    return filterByValueBase(fr, columnIndex, value, true);
  }

  /**
   * @return frame with all the rows whose value in the `columnIndex' column equals to `value`
   */
  public static Frame filterByValue(Frame fr,int columnIndex, double value) {
    return filterByValueBase(fr, columnIndex, value,false);
  }

  private static Frame filterByValueBase(Frame fr, int columnIndex, double value, boolean isInverted) {
    Frame predicateFrame = new FilterByValueTask(value, isInverted).doAll(1, Vec.T_NUM, new Frame(fr.vec(columnIndex))).outputFrame();
    return selectByPredicate(fr, predicateFrame);
  }

  private static Frame selectByPredicate(Frame fr, Frame predicateFrame) {
    String[] names = fr.names().clone();
    byte[] types = fr.types().clone();
    String[][] domains = fr.domains().clone();

    fr.add("predicate", predicateFrame.anyVec());
    Frame filtered = new Frame.DeepSelect().doAll(types, fr).outputFrame(Key.<Frame>make(), names, domains);
    predicateFrame.delete();
    fr.remove("predicate");
    return filtered;
  }

  /** return a frame with unique values from the specified column */
  static Frame uniqueValuesBy(Frame fr, int columnIndex) {
    Vec vec0 = fr.vec(columnIndex);
    Vec v;
    if (vec0.isCategorical()) {
      v = Vec.makeSeq(0, (long) vec0.domain().length, true);
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

  static Frame renameColumn(Frame fr, int indexOfColumnToRename, String newName) {
    String[] updatedtNames = fr.names();
    updatedtNames[indexOfColumnToRename] = newName;
    fr.setNames(updatedtNames);
    return fr;
  }

  static Frame renameColumn(Frame fr, String oldName, String newName) {
    return renameColumn(fr, fr.find(oldName), newName);
  }

  public static Frame[] splitByValue(Frame fr,int columnIndex, double value) {
    return new Frame[] { filterByValue(fr, columnIndex, value), filterNotByValue(fr, columnIndex, value)};
  }

  public static Frame[] splitByValue(Frame fr, String foldColumnName, double value) {
    int indexOfTheFoldColumn = fr.find(foldColumnName);
    return new Frame[] { filterByValue(fr, indexOfTheFoldColumn, value), filterNotByValue(fr, indexOfTheFoldColumn, value)};
  }

  //Note: It could be a good thing to have this method in Frame's API.
  public static Frame factorColumn(Frame fr, String columnName) {
    int columnIndex = fr.find(columnName);
    Vec vec = fr.vec(columnIndex);
    fr.replace(columnIndex, vec.toCategoricalVec());
    vec.remove();
    return fr;
  }

  static String[] getEncodedColumnNames(String[] origColumns) {
    int index = 0;
    for(String columnName : origColumns) {
      origColumns[index] = columnName + "_te";
      index++;
    }
    return origColumns;
  }

  public static <T> T[] concat(T[] first, T[] second) {
    if(first == null) return second;
    if(second == null) return first;
    T[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  /**
   * @param frame
   * @param name name of the fold column
   * @param nfolds number of folds
   * @param seed
   */
  public static Frame addKFoldColumn(Frame frame, String name, int nfolds, long seed) {
    Vec foldVec = frame.anyVec().makeZero();
    frame.add(name, AstKFold.kfoldColumn(foldVec, nfolds, seed == -1 ? new Random().nextLong() : seed));
    return frame;
  }


  /**
   * @param frame
   * @param name name of the fold column
   * @param nfolds number of folds
   * @param seed
   */
  static public Frame addKFoldColumn(Model.Parameters.FoldAssignmentScheme fold_assignment, Frame frame, String name, int nfolds, String responseColumnName, long seed) {
    Vec foldAssignments = null;
    switch(fold_assignment ) {
      case AUTO:
      case Random:     foldAssignments = AstKFold.          kfoldColumn(frame.anyVec().makeZero(), nfolds,seed);
        break;
      case Modulo:     foldAssignments = AstKFold.    moduloKfoldColumn(frame.anyVec().makeZero(), nfolds     );
        break;
      case Stratified: foldAssignments = AstKFold.stratifiedKFoldColumn(frame.vec(responseColumnName), nfolds, seed);
        break;
      default:         throw H2O.unimpl();
    }
    frame.add(name, foldAssignments);
    return frame;
  }
  
  static EncodingMaps convertEncodingMapFromFrameToMap(Map<String, Frame> encodingMap) {
    EncodingMaps convertedEncodingMap = new EncodingMaps();
    Map<String, FrameToTETableTask> tasks = new HashMap<>();

    for (Map.Entry<String, Frame> entry : encodingMap.entrySet()) {

      Frame encodingsForParticularColumn = entry.getValue();
      FrameToTETableTask task = new FrameToTETableTask().dfork(encodingsForParticularColumn);
      tasks.put(entry.getKey(), task);
    }

    for (Map.Entry<String, FrameToTETableTask> taskEntry : tasks.entrySet()) {
      FrameToTETableTask taskEntryValue = taskEntry.getValue();
      IcedHashMap<String, TEComponents> table = taskEntryValue.getResult()._table;
      convertEncodingMapToGenModelFormat(convertedEncodingMap, taskEntry.getKey(), table);
      Scope.track(taskEntryValue._fr);
    }
    
    return convertedEncodingMap;
  }

  /**
   * Note: We can't use the same class for {numerator, denominator} in both `h2o-genmodel` and `h2o-automl` as we need it to be extended 
   * from Iced in `h2o-automl` to make it serializable to distribute MRTasks and we can't use this Iced class from `h2o-genmodel` module 
   * as there is no dependency between modules in this direction 
   * 
   * @param convertedEncodingMap the Map we will put our converted encodings into
   * @param encodingMap encoding map for `teColumn`
   */
  private static void convertEncodingMapToGenModelFormat(EncodingMaps convertedEncodingMap, String teColumn, IcedHashMap<String, TEComponents> encodingMap) {
    Map<Integer, int[]> tableGenModelFormat = new HashMap<>();
    for(Map.Entry<String, TEComponents> entry : encodingMap.entrySet()) {
      TEComponents value = entry.getValue();
      tableGenModelFormat.put(Integer.parseInt(entry.getKey()), new int[] {value.getNumerator(), value.getDenominator()});
    }
    convertedEncodingMap.put(teColumn, new EncodingMap(tableGenModelFormat));
  }

  /**
   * @return Frame that is registered in DKV
   */
  public static Frame register(Frame frame) {
    frame._key = Key.make();
    DKV.put(frame);
    return frame;
  }

  public static void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }

}
