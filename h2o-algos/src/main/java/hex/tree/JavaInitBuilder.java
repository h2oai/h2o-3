package hex.tree;

import hex.Model;
import hex.genmodel.CategoricalEncoding;
import water.util.PojoUtils;
import water.util.SBPrintStream;

public class JavaInitBuilder {

    private final Model.Output _output;

    public JavaInitBuilder(Model.Output output) {
        _output = output;
    }

    SBPrintStream toJavaInit(CategoricalEncoding encoding, SBPrintStream sb) {
        sb.nl();
        sb.ip("public boolean isSupervised() { return true; }").nl();
        sb.ip("public int nfeatures() { return " + _output.nfeatures() + "; }").nl();
        sb.ip("public int nclasses() { return " + _output.nclasses() + "; }").nl();
        if (encoding == CategoricalEncoding.Eigen) {
            sb.ip("public double[] getOrigProjectionArray() { return " + PojoUtils.toJavaDoubleArray(_output._orig_projection_array) + "; }").nl();
        }
        if (encoding != CategoricalEncoding.AUTO) {
            sb.ip("public hex.genmodel.CategoricalEncoding getCategoricalEncoding() { return hex.genmodel.CategoricalEncoding." +
                    encoding.name() + "; }").nl();
        }
        return sb;
    }
    
}
