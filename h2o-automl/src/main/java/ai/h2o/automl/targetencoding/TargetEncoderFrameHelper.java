package ai.h2o.automl.targetencoding;

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
  
  static IcedHashMap<String, Map<String, TargetEncoderModel.TEComponents>> convertEncodingMapFromFrameToMap(Map<String, Frame> encodingMap) {
    IcedHashMap<String, Map<String, TargetEncoderModel.TEComponents>> transformedEncodingMap = new IcedHashMap<>();
    Map<String, FrameToTETable> tasks = new HashMap<>();

    for (Map.Entry<String, Frame> entry : encodingMap.entrySet()) {

      Frame encodingsForParticularColumn = entry.getValue();
      FrameToTETable task = new FrameToTETable().dfork(encodingsForParticularColumn);

      tasks.put(entry.getKey(), task);
    }

    for (Map.Entry<String, FrameToTETable> taskEntry : tasks.entrySet()) {
      transformedEncodingMap.put(taskEntry.getKey(), taskEntry.getValue().getResult().table);
    }
    return transformedEncodingMap;
  }

  // TODO we probably don't need intermediate representation with TEComponents. Refactor
  public static Map<String, Map<String, int[]>> convertEncodingMapToMojoFormat(IcedHashMap<String, Map<String, TargetEncoderModel.TEComponents>> em) {

    IcedHashMap<String, Map<String, int[]>> transformedEncodingMap = null;

    transformedEncodingMap = new IcedHashMap<>();
    for (Map.Entry<String, Map<String, TargetEncoderModel.TEComponents>> entry : em.entrySet()) {
      String columnName = entry.getKey();
      Map<String, TargetEncoderModel.TEComponents> encodingsForParticularColumn = entry.getValue();
      Map<String, int[]> encodingsForColumnMap = new HashMap<>();
      for (Map.Entry<String, TargetEncoderModel.TEComponents> kv : encodingsForParticularColumn.entrySet()) {
        encodingsForColumnMap.put(kv.getKey(), kv.getValue().getNumeratorAndDenominator());
      }
      transformedEncodingMap.put(columnName, encodingsForColumnMap);
    }
    return transformedEncodingMap;
  }


  static class FrameToTETable extends MRTask<FrameToTETable> {
    IcedHashMap<String, TargetEncoderModel.TEComponents> table = new IcedHashMap<>();

    public FrameToTETable() { }

    @Override
    public void map(Chunk[] cs) {
      Chunk categoricalChunk = cs[0];
      String[] domain = categoricalChunk.vec().domain();
      int numRowsInChunk = categoricalChunk._len;
      // Note: we don't store fold column as we need only to be able to give predictions for data which is not encoded yet. 
      // We need folds only for the case when we applying TE to the frame which we are going to train our model on. 
      // But this is done once and then we don't need them anymore.
      for (int i = 0; i < numRowsInChunk; i++) {
        int[] numeratorAndDenominator = new int[2];
        numeratorAndDenominator[0] = (int) cs[1].at8(i);
        numeratorAndDenominator[1] = (int) cs[2].at8(i);
        int factor = (int) categoricalChunk.at8(i);
        String factorName = domain[factor];
        table.put(factorName, new TargetEncoderModel.TEComponents(numeratorAndDenominator));
      }
    }

    @Override
    public void reduce(FrameToTETable mrt) {
      table.putAll(mrt.table);
    }
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
