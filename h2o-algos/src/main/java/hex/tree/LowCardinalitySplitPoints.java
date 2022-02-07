package hex.tree;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.IcedDouble;
import water.util.IcedHashSet;

import java.util.Arrays;

public class LowCardinalitySplitPoints extends MRTask<LowCardinalitySplitPoints> {

    private final int _maxCardinality;
    private final IcedHashSet<IcedDouble>[] _values;

    static double[][] calculateLowCardinalitySplitPoints(Frame trainFr, int maxCardinality) {
        final Frame fr = new Frame();
        final int[] frToTrain = new int[trainFr.numCols()];
        for (int i = 0; i < trainFr.numCols(); ++i) {
            if (!trainFr.vec(i).isNumeric() || trainFr.vec(i).isCategorical() ||
                    trainFr.vec(i).isBinary() || trainFr.vec(i).isConst()) {
                continue;
            }
            frToTrain[fr.numCols()] = i;
            fr.add(trainFr.name(i), trainFr.vec(i));
        }
        IcedHashSet<IcedDouble>[] values = new LowCardinalitySplitPoints(maxCardinality, fr.numCols())
                .doAll(fr)._values;
        double[][] splitPoints = new double[trainFr.numCols()][];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                continue;
            }
            double[] vals = new double[values[i].size()];
            int valsSize = 0;
            for (IcedDouble wrapper : values[i]) {
                vals[valsSize++] = wrapper._val;
            }
            assert valsSize == vals.length;
            Arrays.sort(vals);
            splitPoints[frToTrain[i]] = vals;
        }
        return splitPoints;
    }
        
    @SuppressWarnings("unchecked")
    private LowCardinalitySplitPoints(int maxCardinality, int nCols) {
        _maxCardinality = maxCardinality;
        _values = new IcedHashSet[nCols];
        for (int i = 0; i < _values.length; i++) {
            _values[i] = new IcedHashSet<>();
        }
    }

    @Override
    public void map(Chunk[] cs) {
        for (int col = 0; col < cs.length; col++) {
            IcedHashSet<IcedDouble> values = _values[col];
            if (values == null)
                continue;
            Chunk c = cs[col];
            IcedDouble wrapper = new IcedDouble();
            for (int i = 0; i < c._len; i++) {
                if (c.isNA(i))
                    continue;
                double num = c.atd(i);
                if (wrapper._val == num)
                    continue;
                wrapper.setVal(num);
                if (values.add(wrapper)) {
                    if (values.size() > _maxCardinality) {
                        _values[col] = null;
                        break;
                    }
                    wrapper = new IcedDouble();
                }
            }
        }
    }

    @Override
    public void reduce(LowCardinalitySplitPoints mrt) {
        if (mrt._values != _values) {
            for (int col = 0; col < _values.length; col++) {
                if (_values[col] == null || mrt._values[col] == null)
                    _values[col] = null;
                else {
                    _values[col].addAll(mrt._values[col]);
                    if (_values[col].size() > _maxCardinality) {
                        _values[col] = null;
                    }
                }
            }
        } // else: nothing to do on the same node
    }

    @Override
    protected void postGlobal() {
        for (int col = 0; col < _values.length; col++) {
            if (_values[col] != null && _values[col].size() > _maxCardinality) {
                _values[col] = null;
            }
        }
    }
}
