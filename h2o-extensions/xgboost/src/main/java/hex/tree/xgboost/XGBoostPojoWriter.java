package hex.tree.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.Dart;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import hex.LinkFunction;
import hex.LinkFunctionFactory;
import hex.genmodel.utils.LinkFunctionType;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.util.SBPrintStream;

import static hex.genmodel.algos.xgboost.XGBoostMojoModel.ObjectiveType;

public abstract class XGBoostPojoWriter {

    public static XGBoostPojoWriter make(
        Predictor p,
        String namePrefix,
        XGBoostOutput output,
        double defaultThreshold
    ) {
        if (p.getBooster() instanceof GBTree) {
            return new XGBoostPojoTreeWriter(p, namePrefix, output, defaultThreshold);
        } else {
            throw new UnsupportedOperationException("GBLiear not supported.");
        }
    }

    protected final Predictor _p;
    protected final String _namePrefix;
    protected final XGBoostOutput _output;
    protected final double _defaultThreshold;

    protected XGBoostPojoWriter(Predictor p, String namePrefix, XGBoostOutput output, double defaultThreshold) {
        _p = p;
        _namePrefix = namePrefix;
        _output = output;
        _defaultThreshold = defaultThreshold;
    }

    private void renderTree(JCodeSB sb, RegTree tree, int nidx) {
        RegTreeNode node = tree.getNodes()[nidx];
        if (node.isLeaf()) {
            sb.ip("").pj(node.getLeafValue());
        } else {
            String accessor;
            if (node.getSplitIndex() >= _output._catOffsets[_output._cats]) {
                int colIdx = node.getSplitIndex() - _output._catOffsets[_output._cats] + _output._cats;
                accessor = "data[" + colIdx + "]";
            } else {
                int colIdx = 0;
                while (node.getSplitIndex() >= _output._catOffsets[colIdx + 1]) colIdx++;
                int colValue = node.getSplitIndex() - _output._catOffsets[colIdx];
                accessor = "(data[" + colIdx + "] == " + colValue + " ? 1 : " + (_output._sparse ? "NaN" : "0") + ")";
            }
            String operator;
            int trueChild;
            int falseChild;
            if (node.default_left()) {
                operator = " < ";
                trueChild = node.getLeftChildIndex();
                falseChild = node.getRightChildIndex();
            } else {
                operator = " >= ";
                trueChild = node.getRightChildIndex();
                falseChild = node.getLeftChildIndex();
            }
            sb.ip("((Double.isNaN(").p(accessor).p(") || ").p(accessor).p(operator).pj(node.getSplitCondition()).p(") ?").nl();
            sb.ii(1);
            renderTree(sb, tree, trueChild);
            sb.nl().ip(":").nl();
            renderTree(sb, tree, falseChild);
            sb.di(1);
            sb.nl().ip(")");
        }
    }

    protected String renderTreeClass(
        RegTree[][] trees,
        final int gidx,
        final int tidx,
        final Dart dart,
        CodeGeneratorPipeline fileCtx
    ) {
        final RegTree tree = trees[gidx][tidx];
        final String className = _namePrefix + "_Tree_g_" + gidx + "_t_" + tidx;
        fileCtx.add(new CodeGenerator() {
            @Override
            public void generate(JCodeSB sb) {
                sb.nl().p("class ").p(className).p(" {").nl();
                sb.ii(1);
                sb.ip("static float score0(double[] data) {").nl();
                sb.ii(1);
                sb.ip("return ");
                if (dart != null) {
                    sb.pj(dart.weight(tidx)).p(" * ");
                }
                renderTree(sb, tree, 0);
                sb.p(";").nl();
                sb.di(1);
                sb.ip("}").nl();
                sb.di(1);
                sb.ip("}").nl();
            }
        });
        return className;
    }

    private void renderPredTransformViaLinkFunction(LinkFunctionType type, SBPrintStream sb) {
        LinkFunction lf = LinkFunctionFactory.getLinkFunction(type);
        sb.ip("preds[0] = (float) ").p(lf.linkInvString("preds[0]")).p(";").nl();
    }

    private void renderMultiClassPredTransform(SBPrintStream sb) {
        sb.ip("double max = preds[0];").nl();
        sb.ip("for (int i = 1; i < preds.length-1; i++) max = Math.max(preds[i], max); ").nl();
        sb.ip("double sum = 0.0D;").nl();
        sb.ip("for (int i = 0; i < preds.length-1; i++) {").nl();
        sb.ip("  preds[i] = Math.exp(preds[i] - max);").nl();
        sb.ip("  sum += preds[i];").nl();
        sb.ip("}").nl();
        sb.ip("for (int i = 0; i < preds.length-1; i++) {").nl();
        sb.ip("  preds[i] /= (float) sum;").nl();
        sb.ip("}").nl();
    }

    protected void renderPredTransform(SBPrintStream sb) {
        String objFunction = _p.getObjName();
        if (ObjectiveType.REG_GAMMA.getId().equals(objFunction) ||
            ObjectiveType.REG_TWEEDIE.getId().equals(objFunction) ||
            ObjectiveType.COUNT_POISSON.getId().equals(objFunction)) {
            renderPredTransformViaLinkFunction(LinkFunctionType.log, sb);
        } else if (ObjectiveType.BINARY_LOGISTIC.getId().equals(objFunction)) {
            renderPredTransformViaLinkFunction(LinkFunctionType.logit, sb);
        } else if (ObjectiveType.REG_LINEAR.getId().equals(objFunction) ||
            ObjectiveType.RANK_PAIRWISE.getId().equals(objFunction)) {
            renderPredTransformViaLinkFunction(LinkFunctionType.identity, sb);
        } else if (ObjectiveType.MULTI_SOFTPROB.getId().equals(objFunction)) {
            renderMultiClassPredTransform(sb);
        } else {
            throw new IllegalArgumentException("Unexpected objFunction " + objFunction);
        }
    }

    public abstract void renderJavaPredictBody(SBPrintStream sb, CodeGeneratorPipeline fileCtx);

}

class XGBoostPojoTreeWriter extends XGBoostPojoWriter {

    protected XGBoostPojoTreeWriter(Predictor p, String namePrefix, XGBoostOutput output, double defaultThreshold) {
        super(p, namePrefix, output, defaultThreshold);
    }

    @Override
    public void renderJavaPredictBody(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
        GBTree booster = (GBTree) _p.getBooster();
        Dart dartBooster = null;
        if (booster instanceof Dart) {
            dartBooster = (Dart) booster;
        }
        RegTree[][] trees = booster.getGroupedTrees();
        for (int gidx = 0; gidx < trees.length; gidx++) {
            sb.ip("preds[").p(gidx).p("] = ").nl();
            sb.ii(1);
            for (int tidx = 0; tidx < trees[gidx].length; tidx++) {
                String treeClassName = renderTreeClass(trees, gidx, tidx, dartBooster, fileCtx);
                sb.ip(treeClassName).p(".score0(data)").p(" + ").nl();
            }
            sb.ip("").pj(_p.getBaseScore()).p(";").nl();
            sb.di(1);
        }
        renderPredTransform(sb);
        if (_output.nclasses() > 2) {
            sb.ip("for (int i = preds.length-2; i >= 0; i--)").nl();
            sb.ip("  preds[1 + i] = preds[i];").nl();
            sb.ip("preds[0] = GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, ").pj(_defaultThreshold).p(");").nl();
        } else if (_output.nclasses() == 2) {
            sb.ip("preds[1] = 1f - preds[0];").nl();
            sb.ip("preds[2] = preds[0];").nl();
            sb.ip("preds[0] = GenModel.getPrediction(preds, PRIOR_CLASS_DISTRIB, data, ").pj(_defaultThreshold).p(");").nl();
        }
    }
    
}
