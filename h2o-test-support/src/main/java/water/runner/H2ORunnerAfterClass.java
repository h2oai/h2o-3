package water.runner;

import org.junit.Ignore;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import water.junit.rules.tasks.CleanAllKeysTask;

import java.util.ArrayList;
import java.util.List;

@Ignore
public class H2ORunnerAfterClass extends RunAfters {

    private final Statement next;

    private final Object target;

    private final List<FrameworkMethod> afters;

    public H2ORunnerAfterClass(Statement next, List<FrameworkMethod> afters, Object target) {
        super(next, afters, target);
        this.next = next;
        this.target = target;
        this.afters = afters;
    }

    @Override
    public void evaluate() throws Throwable {
        List<Throwable> errors = new ArrayList<Throwable>();
        try {
            next.evaluate();
        } catch (Throwable e) {
            errors.add(e);
        } finally {
            // Clean all keys shared for the whole test class, created during @BeforeClass,
            // but not cleaned in @AfterClass.
            try {
                new CleanAllKeysTask().doAllNodes();
            } catch (Throwable e) {
                errors.add(e);
            }
            for (FrameworkMethod each : afters) {
                try {
                    each.invokeExplosively(target);
                } catch (Throwable e) {
                    errors.add(e);
                }
            }
        }
        MultipleFailureException.assertEmpty(errors);
    }
}
