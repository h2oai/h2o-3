package hex.genmodel.algos;

import hex.genmodel.GenModel;
import hex.genmodel.utils.Distribution;

import java.util.Map;

/**
 * "Gradient Boosting Method" RawModel
 */
public final class GbmRawModel extends TreeBasedModel {
    private Distribution _distribution;
    private double _init_f;

    public GbmRawModel(ContentReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
        super(cr, info, columns, domains);
        _distribution = new Distribution(info);
        _init_f = (double) info.get("init_f");
    }

    /**
     * Corresponds to `hex.tree.drf.DrfModel.score0()`
     */
    @Override
    public final double[] score0(double[] row, double offset, double[] preds) {
        if (_distribution.isBernoulliOrModhuber()) {
            super.scoreAllTrees(row, preds, 1);
            double f = preds[1] + _init_f + offset;
            preds[2] = _distribution.linkInv(f);
            preds[1] = 1.0 - preds[2];
        } else if (_distribution.isMultinomial()) {
            super.scoreAllTrees(row, preds, _nclasses == 2? 1 : _nclasses);
            if (_nclasses == 2) { // 1-tree optimization for binomial
                preds[1] += _init_f + offset; //offset is not yet allowed, but added here to be future-proof
                preds[2] = -preds[1];
            }
            GenModel.GBM_rescale(preds);
        } else { // Regression
            super.scoreAllTrees(row, preds, 1);
            double f = preds[0] + _init_f + offset;
            preds[0] = _distribution.linkInv(f);
            return preds;
        }
        if (_balanceClasses)
            GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
        preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, row, _defaultThreshold);
        return preds;
    }

    @Override
    public double[] score0(double[] row, double[] preds) {
        return score0(row, 0.0, preds);
    }
}
