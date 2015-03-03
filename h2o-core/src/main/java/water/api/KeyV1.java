package water.api;

import hex.Model;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.util.Arrays;

/**
 * <p>
 * Base Schema Class for Keys.  Note that Key schemas are generally typed by the type of
 * object they point to (e.g., the front something like a Key<Frame>).
 * <p>
 * The type parameters are a bit subtle, because we have several schemas that map to Key,
 * by type.  We want to be parameterized by the type of Keyed that we point to, but the
 * Iced type we pass up to Schema must be Iced, so that a lookup for a Schema for Key<T>
 * doesn't get an arbitrary subclass of KeyV1.
 */
public class KeyV1<I extends Iced, S extends KeyV1<I, S, K>, K extends Keyed> extends Schema<I, S> {
  @API(help="Name (string representation) for this Key.", direction = API.Direction.INOUT)
  public String name;

  @API(help="Name (string representation) for the type of Keyed this Key points to.", direction = API.Direction.INOUT)
  public String type;

  @API(help="URL for the resource that this Key points to, if one exists.", direction = API.Direction.INOUT)
  public String URL;

  public KeyV1() {
    __meta.schema_type = "Key<" + getKeyedClassType() + ">";
  }

  // need versioned
  public KeyV1(Key key) {
    this();
    if (null != key) {
      Class clz = getKeyedClass();
      Value v = DKV.get(key);

      if (null != v) {
        // Type checking of value from DKV
        if (Job.class.isAssignableFrom(clz) && !v.isJob())
          throw new H2OIllegalArgumentException("For Key: " + key + " expected a value of type Job; found a: " + v.theFreezableClass(), "For Key: " + key + " expected a value of type Job; found a: " + v.theFreezableClass() + " (" + clz + ")");
        else if (Frame.class.isAssignableFrom(clz) && !v.isFrame() && !v.isVec())
        // NOTE: we currently allow Vecs to be fetched via the /Frames endpoint, so this constraint is relaxed accordingly.  Note this means that if the user gets hold of a (hidden) Vec key and passes it to some other endpoint they will get an ugly error instead of an H2OIllegalArgumentException.
          throw new H2OIllegalArgumentException("For Key: " + key + " expected a value of type Frame; found a: " + v.theFreezableClass(), "For Key: " + key + " expected a value of type Frame; found a: " + v.theFreezableClass() + " (" + clz + ")");
        else if (Model.class.isAssignableFrom(clz) && !v.isModel())
          throw new H2OIllegalArgumentException("For Key: " + key + " expected a value of type Model; found a: " + v.theFreezableClass(), "For Key: " + key + " expected a value of type Model; found a: " + v.theFreezableClass() + " (" + clz + ")");
        else if (Vec.class.isAssignableFrom(clz) && !v.isVec())
          throw new H2OIllegalArgumentException("For Key: " + key + " expected a value of type Vec; found a: " + v.theFreezableClass(), "For Key: " + key + " expected a value of type Vec; found a: " + v.theFreezableClass() + " (" + clz + ")");
      }

      this.fillFromImpl(key);
    }
  }

  public static KeyV1 make(Class<? extends KeyV1> clz, Key key) {
    KeyV1 result = null;
    try {
      Constructor c = clz.getConstructor(Key.class);
      result = (KeyV1)c.newInstance(key);
    }
    catch (Exception e) {
      throw H2O.fail("Caught exception trying to instantiate KeyV1 for class: " + clz.toString() + ": " + e + "; cause: " + e.getCause() + " " + Arrays.toString(e.getCause().getStackTrace()));
    }
    return result;
  }

  /** TODO: figure out the right KeyV1 class from the Key, so the type is set properly. */
  public static KeyV1 make(Key key) {
    return make(KeyV1.class, key);
  }

  /** Factory method which returns the correct KeyV1 for the given Keyed class (e.g., for Frame.class). */
  public static KeyV1 forKeyedClass(Class<? extends Keyed> keyed_class, Key key) {
    if (Job.class.isAssignableFrom(keyed_class))
      return KeyV1.make(JobKeyV1.class, key);
    else if (Frame.class.isAssignableFrom(keyed_class))
      return KeyV1.make(FrameKeyV1.class, key);
    else if (Model.class.isAssignableFrom(keyed_class))
      return KeyV1.make(ModelKeyV1.class, key);
    else if (Vec.class.isAssignableFrom(keyed_class))
      return KeyV1.make(VecKeyV1.class, key);
    else
      return KeyV1.make(KeyV1.class, key);
  }

  public static class JobKeyV1 extends KeyV1<Iced, JobKeyV1, Job> {
    public JobKeyV1() {}
    public JobKeyV1(Key<Job> key) {
      super(key);
    }
  }

  public static class FrameKeyV1 extends KeyV1<Iced, FrameKeyV1, Frame> {
    public FrameKeyV1() {}
    public FrameKeyV1(Key<Frame> key) { super(key); }
  }

  public static class ModelKeyV1 extends KeyV1<Iced, ModelKeyV1, Model> {
    public ModelKeyV1() {}
    public ModelKeyV1(Key<? extends Model> key) {
      super(key);
    }
  }

  public static class VecKeyV1 extends KeyV1<Iced, VecKeyV1, Vec> {
    public VecKeyV1() {
    }

    public VecKeyV1(Key<Vec> key) {
      super(key);
    }
  }

  @Override
  public S fillFromImpl(Iced i) {
    if (! (i instanceof Key))
      throw new H2OIllegalArgumentException("fillFromImpl", "key", i);

    Key key = (Key)i;

    if (null == key) return (S)this;

    this.name = key.toString();

    // Our type is generally determined by our type parameter, but some APIs use raw untyped KeyV1s to return multiple types.
    this.type = "Key<" + this.getKeyedClassType() + ">";

    if ("Keyed".equals(this.type)) {
      // get the actual type, if the key points to a value in the DKV
      String vc = key.valueClassSimple();
      if (null != vc) {
        this.type = "Key<" + vc + ">";
      }
    }

    Class<? extends Keyed> keyed_class = this.getKeyedClass();

    // TODO: this is kinda hackey; the handlers should register the types they can fetch.
    if (Job.class.isAssignableFrom(keyed_class))
      this.URL = "/" + Schema.getHighestSupportedVersion() + "/Jobs.json/" + key.toString();
    else if (Frame.class.isAssignableFrom(keyed_class))
      this.URL = "/" + Schema.getHighestSupportedVersion() + "/Frames.json/" + key.toString();
    else if (Model.class.isAssignableFrom(keyed_class))
      this.URL = "/" + Schema.getHighestSupportedVersion() + "/Models.json/" + key.toString();
    else if (Vec.class.isAssignableFrom(keyed_class))
      this.URL = null;
    else
      this.URL = null;

    return (S)this;
  }

  public static Class<? extends Keyed> getKeyedClass(Class<? extends KeyV1> clz) {
    // (Only) if we're a subclass of KeyV1 the Keyed class is type parameter 2.
    if (clz == KeyV1.class)
      return Keyed.class;
    return (Class<? extends Keyed>) ReflectionUtils.findActualClassParameter(clz, 2);
  }

  public Class<? extends Keyed> getKeyedClass() {
    return getKeyedClass(this.getClass());
  }

  public static String getKeyedClassType(Class<? extends KeyV1> clz) {
    Class<? extends Keyed> keyed_class = getKeyedClass(clz);
    return keyed_class.getSimpleName();
  }

  public String getKeyedClassType() {
    return getKeyedClassType(this.getClass());
  }

  public Key<K> key() {
    if (null == name) return null;

    return Key.make(this.name);
  }

  @Override public I createImpl() {
    return (I)Key.make(this.name);
  }

  @Override
  public String toString() {
    return type + " " + name;
  }
}
