package water.runner;

import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;
import water.TestUtil;
import water.junit.Priority;
import water.junit.rules.CheckLeakedKeysRule;

import java.util.*;


@Ignore
public class H2ORunner extends BlockJUnit4ClassRunner {
  
  public static final ThreadLocal<Description> currentTest = new ThreadLocal<>();

    /**
     * Creates a BlockJUnit4ClassRunner to run {@code klass}
     *
     * @param klass
     * @throws InitializationError if the test class is malformed.
     */
    public H2ORunner(Class<?> klass) throws InitializationError {
        super(klass);
    }


    @Override
    protected Statement withAfterClasses(Statement statement) {
        final List<FrameworkMethod> afters = getTestClass()
                .getAnnotatedMethods(AfterClass.class);
        return new H2ORunnerAfterClass(statement, afters, null);
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        final Description description = describeChild(method);
        if (isIgnored(method)) {
            notifier.fireTestIgnored(description);
        } else {
            leaf(methodBlock(method), description, notifier);
        }
    }

    @Override
    protected List<TestRule> getTestRules(Object target) {
        List<TestRule> rules = new ArrayList<>();
        rules.add(new CheckLeakedKeysRule());
        rules.addAll(super.getTestRules(target));
        if (!(target instanceof TestUtil)) {
          // add rules defined in TestUtil
          rules.addAll(new TestClass(DefaultRulesBlueprint.class)
                  .getAnnotatedFieldValues(DefaultRulesBlueprint.INSTANCE, Rule.class, TestRule.class));
        }
        rules.sort(new Comparator<TestRule>() {
          /** 
           * sort rules from lower (or no priority) to higher priority rules 
           * so that the latter ones can be "applied" last and therefore "evaluated" first (=outermost rules) 
           * **/
          @Override
          public int compare(TestRule lhs, TestRule rhs) {
            int lp = 0, rp = 0;
            if (lhs.getClass().isAnnotationPresent(Priority.class)) lp = lhs.getClass().getAnnotation(Priority.class).value();
            if (rhs.getClass().isAnnotationPresent(Priority.class)) rp = rhs.getClass().getAnnotation(Priority.class).value();
            return lp - rp;
          }
        });
        return rules;
    }

    public static class DefaultRulesBlueprint extends TestUtil {
        private static final DefaultRulesBlueprint INSTANCE = new DefaultRulesBlueprint(); 
    }
    
    /**
     * Runs a {@link Statement} that represents a leaf (aka atomic) test.
     */
    private void leaf(Statement statement, Description description,
                      RunNotifier notifier) {
        TestUtil.stall_till_cloudsize(fetchCloudSize());
        
        final EachTestNotifier eachNotifier = new EachTestNotifier(notifier, description);
        eachNotifier.fireTestStarted();
        try {
            currentTest.set(description);
            statement.evaluate();
        } catch (AssumptionViolatedException e) {
            eachNotifier.addFailedAssumption(e);
        } catch (Throwable e) {
            eachNotifier.addFailure(e);
        } finally {
            currentTest.remove();
            eachNotifier.fireTestFinished();
        }
    }



    private int fetchCloudSize() {
        final CloudSize annotation = getTestClass().getAnnotation(CloudSize.class);
        if (annotation == null) {
            return 1;
        }

        final int cloudSize = annotation.value();

        if (cloudSize < 1)
            throw new IllegalStateException("@CloudSize annotation must specify sizes greater than zero. Given value: " + cloudSize);

        return cloudSize;
    }

}
