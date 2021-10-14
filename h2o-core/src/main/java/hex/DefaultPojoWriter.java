package hex;

import water.Key;
import water.Lockable;
import water.codegen.CodeGeneratorPipeline;
import water.util.Log;
import water.util.SBPrintStream;

class DefaultPojoWriter<M extends Lockable<M>> extends Lockable<M> {

    public DefaultPojoWriter(Key<M> key) {
        super(key);
    }

    protected boolean toJavaCheckTooBig() {
        Log.warn("toJavaCheckTooBig must be overridden for this model type to render it in the browser");
        return true;
    }

    // Override in subclasses to provide some top-level model-specific goodness
    protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileContext) { return sb; }

    // Override in subclasses to provide some inside 'predict' call goodness
    // Method returns code which should be appended into generated top level class after
    // predict method.
    protected void toJavaPredictBody(SBPrintStream body,
                                     CodeGeneratorPipeline classCtx,
                                     CodeGeneratorPipeline fileCtx,
                                     boolean verboseCode) {
        throw new UnsupportedOperationException("This model type does not support conversion to Java");
    }

    // Generates optional "transform" method, transform method will have a different signature depending on the algo
    // Empty by default - can be overriden by Model implementation
    protected SBPrintStream toJavaTransform(SBPrintStream ccsb,
                                            CodeGeneratorPipeline fileCtx,
                                            boolean verboseCode) { // ccsb = classContext
        return ccsb;
    }

}
