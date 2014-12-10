package water.api;

import water.Key;
import water.Keyed;
import water.util.ReflectionUtils;

/**
 * Base Schema Class for Keys.  Note that Key schemas are generally typed by the type of
 * object they point to (e.g., the front something like a Key<Frame>).
 */
public class KeySchema<T extends Keyed> extends Schema<Key<T>, KeySchema<T>> {
  @API(help="Name (string representation) for this Key.")
  public String name;

  @API(help="Name (string representation) for the type of Keyed this Key points to.")
  public String type;

  @API(help="URL for the resource that this Key points to, if one exists.")
  public String URL;

  // need versioned
  KeySchema(Key key) {
    this();
    if (null != key)
      this.fillFromImpl(key);
  }

  public KeySchema() {
    __schema_type = "Key<" + _impl_class.getSimpleName() + ">";
  }

  @Override
  public KeySchema<T> fillFromImpl(Key key) {
    if (null == key) return this;

    this.name = key.toString();

    // Our type is generally determined by our type parameter, but some APIs use raw untyped KeySchemas to return multiple types.
    this.type = "Key<" + this.getKeyedClassType() + ">";

    if ("Keyed".equals(this.type)) {
      // get the actual type, if the key points to a value in the DKV
      String vc = key.valueClassSimple();
      if (null != vc) {
        this.type = "Key<" + vc + ">";
      }
    }

    // TODO: URL
    return this;
  }

  public static Class<? extends Keyed> getKeyedClass(Class<? extends KeySchema> clz) {
    return (Class<? extends Keyed>)ReflectionUtils.findActualClassParameter(clz, 0);
  }

  public Class<? extends Keyed> getKeyedClass() {
    return getKeyedClass(this.getClass());
  }

  public static String getKeyedClassType(Class<? extends KeySchema> clz) {
    Class<? extends Keyed> keyed_class = getKeyedClass(clz);
    return keyed_class.getSimpleName();
  }

  public String getKeyedClassType() {
    return getKeyedClassType(this.getClass());
  }

}
