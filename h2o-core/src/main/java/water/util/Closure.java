package water.util;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

/**
 * Encapsulates a class inside, so we can pass around functions.
 */
public class Closure<T> extends ClassLoader implements Serializable {
  private final byte[] code;

  public Closure(Class<?> c) throws IOException {
    code = readJavaClass(c);
  }

  public Closure(T instance) throws IOException {
    this(instance.getClass());
  }

  byte[] code_for_testing() {
    return code.clone();
  }

  static byte[] readJavaClass(Class<?> c) throws IOException {
    URL url = c.getClassLoader().getResource(c.getName().replace('.', '/') + ".class");
    if (url == null) throw new IOException(String.format("Class %s not found", c));
    return com.google.common.io.Resources.asByteSource(url).read();
  }

  public Closure(byte[] code) {
    this.code = code;
  }

  private Class load(String name) {
    return defineClass(name, code, 0, code.length);
  }

  private static int[] CONST_SIZES = {
//      -  U   -  I  F  L  D  C  S  FI M IM NT  -  - MH MT ID
      0, -1, 0, 4, 4, 8, 8, 2, 2, 4, 4, 4, 4, 0, 0, 3, 2, 4
  };

  private int intAt(int i) {
    return code[i] << 8 | code[i + 1];
  }

  public Class loadClass() {
    return load(className());
  }

  public String className() {
    int cpCount = intAt(8) - 1;
    int[] constants = new int[cpCount];
    int ptr = 10;
    for (int i = 0; i < cpCount; i++) {
      constants[i] = ptr;
      byte t = code[ptr];
      int len0 = CONST_SIZES[t];
      if (len0 == 0 && i != cpCount - 1) {
        throw new IllegalArgumentException(String.format("Bad bytecode at %d, type=%d", ptr, t));
      }
      int len = (len0 < 0) ? (2 + intAt(ptr + 1)) : len0;
      ptr += len + 1;
    }
    int classInfoIndex = intAt(ptr + 2) - 1;
    int nameIndex = intAt(constants[classInfoIndex] + 1) - 1;
    int namePtr = constants[nameIndex];
    int nameLen = intAt(namePtr + 1);
    return new String(code, namePtr + 3, nameLen).replaceAll("/", ".");
  }

  @SuppressWarnings("unchecked")
  public T instantiate() throws UnsupportedOperationException {
    Class c = loadClass();

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
}
