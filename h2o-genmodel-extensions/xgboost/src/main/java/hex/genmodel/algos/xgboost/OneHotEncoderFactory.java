package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.util.FVec;
import hex.genmodel.GenModel;

import java.io.Serializable;

class OneHotEncoderFactory implements Serializable {

    private boolean _compatible10;
    private final boolean _sparse;
    private final int[] _catOffsets;
    private final int _cats;
    private final int _nums;
    private final boolean _useAllFactorLevels;
    private final int[] _catMap;
    private final float _notHot;

    OneHotEncoderFactory(boolean compatible10, boolean sparse, int[] catOffsets, int cats, int nums, boolean useAllFactorLevels) {
        _compatible10 = compatible10;
        _sparse = sparse;
        _catOffsets = catOffsets;
        _cats = cats;
        _nums = nums;
        _useAllFactorLevels = useAllFactorLevels;
        _notHot = _sparse ? Float.NaN : 0;
        if (_catOffsets == null) {
            _catMap = new int[0];
        } else {
            _catMap = new int[_catOffsets[_cats]];
            for (int c = 0; c < _cats; c++) {
                for (int j = _catOffsets[c]; j < _catOffsets[c+1]; j++)
                    _catMap[j] = c;
            }
        }
    }

    FVec fromArray(double[] input) {
        float[] numValues = new float[_nums];
        int[] catValues = new int[_cats];
        GenModel.setCats(input, catValues, _cats, _catOffsets, _useAllFactorLevels);
        for (int i = 0; i < numValues.length; i++) {
            float val = (float) input[_cats + i];
            numValues[i] = _sparse && (val == 0) ? Float.NaN : val;
        }

        if (_compatible10) {
            return new OneHotEncoderFVecCompatible10(catValues, numValues);
        } else {
            return new DefaultOneHotEncoderFVec(catValues, numValues);
        }
    }

    private abstract class AbstractOneHotEncoderFVec implements FVec {
        protected final int[] _catValues;
        protected final float[] _numValues;

        private  AbstractOneHotEncoderFVec(int[] catValues, float[] numValues) {
            _catValues = catValues;
            _numValues = numValues;
        }

        @Override
        public final float fvalue(int index) {
            if (index >= _catMap.length)
                return _numValues[index - _catMap.length];

            final boolean isHot = getCategoricalValue(index);
            return isHot ? 1 : _notHot;
        }

        protected abstract boolean getCategoricalValue(int index);
    }
    
    private class DefaultOneHotEncoderFVec extends AbstractOneHotEncoderFVec {

        public DefaultOneHotEncoderFVec(int[] catValues, float[] numValues) {
            super(catValues, numValues);
        }

        @Override
        protected boolean getCategoricalValue(int index) {
            return _catValues[_catMap[index]] == index;
        }
    }

    private class OneHotEncoderFVecCompatible10 extends AbstractOneHotEncoderFVec {

        public OneHotEncoderFVecCompatible10(int[] catValues, float[] numValues) {
            super(catValues, numValues);
        }

        @Override
        protected boolean getCategoricalValue(int index) {
            boolean hot = _catValues[_catMap[index]] == index;
            if (hot) return true;
            // check other columns for match
            for (int catValue : _catValues) {
                if (catValue == index) {
                    return true;
                }
            }
            return false;
        }
    }


}

