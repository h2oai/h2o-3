package ai.h2o.automl.targetencoding;

import hex.genmodel.algos.targetencoder.EncodingMap;
import hex.genmodel.algos.targetencoder.EncodingMaps;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.task.FilterByValueTask;
import water.fvec.task.IsNotNaTask;
import water.fvec.task.UniqTask;
import water.rapids.ast.prims.advmath.AstKFold;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.IcedHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TargetEncoderFrameHelper {

  /** @return the expanded with constant vector Frame, for flow-coding */
  static Frame addCon(Frame fr, String appendedColumnName, long constant ) { fr.add(appendedColumnName, Vec.makeCon(constant, fr.numRows(), Vec.T_NUM)); return fr; }

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
  
  static EncodingMaps convertEncodingMapFromFrameToMap(Map<String, Frame> encodingMap) {
    EncodingMaps convertedEncodingMap = new EncodingMaps();
    Map<String, FrameToTETableTask> tasks = new HashMap<>();

    for (Map.Entry<String, Frame> entry : encodingMap.entrySet()) {

      Frame encodingsForParticularColumn = entry.getValue();
      FrameToTETableTask task = new FrameToTETableTask().doAll(encodingsForParticularColumn);
      tasks.put(entry.getKey(), task);
    }

    for (Map.Entry<String, FrameToTETableTask> taskEntry : tasks.entrySet()) {
      IcedHashMap<String, TEComponents> table = taskEntry.getValue().getResult()._table;
      convertEncodingMapToGenModelFormat(convertedEncodingMap, taskEntry.getKey(), table);
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
