package water.rapids;

import water.DKV;
import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.VecUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import static water.util.RandomUtils.getRNG;

public class StratifiedSplit {

  public static Vec split(Vec stratifyingColumn, double splittingFraction, long randomizationSeed, String[] splittingDom) {
    checkIfCanStratifyBy(stratifyingColumn);
    randomizationSeed = randomizationSeed == -1 ? new Random().nextLong() : randomizationSeed;
    // Collect input vector domain
    final long[] classes = new VecUtils.CollectIntegerDomain().doAll(stratifyingColumn).domain();
    // Number of output classes
    final int numClasses = classes.length;

    // Make a new column based on input column - this needs to follow layout of input vector!
    // Save vector into DKV
    Vec outputVec = stratifyingColumn.makeCon(0.0, Vec.T_CAT);
    outputVec.setDomain(splittingDom);
    DKV.put(outputVec);

    // Collect index frame
    // FIXME: This is in fact collecting inverse index class -> {row indices}
    ClassIdxTask finTask = new ClassIdxTask(numClasses,classes).doAll(stratifyingColumn);
    // Loop through each class in the input column
    HashSet<Long> usedIdxs = new HashSet<>();
    for (int classLabel = 0; classLabel < numClasses; classLabel++) {
      // extract frame with index locations of the minority class
      // calculate target number of this class to go to test
      final LongAry indexAry = finTask._indexes[classLabel];
      long tnum = Math.max(Math.round(indexAry.size() * splittingFraction), 1);

      HashSet<Long> tmpIdxs = new HashSet<>();
      // randomly select the target number of indexes
      int generated = 0;
      int count = 0;
      while (generated < tnum) {
        int i = (int) (getRNG(count+ randomizationSeed).nextDouble() * indexAry.size());

        if (tmpIdxs.contains(indexAry.get(i))) { count+=1;continue; }
        tmpIdxs.add(indexAry.get(i));
        generated += 1;
        count += 1;
      }
      usedIdxs.addAll(tmpIdxs);
    }
    // Update class assignments
    new ClassAssignMRTask(usedIdxs).doAll(outputVec);
    return outputVec;
  }


  static void checkIfCanStratifyBy(Vec vec) {
    if (!(vec.isCategorical() || (vec.isNumeric() && vec.isInt())))
      throw new IllegalArgumentException("Stratification only applies to integer and categorical columns. Got: " + vec.get_type_str());
    if (vec.length() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Cannot stratified the frame because it is too long: nrows=" + vec.length());
    }
  }

  public static class ClassAssignMRTask extends MRTask<ClassAssignMRTask> {
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
      _idx = null; // Do not send it back
    }
  }

  public static class ClassIdxTask extends MRTask<ClassIdxTask> {
    LongAry[] _indexes;
    private final int _nclasses;
    private long[] _classes;
    private transient HashMap<Long, Integer> _classMap;

    public ClassIdxTask(int nclasses, long[] classes) {
      _nclasses = nclasses;
      _classes = classes;
    }

    @Override
    protected void setupLocal() {
      _classMap = new HashMap<>(2*_classes.length);
      for (int i = 0; i < _classes.length; i++) {
        _classMap.put(_classes[i], i);
      }
    }

    @Override
    public void map(Chunk[] ck) {
      _indexes = new LongAry[_nclasses];
      for (int i = 0; i < _nclasses; i++) { _indexes[i] = new LongAry(); }
      for (int i = 0; i < ck[0].len(); i++) {
        long clas = ck[0].at8(i);
        Integer clas_idx = _classMap.get(clas);
        if (clas_idx != null) _indexes[clas_idx].add(ck[0].start() + i);
      }
      _classes = null;
    }
    @Override
    public void reduce(ClassIdxTask c) {
      for (int i = 0; i < c._indexes.length; i++) {
        for (int j = 0; j < c._indexes[i].size(); j++) {
          _indexes[i].add(c._indexes[i].get(j));
        }
      }
    }

  }
  
  public static class LongAry extends Iced<LongAry> {
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
