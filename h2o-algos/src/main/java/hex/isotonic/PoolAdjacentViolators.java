package hex.isotonic;

import water.fvec.NewChunk;
import water.util.ArrayUtils;

/**
 * Implements Pool Adjacent Violators algorithm suitable for parallelization
 * using <a href="https://link.springer.com/chapter/10.1007/978-3-642-99789-1_10">An Approach to Parallelizing Isotonic Regression</a>.
 * 
 * Loosely follows <a href="https://github.com/apache/spark/blob/a2c7b2133cfee7fa9abfaa2bfbfb637155466783/mllib/src/main/scala/org/apache/spark/mllib/regression/IsotonicRegression.scala">Spark implementation</a>
 */
class PoolAdjacentViolators {
    /**
     * Block weights
     */
    private final double[] _ws;
    /**
     * mean target/response in the block 
     */
    private final double[] _wYs;
    /**
     * Block metadata:
     * - there are 2 types of blocks - unmerged and merged
     * - for unmerged:
     *      for i (= start of the block):
     *      _blocks[i] species where the given block ends (inclusive)
     *      initially blocks are singletons [i, i], meaning _blocks[i] = i
     *      _blocks[i] + 1 is thus beginning of the next block
     * - for merged:
     *      for i (= current end of the block)
     *      _blocks[i] is a negative reference to the original start of the block
     *      (- _blocks[i - 1] - 1) maps to the current start of the block
     *      allows us to (quickly) walk backwards - get reference to the previous block
     *      we are using negative numbers for debugging purposes (we can quickly see which
     *      blocks were merged)
     */
    private final int[] _blocks;

    PoolAdjacentViolators(double[] ys) {
        this(ys, null);
    }
    
    public PoolAdjacentViolators(double[] ys, double[] ws) {
        _ws = ws != null ? 
                ws.clone() // make a copy - we will modify the weights
                :
                ArrayUtils.constAry(ys.length, 1.0);
        _wYs = new double[_ws.length];
        for (int i = 0; i < _ws.length; i++) {
            _wYs[i] = _ws[i] * ys[i];
        }
        _blocks = ArrayUtils.seq(0, ys.length);
    }

    void findThresholds(double[] xs, NewChunk[] ncs) {
        findThresholds(xs, ncs[0], ncs[1], ncs[2]);
    }
    
    void findThresholds(double[] xs, NewChunk outYs, NewChunk outXs, NewChunk outWs) {
        mergeViolators();
        outputThresholds(xs, outYs, outXs, outWs);
    }

    void mergeViolators() {
        for (int block = 0; next(block) < _blocks.length; ) {
            if (meanY(block) >= meanY(next(block))) {
                mergeWithNext(block);
                while ((block > 0) && (meanY(prev(block)) >= meanY(block))) {
                    block = prev(block);
                    mergeWithNext(block);
                }
            } else {
                block = next(block);
            }
        }
    }

    void outputThresholds(double[] xs, NewChunk outYs, NewChunk outXs, NewChunk outWs) {
        for (int i = 0; i < xs.length; i = next(i)) {
            if (xs[_blocks[i]] > xs[i]) {
                outYs.addNum(meanY(i));
                outXs.addNum(xs[i]);
                outWs.addNum(_ws[i] / 2);

                outYs.addNum(meanY(i));
                outXs.addNum(xs[_blocks[i]]);
                outWs.addNum(_ws[i] / 2);
            } else {
                outYs.addNum(meanY(i));
                outXs.addNum(xs[i]);
                outWs.addNum(_ws[i]);
            }
        }
    }
    
    int next(int b) {
        return _blocks[b] + 1;
    }

    int prev(int b) {
        if (_blocks[b - 1] == b - 1) // unmerged singleton block
            return b - 1;
        int ref = _blocks[b - 1];
        if (ref >= 0)
            throw new IllegalStateException("Block representation is broken, " +
                    "expected a negative encoded block reference, instead got: " + ref + " for block " + b + ".");
        return -ref-1;
    }

    void mergeWithNext(int b1) {
        final int b2 = _blocks[b1] + 1;
        _blocks[b1] = _blocks[b2];
        _blocks[_blocks[b2]] = -b1-1;
        _ws[b1] += _ws[b2];
        _wYs[b1] += _wYs[b2]; 
    }

    double meanY(int b) {
        return _wYs[b] / _ws[b];
    }
}
