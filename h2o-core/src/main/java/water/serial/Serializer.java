package water.serial;

import java.io.IOException;
import java.lang.reflect.Field;

import water.AutoBuffer;
import water.AutoBufferWithoutTypeIds;
import water.Freezable;

/**
 * A simple serializer interface.
 */
interface Serializer<T, IO> {
  /**
   * Save given object into given target.
   * @param e  object to serialize
   * @param output serialization destination (i.e., file, stream)
   * @throws IOException
   */
  public void save(T e, IO output) throws IOException;
  /**
   * Load object from given destination (i.e., file, stream)
   * @param e  object to be filled from
   * @param input object data provider
   * @return
   */
  public T load(T e, IO input) throws IOException;

  /** Load an object from specified input. */
  public T load(IO input) throws IOException;
}

/** Common class for saving top-level Iced objects.
 *
 * It saves header with a simple type hash for given class T.
 *
 * @param <T>  an iced object
 * @param <IO>  input/output definition
 */
abstract class BinarySerializer<T extends Freezable, IO> implements Serializer<T, IO> {

  /** Generate a simple hash for given class for its declared fields. */
  protected int id(T m) {
    int r = m.getClass().getCanonicalName().hashCode();
    for (Field f : m.getClass().getDeclaredFields())  r ^= f.getName().hashCode();
    return r;
  }

  /** Save a simple header by putting hash of serialized class and
   * name of the class.
   *
   * @param m
   * @param ab
   * @return
   */
  protected AutoBuffer saveHeader(T m, AutoBuffer ab) {
    ab.put4(id(m));
    ab.putStr(m.getClass().getName());
    return ab;
  }
  protected T loadHeader(AutoBuffer ab) {
    int smId = ab.get4(); // type hash
    String smCN = ab.getStr(); // type name
    // Load it
    T m = null;
    try {
      m = (T) AutoBufferWithoutTypeIds.newInstance(smCN);
    } catch( Exception e ) {
      throw new IllegalArgumentException("Cannot instantiate the type " + smCN, e);
    }
    int amId = id(m);
    if (amId != smId)
      throw new IllegalArgumentException("Trying to load incompatible model! Actual model id = " + amId + ", stored id = " + smId+", type="+smCN);
    return m;
  }
}
