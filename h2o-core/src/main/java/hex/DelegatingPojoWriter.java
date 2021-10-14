package hex;

import water.codegen.CodeGeneratorPipeline;
import water.util.SBPrintStream;

public class DelegatingPojoWriter implements PojoWriter {

    private final DefaultPojoWriter<?> _builder;

    DelegatingPojoWriter(DefaultPojoWriter<?> builder) {
        _builder = builder;
    }

    @Override
    public boolean toJavaCheckTooBig() {
        return _builder.toJavaCheckTooBig();
    }

    @Override
    public SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileContext) {
        return _builder.toJavaInit(sb, fileContext);
    }

    @Override
    public void toJavaPredictBody(SBPrintStream body, CodeGeneratorPipeline classCtx, CodeGeneratorPipeline fileCtx, boolean verboseCode) {
        _builder.toJavaPredictBody(body, classCtx, fileCtx, verboseCode);
    }

    @Override
    public SBPrintStream toJavaTransform(SBPrintStream ccsb, CodeGeneratorPipeline fileCtx, boolean verboseCode) {
        return _builder.toJavaTransform(ccsb, fileCtx, verboseCode);
    }

}
