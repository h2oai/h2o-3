package jacoco;

import diff.DiffFile;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.analysis.ISourceNode;
import org.jacoco.core.internal.analysis.CounterImpl;
import org.jacoco.core.internal.analysis.MethodAnalyzer;
import org.jacoco.core.internal.analysis.MethodCoverageImpl;
import org.jacoco.core.internal.flow.IFrame;
import org.jacoco.core.internal.flow.Instruction;
import org.jacoco.core.internal.flow.LabelInfo;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;

import java.util.ArrayList;
import java.util.List;

public class DiffMethodAnalyzer extends MethodAnalyzer {
    private final boolean[] probes;

    private final MethodCoverageImpl coverage;

    private int currentLine = ISourceNode.UNKNOWN_LINE;

    private int firstLine = ISourceNode.UNKNOWN_LINE;

    private int lastLine = ISourceNode.UNKNOWN_LINE;

    // Due to ASM issue #315745 there can be more than one label per instruction
    private final List<Label> currentLabel = new ArrayList<Label>(2);

    /** List of all analyzed instructions */
    private final List<Instruction> instructions = new ArrayList<Instruction>();

    /** List of all predecessors of covered probes */
    private final List<Instruction> coveredProbes = new ArrayList<Instruction>();

    /** List of all jumps encountered */
    private final List<Jump> jumps = new ArrayList<Jump>();

    /** Last instruction in byte code sequence */
    private Instruction lastInsn;

    private DiffFile df;

    /**
     * New Method analyzer for the given probe data.
     *
     * @param name
     *            method name
     * @param desc
     *            method descriptor
     * @param signature
     *            optional parameterized signature
     *
     * @param probes
     *            recorded probe date of the containing class or
     *            <code>null</code> if the class is not executed at all
     */
    public DiffMethodAnalyzer(final String name, final String desc,
                          final String signature, final boolean[] probes, DiffFile df) {
        super(name, desc, signature, probes);
        this.probes = probes;
        this.coverage = new MethodCoverageImpl(name, desc, signature);
        this.df = df;
    }

    /**
     * Returns the coverage data for this method after this visitor has been
     * processed.
     *
     * @return coverage data for this method
     */
    public IMethodCoverage getCoverage() {
        return coverage;
    }

    @Override
    public void visitLabel(final Label label) {
        currentLabel.add(label);
        if (!LabelInfo.isSuccessor(label)) {
            lastInsn = null;
        }
    }

    private boolean isDiffLine(int currentLine) {
        return df.hasLineB(currentLine);
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
        currentLine = line;
        if (firstLine > line || lastLine == ISourceNode.UNKNOWN_LINE) {
            firstLine = line;
        }
        if (lastLine < line) {
            lastLine = line;
        }
    }

    private void visitInsn() {
        final Instruction insn = new Instruction(currentLine);
        System.out.println(currentLine + ": " + isDiffLine(currentLine));
        if (isDiffLine(currentLine)) {
            instructions.add(insn);
        }
        if (lastInsn != null) {
            insn.setPredecessor(lastInsn);
        }
        final int labelCount = currentLabel.size();
        if (labelCount > 0) {
            for (int i = labelCount; --i >= 0;) {
                LabelInfo.setInstruction(currentLabel.get(i), insn);
            }
            currentLabel.clear();
        }
        lastInsn = insn;
    }

    @Override
    public void visitInsn(final int opcode) {
        visitInsn();
    }

    @Override
    public void visitIntInsn(final int opcode, final int operand) {
        visitInsn();
    }

    @Override
    public void visitVarInsn(final int opcode, final int var) {
        visitInsn();
    }

    @Override
    public void visitTypeInsn(final int opcode, final String type) {
        visitInsn();
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner,
                               final String name, final String desc) {
        visitInsn();
    }

    @Override
    public void visitMethodInsn(final int opcode, final String owner,
                                final String name, final String desc, final boolean itf) {
        visitInsn();
    }

    @Override
    public void visitInvokeDynamicInsn(final String name, final String desc,
                                       final Handle bsm, final Object... bsmArgs) {
        visitInsn();
    }

    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        visitInsn();
        jumps.add(new Jump(lastInsn, label));
    }

    @Override
    public void visitLdcInsn(final Object cst) {
        visitInsn();
    }

    @Override
    public void visitIincInsn(final int var, final int increment) {
        visitInsn();
    }

    @Override
    public void visitTableSwitchInsn(final int min, final int max,
                                     final Label dflt, final Label... labels) {
        visitSwitchInsn(dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys,
                                      final Label[] labels) {
        visitSwitchInsn(dflt, labels);
    }

    private void visitSwitchInsn(final Label dflt, final Label[] labels) {
        visitInsn();
        LabelInfo.resetDone(labels);
        jumps.add(new Jump(lastInsn, dflt));
        LabelInfo.setDone(dflt);
        for (final Label l : labels) {
            if (!LabelInfo.isDone(l)) {
                jumps.add(new Jump(lastInsn, l));
                LabelInfo.setDone(l);
            }
        }
    }

    @Override
    public void visitMultiANewArrayInsn(final String desc, final int dims) {
        visitInsn();
    }

    @Override
    public void visitProbe(final int probeId) {
        addProbe(probeId);
        lastInsn = null;
    }

    @Override
    public void visitJumpInsnWithProbe(final int opcode, final Label label,
                                       final int probeId, final IFrame frame) {
        visitInsn();
        addProbe(probeId);
    }

    @Override
    public void visitInsnWithProbe(final int opcode, final int probeId) {
        visitInsn();
        addProbe(probeId);
    }

    @Override
    public void visitTableSwitchInsnWithProbes(final int min, final int max,
                                               final Label dflt, final Label[] labels, final IFrame frame) {
        visitSwitchInsnWithProbes(dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsnWithProbes(final Label dflt,
                                                final int[] keys, final Label[] labels, final IFrame frame) {
        visitSwitchInsnWithProbes(dflt, labels);
    }

    private void visitSwitchInsnWithProbes(final Label dflt,
                                           final Label[] labels) {
        visitInsn();
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        visitSwitchTarget(dflt);
        for (final Label l : labels) {
            visitSwitchTarget(l);
        }
    }

    private void visitSwitchTarget(final Label label) {
        final int id = LabelInfo.getProbeId(label);
        if (!LabelInfo.isDone(label)) {
            if (id == LabelInfo.NO_PROBE) {
                jumps.add(new Jump(lastInsn, label));
            } else {
                addProbe(id);
            }
            LabelInfo.setDone(label);
        }
    }

    @Override
    public void visitEnd() {
        // Wire jumps:
        for (final Jump j : jumps) {
            LabelInfo.getInstruction(j.target).setPredecessor(j.source);
        }
        // Propagate probe values:
        for (final Instruction p : coveredProbes) {
            p.setCovered();
        }
        // Report result:
        coverage.ensureCapacity(firstLine, lastLine);
        for (final Instruction i : instructions) {
            final int total = i.getBranches();
            final int covered = i.getCoveredBranches();
            final ICounter instrCounter = covered == 0 ? CounterImpl.COUNTER_1_0
                    : CounterImpl.COUNTER_0_1;
            final ICounter branchCounter = total > 1 ? CounterImpl.getInstance(
                    total - covered, covered) : CounterImpl.COUNTER_0_0;
            coverage.increment(instrCounter, branchCounter, i.getLine());
        }
        coverage.incrementMethodCounter();
    }

    private void addProbe(final int probeId) {
        lastInsn.addBranch();
        if (probes != null && probes[probeId]) {
            coveredProbes.add(lastInsn);
        }
    }

    private static class Jump {

        final Instruction source;
        final Label target;

        Jump(final Instruction source, final Label target) {
            this.source = source;
            this.target = target;
        }
    }

}