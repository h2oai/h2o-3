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
   * @param retainedKeys Keys of {@link Frame}s and {@link Model}s to be retained. Only Frame and Model keys are accepted.
   */
  public static void retain(final Key[] retainedKeys){
    final Set<Key> retainedSet = new HashSet<>(retainedKeys.length);
    retainedSet.addAll(Arrays.asList(retainedKeys));
    // Frames and models have multiple nested keys. Those must be extracted and kept from deletion as well.
    extractNestedKeys(retainedSet);

    new ClearDKVTask(retainedSet.toArray(new Key[retainedSet.size()]))
            .doAllNodes();
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
        if (retainedKeys.contains(value._key)) {
          continue;
        }
        try {
          // The `is*` methods of the Value class (isFrame, isModel() etc.) do call getFreezable method, which might
          // throw ClassNotFoundException if the value has already been removed (type BAD). However, the methods do not
          // declare the ClassNotFound exception, thefore the catch block does not compile. To avoid catching general
          // Exception here,  the `TypeMap.getTheFreezableOrThrow` is called beforehand, as this method declares the 
          // exception and the code compiles.
          TypeMap.getTheFreezableOrThrow(value.type());

          if (value.isNull()) continue;
          if (value.isFrame()) {
            ((Frame) value.get()).retain(removalFutures, retainedKeys);
          } else if (value.isModel()) {
            ((Model) value.get()).remove(removalFutures);
          }
        } catch (ClassNotFoundException e) {
          // Keys are collected globally and then, on each node, this ClearDKVTask is invoked. Local H2O.STORE on each node is
          // deleted, as it might contain local-only keys. This strategy is used to prevent collecting keys from all nodes and then
          // invoking "remove" on one node. When each node iterates over local keys, one key might be removed multiple times.
          // This might result in ClassNotFoundException, as getTheFreezable might from TypeMap might throw this error.
          Log.debug(e);
          continue; // Value not present, ignore
        }
      }
      removalFutures.blockForPending();
    }


  }
}
