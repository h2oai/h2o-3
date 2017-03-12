package water.rapids.ast.prims.advmath;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.VecUtils;

import java.util.*;

import static water.util.RandomUtils.getRNG;

public class AstStratifiedSplit extends AstPrimitive {

  public static String TestTrainSplitColName = "test_train_split";
  public static String[] SplittingDom = new String[]{"train", "test"};

  @Override
  public String[] args() {
    return new String[]{"ary", "test_frac", "seed"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (h2o.random_stratified_split y test_frac seed)

  @Override
  public String str() {
    return "h2o.random_stratified_split";
  }


  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame origfr = stk.track(asts[1].exec(env)).getFrame();
    final double testFrac = asts[2].exec(env).getNum();
    long seed = (long) asts[3].exec(env).getNum();
    Key<Frame> inputFrKey = Key.make();
    Frame fr = origfr.deepCopy(inputFrKey.toString());
    Vec stratifyingColumn = fr.anyVec();
    if (fr.numCols() != 1)
      throw new IllegalArgumentException("Must give a single column to stratify against. Got: " + fr.numCols() + " columns.");
    Frame result = split(stratifyingColumn, testFrac, seed, SplittingDom);
    // clean up temp frames
    fr.delete();
    return new ValFrame(result);
  }

  public static Frame split(Vec stratifyingColumn, double splittingFraction, long randomizationSeed, String[] splittingDom) {
    checkIfCanStratifyBy(stratifyingColumn);
    randomizationSeed = randomizationSeed == -1 ? new Random().nextLong() : randomizationSeed;
    final long[] classes = new VecUtils.CollectDomain().doAll(stratifyingColumn).domain();
    final int nClass = stratifyingColumn.isNumeric() ? classes.length : stratifyingColumn.domain().length;
    // create frame with all 0s (default is train)
    Key<Frame> k1 = Key.make();
    Vec resVec = Vec.makeCon(0, stratifyingColumn.length());
    resVec.setDomain(splittingDom);
    Frame result = new Frame(k1, new String[]{TestTrainSplitColName}, new Vec[]{resVec});
    DKV.put(result);
    // create index frame
    ClassIdxTask finTask = new ClassIdxTask(nClass,classes).doAll(new Frame(stratifyingColumn));
    // loop through each class
    HashSet<Long> usedIdxs = new HashSet<>();
    for (int classLabel = 0; classLabel < nClass; classLabel++) {
      // extract frame with index locations of the minority class
      // calculate target number of this class to go to test
      final LongAry index = finTask.indexes[classLabel];
      long tnum = Math.max(Math.round(index.size() * splittingFraction), 1);

      HashSet<Long> tmpIdxs = new HashSet<>();
      // randomly select the target number of indexes
      int generated = 0;
      int count = 0;
      while (generated < tnum) {
        int i = (int) (getRNG(count+ randomizationSeed).nextDouble() * index.size());

        if (tmpIdxs.contains(index.get(i))) { count+=1;continue; }
        tmpIdxs.add(index.get(i));
        generated += 1;
        count += 1;
      }
      usedIdxs.addAll(tmpIdxs);
    }
    new ClassAssignMRTask(usedIdxs).doAll(result.anyVec());
    return result;
  }

  static void checkIfCanStratifyBy(Vec vec) {
    if (!(vec.isCategorical() || (vec.isNumeric() && vec.isInt())))
      throw new IllegalArgumentException("stratification only applies to integer and categorical columns. Got: " + vec.get_type_str());
  }

  public static class ClassAssignMRTask extends MRTask<AstStratifiedSplit.ClassAssignMRTask> {
    HashSet<Long> _idx;
    ClassAssignMRTask(HashSet<Long> idx) {
      _idx = idx;
    }
    @Override
    public void map(Chunk ck) {
      for (int i = 0; i<ck.len(); i++) {
        if (_idx.contains(ck.start() + i)) {
          ck.set(i,1.0);
        }
      }
    }

  }

  public static class ClassIdxTask extends MRTask<AstStratifiedSplit.ClassIdxTask> {
    LongAry[] indexes;
    int _nclasses;
    HashMap<Long, Integer> classMap; // it's Iced's bug that it does not take Map, needs HashMap

    ClassIdxTask(int nclasses, long[] classes) {
      classMap = new HashMap<>(classes.length);
      for (int i = 0; i < classes.length; i++) {
        classMap.put(classes[i], i);
      }
      _nclasses = nclasses;
    }

    @Override
    public void map(Chunk[] ck) {
      indexes = new LongAry[_nclasses];
      for (int i = 0; i < _nclasses; i++) { indexes[i] = new LongAry(); }
      for (int i = 0; i < ck[0].len(); i++) {
        long clas = ck[0].at8(i);
        Integer clas_idx = classMap.get(clas);
        if (clas_idx != null) indexes[clas_idx].add(ck[0].start() + i);
      }
    }
    @Override
    public void reduce(AstStratifiedSplit.ClassIdxTask c) {
      for (int i = 0; i < c.indexes.length; i++) {
        for (int j = 0; j < c.indexes[i].size(); j++) {
          indexes[i].add(c.indexes[i].get(j));
        }
      }
    }

  }
  public static class LongAry extends Iced<AstStratifiedSplit.LongAry>  {
    public LongAry(long ...vals){_ary = vals; _sz = vals.length;}
    long [] _ary = new long[4];
    int _sz;
    public void add(long i){
      if (_sz == _ary.length)
        _ary = Arrays.copyOf(_ary, Math.max(4, _ary.length * 2));
      _ary[_sz++] = i;
    }
    public long get(int i){
      if(i >= _sz) throw new ArrayIndexOutOfBoundsException(i);
      return _ary[i];
    }
    public int size(){return _sz;}
    public long[] toArray(){return Arrays.copyOf(_ary,_sz);}

    public void clear() {_sz = 0;}
  }
}
