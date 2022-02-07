package hex.tree;

import hex.Model;
import hex.PojoWriter;
import hex.genmodel.CategoricalEncoding;
import hex.genmodel.DefaultCategoricalEncoding;
import water.Key;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.util.JCodeGen;
import water.util.PojoUtils;
import water.util.SB;
import water.util.SBPrintStream;

public abstract class SharedTreePojoWriter implements PojoWriter {

    // common for all models
    protected final Key<?> _modelKey;
    protected final Model.Output _output;

    // specific to tree based models
    protected final CategoricalEncoding _encoding;
    protected final boolean _binomialOpt;
    protected final CompressedTree[/*_ntrees*/][/*_nclass*/] _trees;

    protected final TreeStats _treeStats; // optional (can be null)

    protected SharedTreePojoWriter(Key<?> modelKey, Model.Output output,
                                   CategoricalEncoding encoding, boolean binomialOpt, CompressedTree[][] trees,
                                   TreeStats treeStats) {
        _modelKey = modelKey;
        _output = output;

        _encoding = encoding;
        _binomialOpt = binomialOpt;
        _trees = trees;

        _treeStats = treeStats;
    }

    @Override
    public boolean toJavaCheckTooBig() {
        return _treeStats == null || _treeStats._num_trees * _treeStats._mean_leaves > 1000000;
    }

    @Override
    public SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileContext) {
        sb.nl();
        sb.ip("public boolean isSupervised() { return true; }").nl();
        sb.ip("public int nfeatures() { return " + _output.nfeatures() + "; }").nl();
        sb.ip("public int nclasses() { return " + _output.nclasses() + "; }").nl();
        if (_encoding == DefaultCategoricalEncoding.Eigen) {
            sb.ip("public double[] getOrigProjectionArray() { return " + PojoUtils.toJavaDoubleArray(_output._orig_projection_array) + "; }").nl();
        }
        if (_encoding != DefaultCategoricalEncoding.AUTO) {
            sb.ip("public hex.genmodel.CategoricalEncoding getCategoricalEncoding() { return hex.genmodel.DefaultCategoricalEncoding." +
                    _encoding.name() + "; }").nl();
        }
        return sb;
    }

    @Override
    public void toJavaPredictBody(SBPrintStream body, 
                                  CodeGeneratorPipeline classCtx, CodeGeneratorPipeline fileCtx, 
                                  final boolean verboseCode) {
        final int nclass = _output.nclasses();
        body.ip("java.util.Arrays.fill(preds,0);").nl();
        final String mname = JCodeGen.toJavaId(_modelKey.toString());

        // One forest-per-GBM-tree, with a real-tree-per-class
        for (int t=0; t < _trees.length; t++) {
            // Generate score method for given tree
            toJavaForestName(body.i(),mname,t).p(".score0(data,preds);").nl();

            final int treeIdx = t;

            fileCtx.add(out -> {
                try {
                    // Generate a class implementing a tree
                    out.nl();
                    toJavaForestName(out.ip("class "), mname, treeIdx).p(" {").nl().ii(1);
                    out.ip("public static void score0(double[] fdata, double[] preds) {").nl().ii(1);
                    for (int c = 0; c < nclass; c++) {
                        if (_trees[treeIdx][c] == null) continue;
                        if (!(_binomialOpt && c == 1 && nclass == 2)) // Binomial optimization
                            toJavaTreeName(out.ip("preds[").p(nclass == 1 ? 0 : c + 1).p("] += "), mname, treeIdx, c).p(".score0(fdata);").nl();
                    }
                    out.di(1).ip("}").nl(); // end of function
                    out.di(1).ip("}").nl(); // end of forest class

                    // Generate the pre-tree classes afterwards
                    for (int c = 0; c < nclass; c++) {
                        if (_trees[treeIdx][c] == null) continue;
                        if (!(_binomialOpt && c == 1 && nclass == 2)) { // Binomial optimization
                            String javaClassName = toJavaTreeName(new SB(), mname, treeIdx, c).toString();
                            SB sb = new SB();
                            new TreeJCodeGen(_output, _trees[treeIdx][c], sb, javaClassName, verboseCode).generate();
                            out.p(sb);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Internal error creating the POJO.", e);
                }
            });
        }

        toJavaUnifyPreds(body);
    }

    protected abstract void toJavaUnifyPreds(SBPrintStream body);

    private static <T extends JCodeSB<T>> T toJavaTreeName(T sb, String mname, int t, int c ) {
        return sb.p(mname).p("_Tree_").p(t).p("_class_").p(c);
    }

    private static <T extends JCodeSB<T>> T toJavaForestName(T sb, String mname, int t ) {
        return sb.p(mname).p("_Forest_").p(t);
    }

}
