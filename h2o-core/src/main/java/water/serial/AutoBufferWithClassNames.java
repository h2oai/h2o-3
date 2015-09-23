package water.serial;

import java.nio.channels.FileChannel;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.TypeMap;

/** Simple wrapper around {@link AutoBuffer}
 * which uses class names instead of type ids.
 *
 * @see AutoBuffer
 * @see TypeMap
 */
class AutoBufferWithClassNames extends AutoBuffer {

    public AutoBufferWithClassNames() { super(); }
    public AutoBufferWithClassNames(byte[] b) { super(b); }

    public AutoBufferWithClassNames(FileChannel fc, boolean read) {
      super(fc,read, (byte) 0);
    }

    private static String NULL = "^";

    public static <T extends Freezable> T newInstance(String klazz) {
      try {
        int typeId = TypeMap.onIce(klazz);
        return (T) TypeMap.newFreezable(typeId);
      } catch (Exception e) {
        throw H2O.fail("Cannot instantiate class: " + klazz + " because of " + e.getMessage());
      }
    }
    @Override public AutoBuffer put(Freezable f) {
      if( f == null ) return putStr(NULL);
      putStr(f.getClass().getName());
      return f.write(this);
    }
    @Override public <T extends Freezable> T get(Class<T> t) {
      String klazz = getStr();
      return NULL.equals(klazz) ? null : (T) newInstance(klazz).read(this);
    }

    @Override public <T extends Freezable> T get() {
      String klazz = getStr();
      return NULL.equals(klazz) ? null : (T) newInstance(klazz).read(this);
    }
  }
