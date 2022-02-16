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
     * @param trainFr (adapted) training frame
     * @param weightsColumn name of column containing observation weights (optional)
     * @param priorSplitPoints optional pre-existing split points for some columns
     * @param N number of bins
     * @param nbins_top_level number of top-level bins
     * @return array of split points for each feature column of the input training frame
     */
    static double[][] splitPoints(Frame trainFr, String weightsColumn, 
                                  double[][] priorSplitPoints, final int N, int nbins_top_level) {
        final Frame fr = new Frame();
        final int[] frToTrain = new int[trainFr.numCols()];
        for (int i = 0; i < trainFr.numCols(); ++i) {
            if (priorSplitPoints != null && priorSplitPoints[i] != null) {
                continue;
            }
            if (!trainFr.vec(i).isNumeric() || trainFr.vec(i).isCategorical() || 
                    trainFr.vec(i).isBinary() || trainFr.vec(i).isConst()) {
                continue;
            }
            frToTrain[fr.numCols()] = i;
            fr.add(trainFr.name(i), trainFr.vec(i));
        }
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
            double[][] splitPoints = new double[trainFr.numCols()][];
            for (int q = 0; q < origQuantiles.length; q++) {
                if (origQuantiles[q].length <= 1) {
                    continue;
                }
                final int i = frToTrain[q];
                // make the quantiles split points unique
                splitPoints[i] = ArrayUtils.makeUniqueAndLimitToRange(origQuantiles[q], fr.vec(q).min(), fr.vec(q).max());
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
