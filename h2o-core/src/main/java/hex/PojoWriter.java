package hex;

import water.codegen.CodeGeneratorPipeline;
import water.util.SBPrintStream;

public interface PojoWriter {

    boolean toJavaCheckTooBig();

    // Override in subclasses to provide some top-level model-specific goodness
    SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileContext);

    // Override in subclasses to provide some inside 'predict' call goodness
    // Method returns code which should be appended into generated top level class after
    // predict method.
    void toJavaPredictBody(SBPrintStream body,
                           CodeGeneratorPipeline classCtx,
                           CodeGeneratorPipeline fileCtx,
                           boolean verboseCode);

    // Generates optional "transform" method, transform method will have a different signature depending on the algo
    // Empty by default - can be overridden by Model implementation
    default SBPrintStream toJavaTransform(SBPrintStream ccsb,
                                          CodeGeneratorPipeline fileCtx,
                                          boolean verboseCode) {
        return ccsb;
    }
}
