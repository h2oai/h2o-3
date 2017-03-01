package water.etl.prims.advmath;

import org.apache.commons.lang.ArrayUtils;
import water.DKV;
import water.Iced;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.VecUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static water.util.RandomUtils.getRNG;

public final class StratifiedSplit {
    private StratifiedSplit() {
    }
    public static Frame get(Frame sourceFr, String stratColName, double testFrac, long seed) {
        Vec stratCol = sourceFr.vec(stratColName);
        if (!(stratCol.isCategorical() || (stratCol.isNumeric() && stratCol.isInt())))
            throw new IllegalArgumentException("stratification only applies to integer and categorical columns. Got: " +stratCol.get_type_str());
        final long[] classes = new VecUtils.CollectDomain().doAll(stratCol).domain();
        final int nClass =stratCol.isNumeric() ? classes.length :stratCol.domain().length;
        final String[] domains =stratCol.domain();
        final long[] seeds = new long[nClass]; // seed for each regular fold column (one per class)
        for (int i = 0; i < nClass; ++i)
            seeds[i] = getRNG(seed + i).nextLong();
        String[] dom = new String[]{"train", "test"};
        // create sourceFrame with all 0s (default is train)
        Key<Frame> k1 = Key.make();
        //Vec resVec = Vec.makeCon(0,sourceFr.anyVec().length());
        Vec resVec = stratCol.makeCon(0);
        resVec.setDomain(new String[]{"train","test"});
        Frame result = new Frame(k1, new String[]{"test_train_split"}, new Vec[]{resVec});
        DKV.put(result);
        // create index sourceFrame
        ClassIdxTask finTask = new ClassIdxTask(nClass,classes).doAll(stratCol);
        // loop through each class
        HashSet<Long> usedIdxs = new HashSet<>();
        for (int classLabel = 0; classLabel < nClass; classLabel++) {
            // extract sourceFrame with index locations of the minority class
            // calculate target number of this class to go to test
            long tnum = Math.max(Math.round(finTask._iarray[classLabel].size() * testFrac), 1);

            HashSet<Long> tmpIdxs = new HashSet<>();
            // randomly select the target number of indexes
            int generated = 0;
            int count = 0;
            while (generated < tnum) {
                int i = (int) (getRNG(count+seed).nextDouble() * finTask._iarray[classLabel].size());
                if (tmpIdxs.contains(finTask._iarray[classLabel].get(i))) { count+=1;continue; }
                tmpIdxs.add(finTask._iarray[classLabel].get(i));
                generated += 1;
                count += 1;
            }
            usedIdxs.addAll(tmpIdxs);
        }
        new ClassAssignMRTask(usedIdxs).doAll(result.anyVec());
        // clean up temp sourceFrames
        return result;
    }
    private static class ClassAssignMRTask extends MRTask<StratifiedSplit.ClassAssignMRTask> {
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

    private static class ClassIdxTask extends MRTask<StratifiedSplit.ClassIdxTask> {
        LongAry[] _iarray;
        int _nclasses;
        ArrayList<Long> _classes;
        ClassIdxTask(int nclasses, long[] classes) {
            _nclasses = nclasses;
            Long[] boxed = ArrayUtils.toObject(classes);
            _classes = new ArrayList<Long>(Arrays.asList(boxed));
        }

        @Override
        public void map(Chunk ck) {
            _iarray = new LongAry[_nclasses];
            for (int i = 0; i < _nclasses; i++) { _iarray[i] = new LongAry(); }
            for (int i = 0; i < ck.len(); i++) {
                long clas = ck.at8(i);
                int clas_idx = _classes.indexOf(clas);
                    _iarray[clas_idx].add(ck.start() + i);
            }
        }
        @Override
        public void reduce(StratifiedSplit.ClassIdxTask c) {
            for (int i = 0; i < c._iarray.length; i++) {
                for (int j = 0; j < c._iarray[i].size(); j++) {
                    _iarray[i].add(c._iarray[i].get(j));
                }
            }
        }

    }
    private static class LongAry extends Iced<StratifiedSplit.LongAry> {
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

