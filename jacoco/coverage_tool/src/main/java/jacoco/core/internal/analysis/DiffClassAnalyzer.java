package jacoco.core.internal.analysis;

import diff.DiffFile;
import diff.DiffReport;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.internal.analysis.ClassAnalyzer;
import org.jacoco.core.internal.analysis.ClassCoverageImpl;
import org.jacoco.core.internal.analysis.StringPool;
import org.jacoco.core.internal.flow.MethodProbesVisitor;
import org.jacoco.core.internal.instr.InstrSupport;
import org.objectweb.asm.Opcodes;

public class DiffClassAnalyzer extends ClassAnalyzer {
    private StringPool stringPool;
    private ClassCoverageImpl coverage;
    private boolean[] probes;
    private DiffReport _dr;
    private DiffFile _df;
    private boolean hasDiff = false;

    public DiffClassAnalyzer(final ClassCoverageImpl coverage, final boolean[] probes, final StringPool stringPool, DiffReport dr) {
        super(coverage, probes, stringPool);
        this.probes = probes;
        this.stringPool = stringPool;
        this.coverage = coverage;
        this._dr = dr;
    }

    @Override
    public void visitSource(final String source, final String debug) {
        coverage.setSourceFileName(stringPool.get(source));
        hasDiff = (_df = _dr.getDiffFile(source)) != null;
        System.out.println(source + ": " + hasDiff);
    }

    // TODO: Use filter hook in future
    private boolean isMethodFiltered(final int access, final String name) {
        return (access & Opcodes.ACC_SYNTHETIC) != 0
                && !name.startsWith("lambda$");
    }

    @Override
    public MethodProbesVisitor visitMethod(final int access, final String name,
                                           final String desc, final String signature, final String[] exceptions) {
        InstrSupport.assertNotInstrumented(name, coverage.getName());
        if (!hasDiff) return null;

        if (isMethodFiltered(access, name)) {
            return null;
        }

        return new DiffMethodAnalyzer(stringPool.get(name), stringPool.get(desc),
                stringPool.get(signature), probes, _df) {
            @Override
            public void visitEnd() {
                super.visitEnd();
                final IMethodCoverage methodCoverage = getCoverage();
                if (methodCoverage.getInstructionCounter().getTotalCount() > 0) {
                    // Only consider methods that actually contain code
                    coverage.addMethod(methodCoverage);
                }
            }
        };
    }
}
