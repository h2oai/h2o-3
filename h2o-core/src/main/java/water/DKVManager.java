package water;

import hex.Model;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;

public class DKVManager {

  public static void clear() {
    // Bulk brainless key removal.  Completely wipes all Keys without regard.
    new MRTask(H2O.MIN_HI_PRIORITY) {
      @Override
      public void setupLocal() {
        H2O.raw_clear();
        water.fvec.Vec.ESPC.clear();
      }
    }.doAllNodes();
    // Wipe the backing store without regard as well
    H2O.getPM().getIce().cleanUp();
    H2O.updateNotIdle();
  }

  /**
   * Clears keys in all H2O nodes, except for the ones marked as retained.
   * Only Model and Frame keys are retained. If a key of any other type is provided, it will be removed as well.
   * <p>
   * Model's training and validation frames are retained automatically with the specified model. However, cross validation models are NOT retained.
   *
   * @param retainedKeys Keys of {@link Frame}s and {@link Model}s to be retained. Only Frame and Model keys are accepted.
   */
  public static void retain(final Key... retainedKeys) {
    final Set<Key> retainedSet = new HashSet<>(retainedKeys.length);
    retainedSet.addAll(Arrays.asList(retainedKeys));
    // Frames and models have multiple nested keys. Those must be extracted and kept from deletion as well.
    extractNestedKeys(retainedSet);
    final Key[] allRetainedkeys = retainedSet.toArray(new Key[retainedSet.size()]);
    final NodeKeysRemovalTask nodeKeysRemovalTask = new NodeKeysRemovalTask(allRetainedkeys);

    for (final H2ONode node : H2O.CLOUD.members()) {
      H2O.runOnH2ONode(node, nodeKeysRemovalTask);
    }
  }

  /**
   * Iterates through the keys provided by the user, dropping any keys that are not a Model key or a Frame key.
   * Afterwards, extracts
   *
   * @param retainedKeys A {@link Set} of retained keys to insert the extracted {@link Frame} and {@link Model} keys to.
   *                     Should contain user-specified keys to retain in order to extract anything.
   * @throws IllegalArgumentException If any of the keys given to be retained is not a Model key nor a Frame key
   */
  private static void extractNestedKeys(final Set<Key> retainedKeys) throws IllegalArgumentException {

    final Iterator<Key> keysIterator = retainedKeys.iterator(); // Traverse keys provided by the user only.
    final Set<Key> newKeys = new HashSet<>(); // Avoid concurrent modification of retainedKeys set + avoid introducing locking & internally synchronized set structures 
    while (keysIterator.hasNext()) {
      final Key key = keysIterator.next();

      final Value value = DKV.get(key);

      if (value == null || value.isNull()) {
        continue; // Ignore missing values
      } else if (!value.isFrame() && !value.isModel()) {
        throw new IllegalArgumentException(String.format("Given key %s is of type %d. Please provide only Model and Frame keys.", key.toString(), value.type()));
      } else if (value.isFrame()) {
        extractFrameKeys(newKeys, value.get());
      }
    }
    retainedKeys.addAll(newKeys); // Add the newly found keys to the original retainedKeys set after the iteration to avoid concurrent modification
  }

  /**
   * Extracts keys a {@link Frame} points to.
   *
   * @param retainedkeys A set of retained keys to insert the extracted {@link Frame} keys to.
   * @param frame        An instance of {@link Frame} to extract the keys from.
   */
  private static void extractFrameKeys(final Set<Key> retainedkeys, final Frame frame) {
    Objects.requireNonNull(frame);
    final Key<Vec>[] frameKeys = frame.keys();
    for (Key k : frameKeys) {
      retainedkeys.add(k);
    }
  }

  private static final class NodeKeysRemovalTask extends H2O.RemoteRunnable<NodeKeysRemovalTask> {

    private final Key[] _ignoredKeys;

    private NodeKeysRemovalTask(final Key[] retainedKeys) {
      _ignoredKeys = retainedKeys;
    }

    @Override
    public void run() {
      final Set<Key> keys = H2O.localKeySet();
      final Set<Key> ignoredSet = new HashSet<>();
      final Futures futures = new Futures();

      for (final Key ignoredKey : _ignoredKeys) {
        ignoredSet.add(ignoredKey);
      }

      for (final Key key : keys) {
        if (ignoredSet.contains(key)) continue; // Do not perform DKV.get at all if the key is to be ignored

        final Value value = DKV.get(key);
        if (value == null || value.isNull()) continue;
        if (value.isModel()) {
          Keyed.remove(key, futures, false);
        } else if (value.isFrame()) {
          final Frame frame = value.get();
          frame.retain(futures, ignoredSet);
        }
        futures.blockForPending(); // Delete one key at a time.
      }
    }
  }
}
