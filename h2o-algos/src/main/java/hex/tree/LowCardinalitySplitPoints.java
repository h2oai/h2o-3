package hex.tree;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.util.IcedDouble;
import water.util.IcedHashSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
            assert isUniqueSequence(vals);
            splitPoints[frToTrain[i]] = vals;
        }
        return splitPoints;
    }

    static boolean isUniqueSequence(double[] seq) {
        if (seq.length == 1)
            return true;
        double lastValue = seq[0];
        for (int i = 1; i < seq.length; i++) {
            if (lastValue >= seq[i])
                return false;
            lastValue = seq[i];
        }
        return true;
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
        Set<IcedDouble> localValues = new HashSet<>(_maxCardinality);
        for (int col = 0; col < cs.length; col++) {
            localValues.clear();
            if (_values[col] == null)
                continue;
            Chunk c = cs[col];
            IcedDouble wrapper = new IcedDouble();
            for (int i = 0; i < c._len; i++) {
                double num = c.atd(i);
                if (Double.isNaN(num))
                    continue;
                if (wrapper._val == num)
                    continue;
                wrapper.setVal(num);
                if (localValues.add(wrapper)) {
                    if (localValues.size() > _maxCardinality) {
                        _values[col] = null;
                        break;
                    }
                    wrapper = new IcedDouble();
                }
            }
            merge(col, localValues);
        }
    }

    private void merge(int col, Collection<IcedDouble> localValues) {
        final Set<IcedDouble> allValues = _values[col];
        if (allValues == null)
            return;
        allValues.addAll(localValues);
        if (allValues.size() > _maxCardinality) {
            _values[col] = null;
        }
    }

    @Override
    public void reduce(LowCardinalitySplitPoints mrt) {
        if (mrt._values != _values) { // merging with a result from a different node
            for (int col = 0; col < _values.length; col++) {
                if (_values[col] == null || mrt._values[col] == null)
                    _values[col] = null;
                else {
                    merge(col, mrt._values[col]);
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
