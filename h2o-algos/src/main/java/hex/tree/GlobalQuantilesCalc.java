package hex.tree;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.DKV;
import water.Job;
import water.Key;
import water.fvec.Frame;
import water.util.ArrayUtils;

/**
 * Helper class for calculating split points used when histogram type is "QuantilesGlobal"
 */
class GlobalQuantilesCalc {

    /**
     * Calculates split points for histogram type = QuantilesGlobal.
     * 
     * @param fr (adapted) training frame
     * @param weightsColumn name of column containing observation weights (optional) 
     * @param N number of bins
     * @param nbins_top_level number of top-level bins
     * @return array of split points for each feature column of the input training frame
     */
    static double[][] splitPoints(Frame fr, String weightsColumn, final int N, int nbins_top_level) {
        Key<Frame> rndKey = Key.make();
        DKV.put(rndKey, fr);
        QuantileModel qm = null;
        try {
            QuantileModel.QuantileParameters p = new QuantileModel.QuantileParameters();
            p._train = rndKey;
            p._weights_column = weightsColumn;
            p._combine_method = QuantileModel.CombineMethod.INTERPOLATE;
            p._probs = new double[N];
            for (int i = 0; i < N; ++i) //compute quantiles such that they span from (inclusive) min...maxEx (exclusive)
                p._probs[i] = i * 1. / N;
            Job<QuantileModel> job = new Quantile(p).trainModel();
            qm = job.get();
            job.remove();
            double[][] origQuantiles = qm._output._quantiles;
            //pad the quantiles until we have nbins_top_level bins
            double[][] splitPoints = new double[origQuantiles.length][];
            for (int i = 0; i < origQuantiles.length; ++i) {
                if (!fr.vec(i).isNumeric() || fr.vec(i).isCategorical() || fr.vec(i).isBinary() || origQuantiles[i].length <= 1) {
                    continue;
                }
                // make the quantiles split points unique
                splitPoints[i] = ArrayUtils.makeUniqueAndLimitToRange(origQuantiles[i], fr.vec(i).min(), fr.vec(i).max());
                if (splitPoints[i].length <= 1) //not enough split points left - fall back to regular binning
                    splitPoints[i] = null;
                else
                    splitPoints[i] = ArrayUtils.padUniformly(splitPoints[i], nbins_top_level);
                assert splitPoints[i] == null || splitPoints[i].length > 1;
            }
            return splitPoints;
        } finally {
            DKV.remove(rndKey);
            if (qm != null) {
                qm.delete();
            }
        }
    }

}
