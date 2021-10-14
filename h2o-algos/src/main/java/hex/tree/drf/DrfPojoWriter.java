package hex.tree.drf;

import hex.Model;
import hex.genmodel.CategoricalEncoding;
import hex.tree.CompressedTree;
import hex.tree.SharedTreePojoWriter;
import water.util.SBPrintStream;

class DrfPojoWriter extends SharedTreePojoWriter {

    private final boolean _balance_classes;

    DrfPojoWriter(DRFModel model, CompressedTree[][] trees) {
        super(model._key, model._output, model.getGenModelEncoding(), model.binomialOpt(),
                trees, model._output._treeStats);
        _balance_classes = model._parms._balance_classes;
    }

    DrfPojoWriter(Model<?, ?, ?> model, CategoricalEncoding encoding,
                  boolean binomialOpt, CompressedTree[][] trees,
                  boolean balanceClasses) {
        super(model._key, model._output, encoding, binomialOpt, trees, null);
        _balance_classes = balanceClasses;
    }

    @Override
    protected void toJavaUnifyPreds(SBPrintStream body) {
        if (_output.nclasses() == 1) { // Regression
            body.ip("preds[0] /= " + _trees.length + ";").nl();
        } else { // Classification
            if (_output.nclasses() == 2 && _binomialOpt) { // Kept the initial prediction for binomial
                body.ip("preds[1] /= " + _trees.length + ";").nl();
                body.ip("preds[2] = 1.0 - preds[1];").nl();
            } else {
                body.ip("double sum = 0;").nl();
                body.ip("for(int i=1; i<preds.length; i++) { sum += preds[i]; }").nl();
                body.ip("if (sum>0) for(int i=1; i<preds.length; i++) { preds[i] /= sum; }").nl();
            }
            if (_balance_classes)
                body.ip("hex.genmodel.GenModel.correctProbabilities(preds, PRIOR_CLASS_DISTRIB, MODEL_CLASS_DISTRIB);").nl();
            body.ip("preds[0] = hex.genmodel.GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, " + _output.defaultThreshold() + ");").nl();
        }
    }
}
