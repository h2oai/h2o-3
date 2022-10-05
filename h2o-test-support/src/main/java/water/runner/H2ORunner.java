package water.runner;

import org.junit.AfterClass;
import org.junit.Before;
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
import water.Key;
import water.TestUtil;
import water.Value;
import water.fvec.Frame;
import water.fvec.Vec;
import water.junit.rules.PriorityTestRule;
import water.util.Log;

import java.util.*;


@Ignore
public class H2ORunner extends BlockJUnit4ClassRunner {

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
    protected Statement withBefores(FrameworkMethod method, Object target, Statement statement) {
        List<FrameworkMethod> befores = getTestClass().getAnnotatedMethods(
                Before.class);
        return new H2ORunnerBefores(statement, befores, target);
    }

    @Override
    protected Statement withAfterClasses(Statement statement) {
        final List<FrameworkMethod> afters = getTestClass()
                .getAnnotatedMethods(AfterClass.class);
        return new H2ORunnerAfters(statement, afters, null);
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
        if (target instanceof TestUtil) {
            return super.getTestRules(target);
        }

        List<TestRule> rules = new ArrayList<>(super.getTestRules(target));
        // add rules defined in TestUtil
        rules.addAll(new TestClass(DefaultRulesBlueprint.class)
                .getAnnotatedFieldValues(DefaultRulesBlueprint.INSTANCE, Rule.class, TestRule.class));
        rules.sort(new Comparator<TestRule>() {
          /** 
           * sort rules from lower (or no priority) to higher priority rules 
           * so that the latter ones can be "applied" last and therefore "evaluated" first (=outermost rules) 
           * **/
          @Override
          public int compare(TestRule lhs, TestRule rhs) {
            int lp = 0, rp = 0;
            if (lhs instanceof PriorityTestRule) lp = ((PriorityTestRule)lhs).priority();
            if (rhs instanceof PriorityTestRule) rp = ((PriorityTestRule)rhs).priority();
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
            }
            eachNotifier.fireTestFinished();
        }
    }

    private void checkLeakedKeys(final Description description) {
        final CheckKeysTask checkKeysTask = new CheckKeysTask().doAllNodes();
        if (checkKeysTask.leakedKeys.length == 0) {
            return;
        }

        printLeakedKeys(checkKeysTask.leakedKeys, checkKeysTask.leakInfos);
        throw new IllegalStateException(String.format("Test method '%s.%s' leaked %d keys.", description.getTestClass().getName(), description.getMethodName(), checkKeysTask.leakedKeys.length));
    }


    private void printLeakedKeys(final Key[] leakedKeys, final CheckKeysTask.LeakInfo[] leakInfos) {
        final Set<Key> leakedKeysSet = new HashSet<>(leakedKeys.length);

        leakedKeysSet.addAll(Arrays.asList(leakedKeys));

        for (Key key : leakedKeys) {

            final Value keyValue = Value.STORE_get(key);
            if (keyValue != null && keyValue.isFrame()) {
                Frame frame = (Frame) key.get();
                Log.err(String.format("Leaked frame with key '%s' and columns '%s'. This frame contains the following vectors:", 
                        frame._key.toString(), Arrays.toString(frame.names())));

                for (Key vecKey : frame.keys()) {
                    if (!leakedKeysSet.contains(vecKey)) continue;
                    Log.err(String.format("   Vector '%s'. This vector contains the following chunks:", vecKey.toString()));

                    final Vec vec = (Vec) vecKey.get();
                    for (int i = 0; i < vec.nChunks(); i++) {
                        final Key chunkKey = vec.chunkKey(i);
                        if (!leakedKeysSet.contains(chunkKey)) continue;
                        Log.err(String.format("       Chunk id %d, key '%s'", i, chunkKey));
                        leakedKeysSet.remove(chunkKey);
                    }
                    
                    if (leakedKeysSet.contains(vec.rollupStatsKey())) {
                        Log.err(String.format("       Rollup stats '%s'", vec.rollupStatsKey().toString()));
                        leakedKeysSet.remove(vec.rollupStatsKey());
                    }

                    leakedKeysSet.remove(vecKey);
                }
                leakedKeysSet.remove(key);
            }
        }

        if (!leakedKeysSet.isEmpty()) {
            Log.err(String.format("%nThere are %d uncategorized leaked keys detected:", leakedKeysSet.size()));
        }

        for (Key key : leakedKeysSet) {
            Log.err(String.format("Key '%s' of type %s.", key.toString(), key.valueClass()));
        }

        for (CheckKeysTask.LeakInfo leakInfo : leakInfos) {
            Log.err(String.format("Leak info for key '%s': %s", leakedKeys[leakInfo._keyIdx], leakInfo));
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
