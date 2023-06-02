package hex.isotonic;

import water.*;
import water.fvec.*;
import static water.rapids.Merge.sort;

import java.util.Arrays;

/**
 * Distributed implementation of Pool Adjacent Violators algorithm
 * for H2O Frames
 */
public class PoolAdjacentViolatorsDriver {

    public static Frame runPAV(Frame fr) { // y, X, w
        if (fr.numCols() != 3) {
            throw new IllegalArgumentException("Input frame is expected to have 3 columns: y, X, weights.");
        }
        if (fr.lastVec().min() < 0) {
            throw new IllegalArgumentException("Weights cannot be negative.");
        }
        Frame sorted = null;
        Frame local = null;
        Frame single = null;
        try {
            sorted = sort(fr, new int[]{1, 0});
            local = pav(sorted);
            single = RebalanceDataSet.toSingleChunk(local);
            return pav(single);
        } finally {
            Futures fs = new Futures();
            if (sorted != null)
                sorted.remove(fs);
            if (local != null)
                local.remove(fs);
            if (single != null)
                single.remove(fs);
            fs.blockForPending();
        }
    }

    static Frame pav(Frame fr) {
        return new PoolAdjacentViolatorsTask()
                .doAll(3, Vec.T_NUM, fr).outputFrame();
    } 
    
    static class PoolAdjacentViolatorsTask extends MRTask<PoolAdjacentViolatorsTask> {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            assert cs.length == 3;
            Chunk weightChunk = cs[2];
            int len = 0;
            int[] idx = new int[weightChunk._len];
            for (int i = 0; i < idx.length; i++) {
                if (weightChunk.isNA(i))
                    continue;
                double w = weightChunk.atd(i);
                if (w != 0) {
                    idx[len++] = i;
                }
            }
            idx = Arrays.copyOf(idx, len);
            double[] ys = cs[0].getDoubles(MemoryManager.malloc8d(len), idx);
            double[] xs = cs[1].getDoubles(MemoryManager.malloc8d(len), idx);
            double[] ws = cs[2].getDoubles(MemoryManager.malloc8d(len), idx);
            new PoolAdjacentViolators(ys, ws).findThresholds(xs, ncs);
        }
    }

}
