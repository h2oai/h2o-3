package hex.genmodel.algos;

import hex.genmodel.GenModel;
import hex.genmodel.utils.DistributionFamily;

import java.util.Map;

import static hex.genmodel.utils.DistributionFamily.*;

/**
 * "Gradient Boosting Machine" MojoModel
 */
public final class GbmModel extends TreeBasedModel {
    private DistributionFamily _family;
    private double _init_f;

    public GbmModel(MojoReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
        super(cr, info, columns, domains);
        _family = DistributionFamily.valueOf((String) info.get("distribution"));
        _init_f = (double) info.get("init_f");
    }

    /**
     * Corresponds to `hex.tree.drf.DrfModel.score0()`
     */
    @Override
    public final double[] score0(double[] row, double offset, double[] preds) {
        if (_family == bernoulli || _family == modified_huber) {
            super.scoreAllTrees(row, preds, 1);
            double f = preds[1] + _init_f + offset;
            preds[2] = _family.linkInv(f);
            preds[1] = 1.0 - preds[2];
        } else if (_family == multinomial) {
            super.scoreAllTrees(row, preds, _nclasses == 2? 1 : _nclasses);
            if (_nclasses == 2) { // 1-tree optimization for binomial
                preds[1] += _init_f + offset; //offset is not yet allowed, but added here to be future-proof
                preds[2] = -preds[1];
            }
            GenModel.GBM_rescale(preds);
        } else { // Regression
            super.scoreAllTrees(row, preds, 1);
            double f = preds[0] + _init_f + offset;
            preds[0] = _family.linkInv(f);
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
