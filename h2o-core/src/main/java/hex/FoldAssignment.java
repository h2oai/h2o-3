package hex;

import water.Iced;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.TransformWrappedVec;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.VecUtils;

import java.util.Arrays;

public class FoldAssignment extends Iced<FoldAssignment> {
    protected final Vec _fold;

    FoldAssignment(Vec fold) {
        _fold = fold;
    }

    Frame toFrame(Key<Frame> key) {
        return new Frame(key, new String[]{"fold_assignment"}, new Vec[]{_fold});
    }

    Vec getAdaptedFold() {
        return _fold;
    }

    void remove(boolean keepOriginalFold) {
        if (!keepOriginalFold)
            _fold.remove();
    }

    static FoldAssignment fromUserFoldSpecification(int N, Vec fold) {
        int[] foldValues = findActualFoldValues(fold);
        if ( ! (fold.isCategorical() 
                 || (fold.isInt()
                      && foldValues.length == N // No holes in the sequence
                      && ((fold.min() == 0 && fold.max() == N-1) || (fold.min() == 1 && fold.max() == N)))) ) // Allow 0 to N-1, or 1 to N
            throw new H2OIllegalArgumentException("Fold column must be either categorical or contiguous integers from 0..N-1 or 1..N");

        return new TransformFoldAssignment(fold, foldValues);
    }

    static FoldAssignment fromInternalFold(int N, Vec fold) {
        assert fold.isInt();
        assert fold.min() == 0 && fold.max() == N-1;
        return new FoldAssignment(fold);
    }

    static int nFoldWork(Vec fold) {
        return findActualFoldValues(fold).length;
    }

    /**
     * For a given fold Vec finds the actual used fold values (only used levels).
     * 
     * @param f input Vec
     * @return indices of the used domain levels (for categorical fold) or the used values (for a numerical fold) 
     */
    static int[] findActualFoldValues(Vec f) {
        Vec fc = VecUtils.toCategoricalVec(f);
        final String[] actualDomain;
        try {
            if (!f.isCategorical()) {
                actualDomain = fc.domain();
            } else {
                actualDomain = VecUtils.collectDomainFast(fc);
            }
        } finally {
            fc.remove();
        }
        int N = actualDomain.length;
        if (Arrays.equals(actualDomain, fc.domain())) {
            int offset = f.isCategorical() ? 0 : (int) f.min();
            return ArrayUtils.seq(offset, N + offset);
        } else {
            int[] mapping = new int[N];
            String[] fullDomain = fc.domain();
            for (int i = 0; i < N; i++) {
                int pos = ArrayUtils.find(fullDomain, actualDomain[i]);
                assert pos >= 0;
                mapping[i] = pos;
            }
            return mapping;
        }
    }

}

class TransformFoldAssignment extends FoldAssignment {
    private final Vec _adaptedFold;
    
    TransformFoldAssignment(Vec fold, int[] usedFoldValues) {
        super(fold);
        _adaptedFold = makeAdaptedFold(usedFoldValues);
    }

    Vec getAdaptedFold() {
        return _adaptedFold;
    }

    final Vec makeAdaptedFold(int[] usedFoldValues) {
        int[] foldValuesToFoldIndices = foldValuesToFoldIndices(usedFoldValues);
        return new TransformWrappedVec(new Vec[]{_fold}, new MappingTransformFactory(foldValuesToFoldIndices));
    }

    static int[] foldValuesToFoldIndices(int[] usedFoldValues) {
        int max = ArrayUtils.maxValue(usedFoldValues);
        final int[] valueToFoldIndex = new int[max + 1];
        Arrays.fill(valueToFoldIndex, -1);
        for (int i = 0; i < usedFoldValues.length; i++) {
            valueToFoldIndex[usedFoldValues[i]] = i;
        }
        return valueToFoldIndex;
    }

    @Override
    void remove(boolean keepOriginalFold) {
        _adaptedFold.remove();
    }

}

class MappingTransformFactory extends Iced<MappingTransformFactory> 
        implements TransformWrappedVec.TransformFactory<MappingTransformFactory> {
    final int[] _mapping;

    public MappingTransformFactory(int[] mapping) {
        _mapping = mapping;
    }

    @Override
    public TransformWrappedVec.Transform create(int n_inputs) {
        assert n_inputs == 1;
        return new TransformWrappedVec.Function1DTransform() {
            @Override
            public double apply(double x) {
                return _mapping[(int) x];
            }
        };
    }
}
