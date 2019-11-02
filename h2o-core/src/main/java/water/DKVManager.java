package water;

import hex.Model;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.*;

public class DKVManager {

  /**
   * Clears keys in all H2O nodes, except for the ones marked as retained.
   * Only Model and Frame keys are retained. If a key of any other type is provided, it will be removed as well.
   * 
   * Model's training and validation frames are retained automatically with the specified model. However, cross validation models are NOT retained.
   *
   * @param retainedKeys Keys of {@link Frame}s and {@link Model}s to be retained.
   */
  public static void retain(final Key[] retainedKeys){
    final Set<Key> retainedSet = new HashSet<>(retainedKeys.length);
    retainedSet.addAll(Arrays.asList(retainedKeys));
    extractNestedKeys(retainedSet);

    new ClearDKVTask(retainedSet.toArray(new Key[]{}))
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
      // Does not call runLocal in order to serialize only things present in this MRTask
      final Collection<Value> storeKeys = H2O.STORE.values();
      Futures removalFutures = new Futures();
      for (final Value value : storeKeys) {
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

  /**
   * Retains given keys locally, not cluster-wide.
   *
   * @param retainedKeys Keys to retain
   */
  public static void retainLocal(final Set<Key> retainedKeys) {
    final Collection<Value> storeKeys = H2O.STORE.values();
    Futures removalFutures = new Futures();
    for (final Value value : storeKeys) {
      if (retainedKeys.contains(value._key)) {
        continue;
      }
      if (value.isNull()) continue;

      if (value.isFrame()) {
        ((Frame) value.get()).retain(removalFutures, retainedKeys);
      } else if (value.isModel()) {
        ((Model) value.get()).remove(removalFutures);
      }
    }
    removalFutures.blockForPending();
  }
}
