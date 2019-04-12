package water.runner;

import org.junit.internal.runners.statements.RunBefores;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.util.List;

public class H2ORunnerBefores extends RunBefores {
    private final List<FrameworkMethod> befores;
    private final Object target;
    private final Statement next;

    public H2ORunnerBefores(Statement next, List<FrameworkMethod> befores, Object target) {
        super(next, befores, target);
        this.next = next;
        this.target = target;
        this.befores = befores;
    }

    @Override
    public void evaluate() throws Throwable {

        for (FrameworkMethod before : befores) {
            before.invokeExplosively(target);
        }
        new CollectInitKeysTask().doAllNodes();
        next.evaluate();
    }
}
