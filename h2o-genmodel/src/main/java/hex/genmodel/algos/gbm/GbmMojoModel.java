package hex.genmodel.algos.gbm;

import hex.genmodel.GenModel;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.utils.DistributionFamily;

import static hex.genmodel.utils.DistributionFamily.*;

/**
 * "Gradient Boosting Machine" MojoModel
 */
public final class GbmMojoModel extends SharedTreeMojoModel {
    public DistributionFamily _family;
    public double _init_f;

    public GbmMojoModel(String[] columns, String[][] domains) {
        super(columns, domains);
    }


    /**
     * Corresponds to `hex.tree.drf.DrfMojoModel.score0()`
     */
    @Override
    public final double[] score0(double[] row, double offset, double[] preds) {
        super.scoreAllTrees(row, preds);
        if (_family == bernoulli || _family == modified_huber) {
            double f = preds[1] + _init_f + offset;
            preds[2] = _family.linkInv(f);
            preds[1] = 1.0 - preds[2];
        } else if (_family == multinomial) {
            if (_nclasses == 2) { // 1-tree optimization for binomial
                preds[1] += _init_f + offset; //offset is not yet allowed, but added here to be future-proof
                preds[2] = -preds[1];
            }
            GenModel.GBM_rescale(preds);
        } else { // Regression
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
