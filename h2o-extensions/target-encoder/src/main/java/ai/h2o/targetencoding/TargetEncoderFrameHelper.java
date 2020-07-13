package ai.h2o.targetencoding;

import hex.genmodel.algos.targetencoder.EncodingMap;
import hex.genmodel.algos.targetencoder.EncodingMaps;
import water.DKV;
import water.Key;
import water.MRTask;
import water.Scope;
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
import water.util.ArrayUtils;
import water.util.IcedHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TargetEncoderFrameHelper {

  static public Frame rBind(Frame a, Frame b) {
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
  static int addCon(Frame fr, String appendedColumnName, long constant ) { 
    Vec constVec = fr.anyVec().makeCon(constant);
    fr.add(appendedColumnName, constVec); 
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
  static Frame filterByValue(Frame fr,int columnIndex, double value) {
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
  
  static EncodingMaps convertEncodingMapValues(Map<String, Frame> encodingMap) {
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
    Map<Integer, double[]> tableGenModelFormat = new HashMap<>();
    for (Map.Entry<String, TEComponents> entry : encodingMap.entrySet()) {
      TEComponents value = entry.getValue();
      tableGenModelFormat.put(Integer.parseInt(entry.getKey()), new double[] {value.getNumerator(), value.getDenominator()});
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
    if (encodingMap == null) return;
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }

}
