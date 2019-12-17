package water;

import hex.Model;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.*;

public class DKVManager {

  /**
   * Clears keys in all H2O nodes, except for the ones marked as retained.
   * Only Model and Frame keys are retained. If a key of any other type is provided, it will be removed as well.
   * <p>
   * Model's training and validation frames are retained automatically with the specified model. However, cross validation models are NOT retained.
   *
   * @param retainedKeys Keys of {@link Frame}s and {@link Model}s to be retained.
   */
  public static void retain(final Key[] retainedKeys) {
    Log.debug(String.format("Clearing DKV. Obtained %d keys to retain.", retainedKeys.length));
    final Set<Key> retainedSet = new HashSet<>(retainedKeys.length);
    retainedSet.addAll(Arrays.asList(retainedKeys));
    extractNestedKeys(retainedSet);
    Log.debug(String.format(String.format("After nested key extraction, there are %d keys to retain.", retainedSet.size())));

    for (Key k : retainedSet) {
      if (k.get() == null) {
        Log.debug(String.format("Retaining key '%s' with value '%s'", k, k.get()));
      } else {
        Log.debug(String.format("Retaining key '%s' with value '%s' and frozen type", k, k.get(), k.get().frozenType()));
      }

    }

    new ClearDKVTask(retainedSet.toArray(new Key[retainedSet.size()]))
            .doAllNodes();
  }

  /**
   * Iterates through the keys provided by the user, dropping any keys that are not a Model key or a Frame key.
   * Afterwards, extracts
   *
   * @param retainedKeys A {@link Set} of retained keys to insert the extracted {@link Frame} and {@link Model} keys to.
   *                     Should contain user-specified keys to retain in order to extract anything.
   */
  private static void extractNestedKeys(final Set<Key> retainedKeys) {

    final Iterator<Key> keysIterator = retainedKeys.iterator(); // Traverse keys provided by the user only.
    final Set<Key> newKeys = new HashSet<>(); // Avoid concurrent modification of retainedKeys set + avoid introducing locking & internally synchronized set structures 
    while (keysIterator.hasNext()) {
      final Key key = keysIterator.next();

      final Value value = DKV.get(key);

      Log.debug(String.format("Extracting nested keys for key '%s' with value '%s'",
              key, value));

      if (value == null || value.isNull()) {
        Log.debug(String.format("Value for key '%s' is null. There is not freezable type available.", key));
        continue; // Ignore missing values
      } else {
        Log.debug("Freezable type for Freezable '%s' is %s", value.getFreezable(), value.getFreezable().frozenType());
        if (!value.isFrame() && !value.isModel()) {
          throw new IllegalArgumentException(String.format("Given key %s is of type %d. Please provide only Model and Frame keys.", key.toString(), value.type()));
        } else if (value.isFrame()) {
          extractFrameKeys(newKeys, value.get());
        } else if (value.isModel()) {
          extractModelKeys(newKeys, value.get());
        }
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
    Log.debug(String.format("Preparing to extract keys for frame '%s'", frame));
    final Key<Vec>[] frameKeys = frame.keys();
    for (Key k : frameKeys) {
      retainedkeys.add(k);
      Log.debug(String.format("Retaining extracted key '%s'", k));
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

  private static class ClearDKVTask extends MRTask<ClearDKVTask> {

    private final Key[] _retainedKeys; // Original model & frame keys provided by the user, accompanied with extracted internal keys

    /**
     *
     * @param retainedKeys Keys that are NOT deleted and will remain in DKV. Only Model keys and Frame keys are accepted
     */
    public ClearDKVTask(Key[] retainedKeys) {
      _retainedKeys = retainedKeys;
    }

    @Override
    protected void setupLocal() {
      final Set<Key> retainedKeys = new HashSet<>(_retainedKeys.length);
      retainedKeys.addAll(Arrays.asList(_retainedKeys));

      final Collection<Value> storeKeys = H2O.STORE.values();
      Futures removalFutures = new Futures();
      for (final Value value : storeKeys) {
        Log.debug(String.format("Retaining key '%s' with value '%s' of freezable type %d", value._key, value, value.frozenType()));
        if (retainedKeys.contains(value._key)) {
          continue;
        }
        if(value.isNull()) continue;

        if (value.isFrame()) {
          ((Frame) value.get()).retain(removalFutures, retainedKeys);
        } else if (value.isModel()) {
          ((Model)value.get()).remove(removalFutures);
        }
      }
      removalFutures.blockForPending();
    }


  }
}
