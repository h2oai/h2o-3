package water.runner;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;
import water.Key;
import water.TestUtil;

import java.util.HashSet;
import java.util.List;

public class H2ORunner extends BlockJUnit4ClassRunner {
    private TestClass testClass;
    private HashSet<Key> initKeys;

    /**
     * Creates a BlockJUnit4ClassRunner to run {@code klass}
     *
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public H2ORunner(Class<?> klass) throws InitializationError {
        super(klass);
        testClass = getTestClass();
        initKeys = new HashSet<>();
        TestUtil.stall_till_cloudsize(calculateCloudSize());
    }


    /**
     * Returns a {@link Statement}: run all non-overridden {@code @BeforeClass} methods on this class
     * and superclasses before executing {@code statement}; if any throws an
     * Exception, stop execution and pass the exception on.
     */
    protected Statement withBeforeClasses(Statement statement) {
        List<FrameworkMethod> befores = getTestClass()
                .getAnnotatedMethods(BeforeClass.class);
        return befores.isEmpty() ? statement :
                new H2ORunnerBefores(statement, befores, null, initKeys);
    }

    @Override
    protected Statement withAfterClasses(Statement statement) {
        List<FrameworkMethod> afters = testClass
                .getAnnotatedMethods(AfterClass.class);
        return afters.isEmpty() ? statement :
                new H2ORunnerAfters(statement, afters, null);
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        Description description = describeChild(method);
        if (isIgnored(method)) {
            notifier.fireTestIgnored(description);
        } else {
            leaf(methodBlock(method), description, notifier);
        }
    }

    /**
     * Runs a {@link Statement} that represents a leaf (aka atomic) test.
     */
    private void leaf(Statement statement, Description description,
                      RunNotifier notifier) {
        EachTestNotifier eachNotifier = new EachTestNotifier(notifier, description);
        eachNotifier.fireTestStarted();
        try {
            statement.evaluate();
        } catch (AssumptionViolatedException e) {
            eachNotifier.addFailedAssumption(e);
        } catch (Throwable e) {
            eachNotifier.addFailure(e);
        } finally {
            try {
                checkLeakedKeys(description);
            } catch (Throwable t) {
                eachNotifier.addFailure(t);
            } finally {
                new CleanNewKeysTask().doAllNodes();
            }
            eachNotifier.fireTestFinished();
        }
    }

    private void checkLeakedKeys(Description description) {
        CheckKeysTask checkKeysTask = new CheckKeysTask().doAllNodes();
        if(checkKeysTask.leakedKeys.length > 0){
            throw new IllegalStateException(String.format("Test method '%s.%s' leaked %d keys.", description.getTestClass().getName(), description.getMethodName(), checkKeysTask.leakedKeys.length));
        }
        
    }



    private int calculateCloudSize() {
        final CloudSize annotation = testClass.getAnnotation(CloudSize.class);
        if (annotation == null) return 1;

        return annotation.value();
    }


}
