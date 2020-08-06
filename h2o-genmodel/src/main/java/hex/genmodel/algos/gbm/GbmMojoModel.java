package hex.genmodel.algos.gbm;

import hex.genmodel.GenModel;
import hex.genmodel.PredictContributions;
import hex.genmodel.algos.tree.*;
import hex.genmodel.utils.DistributionFamily;
import hex.genmodel.utils.LinkFunctionType;

import static hex.genmodel.utils.DistributionFamily.*;

/**
 * "Gradient Boosting Machine" MojoModel
 */
public final class GbmMojoModel extends SharedTreeMojoModelWithContributions implements SharedTreeGraphConverter {
    public DistributionFamily _family;
    public LinkFunctionType _link_function;
    public double _init_f;

    public GbmMojoModel(String[] columns, String[][] domains, String responseColumn) {
        super(columns, domains, responseColumn);
    }

    @Override
    protected PredictContributions getContributionsPredictor(TreeSHAPPredictor<double[]> treeSHAPPredictor) {
        return new SharedTreeContributionsPredictor(this, treeSHAPPredictor);
    }
    
    @Override
    public double getInitF() {
        return _init_f;
    }

    /**
     * Corresponds to `hex.tree.gbm.GbmMojoModel.score0()`
     */
    @Override
    public final double[] score0(double[] row, double offset, double[] preds) {
        super.scoreAllTrees(row, preds);
        return unifyPreds(row, offset, preds);
    }

    @Override
    public final double[] unifyPreds(double[] row, double offset, double[] preds) {
        if (_family == bernoulli || _family == quasibinomial || _family == modified_huber) {
            double f = preds[1] + _init_f + offset;
            preds[2] = linkInv(_link_function, f);
            preds[1] = 1.0 - preds[2];
        } else if (_family == multinomial) {
            if (_nclasses == 2) { // 1-tree optimization for binomial
                preds[1] += _init_f + offset; //offset is not yet allowed, but added here to be future-proof
                preds[2] = -preds[1];
            }
            GenModel.GBM_rescale(preds);
        } else { // Regression
            double f = preds[0] + _init_f + offset;
            preds[0] = linkInv(_link_function, f);
            return preds;
        }
        if (_balanceClasses)
            GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
        preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, row, _defaultThreshold);
        return preds;
    }
    
    /**
     * Calculate inverse link depends on distribution type - every distribution has own link function
     * Be careful if you are changing code here - you have to change it in hex.LinkFunction too
     * @param linkFunction link function to compute link inversion
     * @param f raw prediction
     * @return calculated inverse link value
     */
    private double linkInv(LinkFunctionType linkFunction, double f){
        switch (linkFunction) {
            case log:
                return exp(f);
            case logit:
            case ologit:    
                return 1 / (1 + exp(-f));
            case ologlog:
                return 1 - exp(-1 * exp(f));
            case oprobit:
                return 0;
            case inverse:
                double xx = f < 0 ? Math.min(-1e-5, f) : Math.max(-1e-5, f);
                return 1.0/xx;
            case identity:    
            default:
                return f;
        }
    }

    /**
     * Sanitized exponential function - helper function.
     * Be careful if you are changing code here - you have to change it in hex.LogExpUtils too
     *
     * @param x value to be transform
     * @return result of exp function
     */
    public static double exp(double x) { return Math.min(1e19, Math.exp(x)); }

    /**
     * Sanitized log function - helper function
     * Be careful if you are changing code here - you have to change it in hex.LogExpUtils too
     *
     * @param x value to be transform
     * @return result of log function
     */
    public static double log(double x) {
        x = Math.max(0, x);
        return x == 0 ? -19 : Math.max(-19, Math.log(x));
    }
    
    @Override
    public double[] score0(double[] row, double[] preds) {
        return score0(row, 0.0, preds);
    }

    public String[] leaf_node_assignment(double[] row) {
        return getDecisionPath(row);
    }

    @Override
    public String[] getOutputNames() {
        if (_family == quasibinomial && getDomainValues(getResponseIdx()) == null) {
            return new String[]{"predict", "pVal0", "pVal1"};
        }
        return super.getOutputNames();
    }

}
