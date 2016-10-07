package water.util;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

/**
 * Encapsulates a class inside, so we can pass around functions.
 */
public class MoveableCode<T> extends ClassLoader implements Serializable {

  public Class loadClass() { return code.loadClass(); }

  private class Code {
    private final String className;
    private final byte[] bytes;
    private final Code outer;

    Code(String className, byte[] bytes, Code outer) {
      this.className = className;
      this.bytes = bytes;
      this.outer = outer;
    }

    Code(Class<?> c) throws IOException {
      bytes = readJavaClass(c);
      className = c.getName();
      Class<?> dc = c.getDeclaringClass();
      outer = (dc == null) ? null : new Code(dc);
    }

    Class loadClass() {
      if (outer != null) outer.loadClass();
      return defineClass(className, bytes, 0, bytes.length);
    }
  }

  byte[] readJavaClass(Class<?> c) throws IOException {
    URL url = c.getClassLoader().getResource(c.getName().replace('.', '/') + ".class");
    if (url == null) throw new IOException(String.format("Class %s not found", c));
    return com.google.common.io.Resources.asByteSource(url).read();
  }

  private Code code;

//  private final static boolean DEBUG = true;

  public MoveableCode(Class<?> c) throws IOException {
    code = new Code(c);
  }

  // TODO(vlad): make sure this is not called with an array
  public MoveableCode(T instance) throws IOException {
    this(instance.getClass());
  }

  byte[] code_bytes_for_testing() {
    return code.bytes.clone();
  }

  public MoveableCode(String className, byte[] bytes) {
    this(className, bytes, null);
  }

  public MoveableCode(String className, byte[] bytes, Code outer) {
    this.code = new Code(className, bytes, outer);
  }

  protected T instance = null;

  synchronized protected T instance() {
    if (instance == null) instance = instantiate();
    return instance;
  }

  @SuppressWarnings("unchecked")
  T instantiate() throws UnsupportedOperationException {
    Class c = code.loadClass();
    try {
      Constructor con = c.getDeclaredConstructor();
      con.setAccessible(true);
      Object instance = con.newInstance();
      return (T) instance;
    } catch (ClassCastException e) {
      throw new UnsupportedOperationException("Could not cast to required type: " + c, e);
    } catch (InstantiationException e) {
      throw new UnsupportedOperationException("Failed to instantiate " + c, e);
    } catch (IllegalAccessException e) {
      throw new UnsupportedOperationException("Access problems with class " + c, e);
    } catch (NoSuchMethodException e) {
      throw new UnsupportedOperationException("Constructor missing in " + c, e);
    } catch (InvocationTargetException e) {
      throw new UnsupportedOperationException("Constructor failed in " + c, e);
    }
  }

  String dumpCode() { return dump(code.bytes); }

  public static String dump(byte[] bytes) {
    StringWriter out = new StringWriter();

    for (int i = 0; i < bytes.length; i+= 16) {
      for (int j = i; j < bytes.length && j < i+16; j++) {
        byte b = bytes[j];
        String s = Integer.toHexString(b + 0x1100);
        out.write(s.substring(s.length() - 2));
        out.write(" ");
      }
      out.write("\n");
    }
    return out.toString();
  }

}
