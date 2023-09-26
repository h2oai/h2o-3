package hex.tree.uplift;

import hex.Model;
import hex.genmodel.CategoricalEncoding;
import hex.tree.CompressedTree;
import hex.tree.SharedTreePojoWriter;
import water.util.SBPrintStream;

public class UpliftDrfPojoWriter extends SharedTreePojoWriter {
    
    UpliftDrfPojoWriter(UpliftDRFModel model, CompressedTree[][] trees) {
        super(model._key, model._output, model.getGenModelEncoding(), model.binomialOpt(),
                trees, model._output._treeStats);
    }

    UpliftDrfPojoWriter(Model<?, ?, ?> model, CategoricalEncoding encoding,
                  boolean binomialOpt, CompressedTree[][] trees,
                  boolean balanceClasses) {
        super(model._key, model._output, encoding, binomialOpt, trees, null);
    }

    @Override
    protected void toJavaUnifyPreds(SBPrintStream body) {
        body.ip("preds[1] /= " + _trees.length + ";").nl();
        body.ip("preds[2] /= " + _trees.length + ";").nl();
        body.ip("preds[0] = preds[1] - preds[2]");
    }
}
