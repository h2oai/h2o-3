package water;

import hex.Model;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.*;

public class DKVManager {

  /**
   * Clears keys in all H2O nodes, except for the ones marked as retained.
   * Only Model and Frame keys are retained. If a key of any other type is provided, it will be removed as well.
   * <p>
   * Model's training and validation frames are retained automatically with the specified model. However, cross validation models are NOT retained.
   *
   * @param retainedKeys Keys of {@link Frame}s and {@link Model}s to be retained. Only Frame and Model keys are accepted.
   */
  public static void retain(final Key[] retainedKeys) {
    final Set<Key> retainedKeysSet = new HashSet<>(retainedKeys.length);
    retainedKeysSet.addAll(Arrays.asList(retainedKeys));
    // Frames and models have multiple nested keys. Those must be extracted and kept from deletion as well.
    extractNestedKeys(retainedKeysSet);
    Set<Value> removedKeys = collectUniqueKeys();
    removeValues(removedKeys, retainedKeysSet);
  }

  /**
   * Collects a {@link Set} of keys available cluster-wide
   *
   * @return
   */
  private static final Set<Value> collectUniqueKeys() {
    final Value[] collectedValues = new CollectValuesTask()
            .doAllNodes()
            ._collectedValues;
    final Set<Value> clusterValues = new HashSet<>(collectedValues.length);

    for (final Value value : collectedValues) {
      clusterValues.add(value);
    }

    return clusterValues;
  }

  /**
   * Removes values from DKV, except for keys meant to be retained.
   *
   * @param removedValues Values to be removed
   * @param retainedKeys  A {@link Set} of keys to be retained
   */
  private static void removeValues(final Set<Value> removedValues, final Set<Key> retainedKeys) {

    for (final Value value : removedValues) {
      if (retainedKeys.contains(value._key)) {
        continue;
      }
      final Futures removalFutures = new Futures();
      if (value == null || value._key == null || value.isNull()) continue;
      if (value.isFrame()) {
        final Frame frame = value.get();
        frame.retain(removalFutures, retainedKeys);
      } else if (value.isModel()) {
        final Model model = value.get();
        model.remove(removalFutures, false);
      }
      removalFutures.blockForPending();
    }

  }

  /**
   * Collects all {@link Value}(s) cluster-wide and stores them inside the _collectedValues field
   */
  private static class CollectValuesTask extends MRTask<CollectValuesTask> {
    private Value[] _collectedValues;

    @Override
    protected void setupLocal() {
      final Collection<Value> localkeysSet = H2O.values();
      _collectedValues = localkeysSet.toArray(new Value[localkeysSet.size()]);
    }

    @Override
    public void reduce(final CollectValuesTask mrt) {
      try {
        // Protection against too many keys present on a cluster to fit into a single array. 
        Math.addExact(_collectedValues.length, mrt._collectedValues.length);
      } catch (ArithmeticException e) {
        throw new IllegalStateException("Too many keys on cluster - Array of keys is overflown.", e);
      }
      _collectedValues = ArrayUtils.append(_collectedValues, mrt._collectedValues);
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
        extractFrameKeys(newKeys, (Frame) value.get());
      } else if (value.isModel()) {
        extractModelKeys(newKeys, (Model) value.get());
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

  /**
   * Exctracts keys a {@link Model} points to.
   *
   * @param retainedKeys A set of retained keys to insert the extracted {@link Model} keys to.
   * @param model        An instance of {@link Model} to extract the keys from
   */
  private static void extractModelKeys(final Set<Key> retainedKeys, final Model model) {
    Objects.requireNonNull(model);
    if (model._parms._train != null) {
      retainedKeys.add(model._parms._train);
      extractFrameKeys(retainedKeys, model._parms._train.get());
    }
    if (model._parms._valid != null) {
      retainedKeys.add(model._parms._valid);
      extractFrameKeys(retainedKeys, model._parms._valid.get());
    }
  }
}
