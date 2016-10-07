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
  private final static boolean DEBUG = true;
  private final byte[] code;
  private final String className;

  public MoveableCode(Class<?> c) throws IOException {
    code = readJavaClass(c);
    className = c.getCanonicalName();
  }

  // TODO(vlad): make sure this is not called with an array
  public MoveableCode(T instance) throws IOException {
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

  public MoveableCode(String className, byte[] code) {
    this.className = className;
    this.code = code;
  }

  private Class load(String name) {
    return defineClass(name, code, 0, code.length);
  }

  private static int[] CONST_SIZES = {
//     -  U   -  I  F  L  D  C  S  FI M IM NT   -   - MH MT ID
      -1, 0, -1, 4, 4, 8, 8, 2, 2, 4, 4, 4, 4, -1, -1, 3, 2, 4
  };

  private int intAt(int i) {
    return code[i] << 8 | code[i + 1];
  }

  private String strAt(int i) {
    int size = intAt(i);
    return new String(code, i+2, size);
  }

  private int[] constants;

  private String stringNo(int i) {
    int ptr = constants[i];
    byte kind = code[ptr];
    if (kind != 1) return null;//throw new IllegalArgumentException(String.format("constant[%d] is of type %d", i, kind));

    return strAt(ptr+1);
  }

  private int classNo(int i) {
    int ptr = constants[i];
    byte kind = code[ptr];
    if (kind != 7) throw new IllegalArgumentException(String.format("constant[%d] is of type %d", i, kind));

    return intAt(ptr+1) - 1;
  }

  public Class loadClass() {
    return load(className);
  }

  public String clazzzzName() throws IllegalArgumentException {
    try {
      int cpCount = intAt(8) - 1;
      constants = new int[cpCount];
      int ptr = 10;
      for (int i = 0; i < cpCount; i++) {
        constants[i] = ptr;
        byte t = code[ptr];
        int len0 = CONST_SIZES[t];
        if (len0 == -1 && i != cpCount - 1) {
          throw new IllegalArgumentException(String.format("Bad bytecode at %d, type=%d", ptr, t));
        }
        int len = (len0 == 0) ? (2 + intAt(ptr + 1)) : len0;
        ptr += len + 1;
      }

//      for(int i = 0; p: constants) System.out.println(p + ":" + (code[p]==1 ? strAt(p+1) : "XXX"));

      int classInfoIndex = intAt(ptr + 2) - 1;
      int classNameIndex = classNo(classInfoIndex);
      String name = stringNo(classNameIndex);
      return name == null ? null : name.replaceAll("/", ".");
    } catch (Exception x) {
      if (DEBUG) {
        x.printStackTrace();
        System.out.println(dumpCode());
      }
      throw new IllegalArgumentException("Failed to extract class name from bytes");
    }
  }

  protected T instance = null;

  synchronized protected T instance() {
    if (instance == null) instance = instantiate();
    return instance;
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

  String dumpCode() { return dump(code); }

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
