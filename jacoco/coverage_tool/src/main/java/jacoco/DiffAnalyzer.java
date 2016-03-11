package jacoco;

import diff.DiffReport;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.ICoverageVisitor;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.internal.analysis.ClassAnalyzer;
import org.jacoco.core.internal.analysis.ClassCoverageImpl;
import org.jacoco.core.internal.analysis.StringPool;
import org.jacoco.core.internal.data.CRC64;
import org.jacoco.core.internal.flow.ClassProbesAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

/**
 * Created by nkalonia1 on 3/10/16.
 */
public class DiffAnalyzer extends Analyzer {

    private final StringPool stringPool;
    private final ExecutionDataStore executionData;
    private final ICoverageVisitor coverageVisitor;
    private final DiffReport dr;

    public DiffAnalyzer(final ExecutionDataStore executionData,
                        final ICoverageVisitor coverageVisitor, DiffReport dr) {
        super(executionData, coverageVisitor);
        this.coverageVisitor = coverageVisitor;
        this.executionData = executionData;
        this.stringPool = new StringPool();
        this.dr = dr;
    }

    /**
     * Creates an ASM class visitor for analysis.
     *
     * @param classid
     *            id of the class calculated with {@link CRC64}
     * @param className
     *            VM name of the class
     * @return ASM visitor to write class definition to
     */
    private ClassVisitor createAnalyzingVisitor(final long classid,
                                                final String className) {
        final ExecutionData data = executionData.get(classid);
        final boolean[] probes;
        final boolean noMatch;
        if (data == null) {
            probes = null;
            noMatch = executionData.contains(className);
        } else {
            probes = data.getProbes();
            noMatch = false;
        }
        final ClassCoverageImpl coverage = new ClassCoverageImpl(className,
                classid, noMatch);
        final ClassAnalyzer analyzer = new DiffClassAnalyzer(coverage, probes,
                stringPool, dr) {
            @Override
            public void visitEnd() {
                super.visitEnd();
                coverageVisitor.visitCoverage(coverage);
            }
        };
        return new ClassProbesAdapter(analyzer, false);
    }

    @Override
    public void analyzeClass(final ClassReader reader) {
        final ClassVisitor visitor = createAnalyzingVisitor(
                CRC64.checksum(reader.b), reader.getClassName());
        reader.accept(visitor, 0);
    }

}
