package hex.tree.gbm;

import hex.Distribution;
import hex.DistributionFactory;
import hex.LinkFunction;
import hex.Model;
import hex.genmodel.CategoricalEncoding;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.CompressedTree;
import hex.tree.SharedTreePojoWriter;
import water.util.SBPrintStream;

class GbmPojoWriter extends SharedTreePojoWriter {

    private final double _init_f;
    private final boolean _balance_classes;
    private final DistributionFamily _distribution_family;
    private final LinkFunction _link_function;

    GbmPojoWriter(GBMModel model, CompressedTree[][] trees) {
        super(model._key, model._output, model.getGenModelEncoding(), model.binomialOpt(),
                trees, model._output._treeStats);
        _init_f = model._output._init_f;
        _balance_classes = model._parms._balance_classes;
        Distribution distribution = DistributionFactory.getDistribution(model._parms);
        _distribution_family = distribution._family;
        _link_function = distribution._linkFunction;
    }

    GbmPojoWriter(Model<?, ?, ?> model, CategoricalEncoding encoding,
                  boolean binomialOpt, CompressedTree[][] trees,
                  double initF, boolean balanceClasses,
                  DistributionFamily distributionFamily, LinkFunction linkFunction) {
        super(model._key, model._output, encoding, binomialOpt, trees, null);
        _init_f = initF;
        _balance_classes = balanceClasses;
        _distribution_family = distributionFamily;
        _link_function = linkFunction;
    }

    // Note: POJO scoring code doesn't support per-row offsets (the scoring API would need to be changed to pass in offsets)
    @Override
    protected void toJavaUnifyPreds(SBPrintStream body) {
        // Preds are filled in from the trees, but need to be adjusted according to
        // the loss function.
        if (_distribution_family == DistributionFamily.bernoulli
                || _distribution_family == DistributionFamily.quasibinomial
                || _distribution_family == DistributionFamily.modified_huber
        ) {
            body.ip("preds[2] = preds[1] + ").p(_init_f).p(";").nl();
            body.ip("preds[2] = " + _link_function.linkInvString("preds[2]") + ";").nl();
            body.ip("preds[1] = 1.0-preds[2];").nl();
            if (_balance_classes)
                body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
            body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + _output.defaultThreshold() + ");").nl();
            return;
        }
        if (_output.nclasses() == 1) { // Regression
            body.ip("preds[0] += ").p(_init_f).p(";").nl();
            body.ip("preds[0] = " + _link_function.linkInvString("preds[0]") + ";").nl();
            return;
        }
        if (_output.nclasses() == 2) { // Kept the initial prediction for binomial
            body.ip("preds[1] += ").p(_init_f).p(";").nl();
            body.ip("preds[2] = - preds[1];").nl();
        }
        body.ip("hex.genmodel.GenModel.GBM_rescale(preds);").nl();
        if (_balance_classes)
            body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
        body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + _output.defaultThreshold() + ");").nl();
    }
}
