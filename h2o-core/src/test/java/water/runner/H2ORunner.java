package water.runner;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.runners.model.EachTestNotifier;
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
import water.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


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
        TestUtil.stall_till_cloudsize(fetchCloudSize());
        new CollectInitKeysTask().doAllNodes();
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
        if (checkKeysTask.leakedKeys.length == 0) {
            return;
        }

        printLeakedKeys(checkKeysTask.leakedKeys);
        throw new IllegalStateException(String.format("Test method '%s.%s' leaked %d keys.", description.getTestClass().getName(), description.getMethodName(), checkKeysTask.leakedKeys.length));
    }


    private void printLeakedKeys(final Key[] leakedKeys) {
        Set<Key> leakedKeysSet = new HashSet<>(leakedKeys.length);

        for (Key k : leakedKeys) {
            leakedKeysSet.add(k);
        }

        for (Key key : leakedKeys) {

            final Value keyValue = Value.STORE_get(key);
            if (keyValue != null && keyValue.isFrame()) {
                Frame frame = (Frame) key.get();
                Log.err(String.format("Leaked frame with key '%s'. This frame contains the following vectors:", frame._key.toString()));

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
                    
                    leakedKeysSet.remove(vecKey);
                }
                leakedKeysSet.remove(key);
            }
        }

        if (!leakedKeysSet.isEmpty()) {
            Log.err(String.format("%nThere are also %d more leaked keys:", leakedKeysSet.size()));
        }

        for (Key key : leakedKeysSet) {
            Log.err(String.format("Key '%s'", key.toString()));
        }
    }



    private int fetchCloudSize() {
        final CloudSize annotation = testClass.getAnnotation(CloudSize.class);
        if (annotation == null) throw new IllegalStateException("@CloudSize annotation is missing for test class: " + testClass.getName());

        final int cloudSize = annotation.value();
        
        if(cloudSize < 1) throw new IllegalStateException("@CloudSize annotation must specify sizes greater than zero. Given value: " + cloudSize);
        
        return cloudSize;
    }


}
