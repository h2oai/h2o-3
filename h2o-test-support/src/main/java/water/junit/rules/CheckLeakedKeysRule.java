package water.junit.rules;

import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import water.Key;
import water.Value;
import water.fvec.Frame;
import water.fvec.Vec;
import water.junit.Priority;
import water.junit.rules.tasks.CheckKeysTask;
import water.junit.rules.tasks.CollectBeforeTestKeysTask;
import water.runner.H2ORunner;
import water.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Priority(RulesPriorities.CHECK_LEAKED_KEYS)
public class CheckLeakedKeysRule extends ExternalResource {

  @Override
  protected void before() throws Throwable {
    new CollectBeforeTestKeysTask().doAllNodes();
  }

  @Override
  protected void after() {
    checkLeakedKeys(H2ORunner.currentTest.get());
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

}
