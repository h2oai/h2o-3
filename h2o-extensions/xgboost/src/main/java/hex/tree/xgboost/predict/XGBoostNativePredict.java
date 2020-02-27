package hex.tree.xgboost.predict;

import hex.genmodel.GenModel;
import hex.genmodel.algos.xgboost.XGBoostMojoModel;
import ml.dmlc.xgboost4j.java.Booster;
import hex.tree.xgboost.util.BoosterHelper;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoostError;

public class XGBoostNativePredict {

    public static double[] score0(
        double[] doubles, double offset, double[] preds,
        String _boosterType, int _ntrees,
        Booster _booster, int _nums, int _cats,
        int[] _catOffsets, boolean _useAllFactorLevels,
        int nclasses, double[] _priorClassDistrib,
        double _defaultThreshold, final boolean _sparse,
        boolean _hasOffset
    ) {
        int cats = _catOffsets == null ? 0 : _catOffsets[_cats];
        // convert dense doubles to expanded floats
        final float[] floats = new float[_nums + cats]; //TODO: use thread-local storage
        GenModel.setInput(doubles, floats, _nums, _cats, _catOffsets, null, null, _useAllFactorLevels, _sparse /*replace NA with 0*/);
        float[] out;
        DMatrix dmat = null;
        try {
            dmat = new DMatrix(floats,1, floats.length, _sparse ? 0 : Float.NaN);
            if (_hasOffset) {
                dmat.setBaseMargin(new float[] {  (float) offset });
            } else if (offset != 0) {
                throw new UnsupportedOperationException("Unsupported: offset != 0");
            }
            final DMatrix row = dmat;
            final int treeLimit;
            if ("dart".equals(_boosterType)) {
                treeLimit = _ntrees;
            } else {
                treeLimit = 0;
            }
            BoosterHelper.BoosterOp<float[]> predictOp = new BoosterHelper.BoosterOp<float[]>() {
                @Override
                public float[] apply(Booster booster) throws XGBoostError {
                    return booster.predict(row, false, treeLimit)[0];
                }
            };
            out = BoosterHelper.doWithLocalRabit(predictOp, _booster);
        } catch (XGBoostError xgBoostError) {
            throw new IllegalStateException("Failed XGBoost prediction.", xgBoostError);
        } finally {
            BoosterHelper.dispose(dmat);
        }

        return XGBoostMojoModel.toPreds(doubles, out, preds, nclasses, _priorClassDistrib, _defaultThreshold);
    }

}
