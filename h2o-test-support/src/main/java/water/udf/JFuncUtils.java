package water.udf;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import water.DKV;
import water.Key;
import water.Value;
import water.util.ArrayUtils;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Ignore("Support for tests, but no actual tests here")
public class JFuncUtils {

  /**
   * Load given jar file into K/V store under given name as untyped Value.
   * @param keyName  name of key
   * @param testJarPath  path to jar file
   * @return  KV-store key referencing loaded jar-file
   * @throws IOException
   */
  public static Key loadTestJar(String keyName, String testJarPath) throws IOException {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    InputStream is = cl.getResourceAsStream(testJarPath);
    try {
      byte[] ba = IOUtils.toByteArray(is);
      Key key = Key.make(keyName);
      DKV.put(key, new Value(key, ba));
      return key;
    } finally {
      is.close();
    }
  }

  /**
   * Load test function given as Class reference.
   * The method get the class via resources, and store it in K/V under given key name.
   *
   * @param keyName  name of key to store the class in K/V
   * @param klazz  class to save into K/V
   * @return  test function definition
   * @throws IOException
   */
  public static CFuncRef loadTestFunc(String keyName, Class klazz) throws IOException {
    String klazzName = klazz.getName().replaceAll("\\.", "/") + ".class";
    return loadTestFunc("java", keyName, new String[] {klazzName}, klazz.getName());
  }

  public static CFuncRef loadTestFunc(String lang, String keyName, String[] resourcePaths, String entryFuncName) throws IOException {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    // Output jar in-memory jar file
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    JarOutputStream jos = new JarOutputStream(bos);
    // Save all resources from classpath to jar-file

    try {
      for (String resourcePath : resourcePaths) {
        InputStream is = cl.getResourceAsStream(resourcePath);
        byte[] ba;
        try {
           ba = IOUtils.toByteArray(is);
        } finally {
          is.close();
        }
        jos.putNextEntry(new ZipEntry(resourcePath));
        jos.write(ba);
      }
    } finally {
      jos.close();
    }

    Key key = Key.make(keyName);
    DKV.put(key, new Value(key, bos.toByteArray()));
    return new CFuncRef(lang, keyName, entryFuncName);
  }

  public static CFuncRef loadRawTestFunc(String lang, String keyName, String funcName, byte[] rawDef, String pathInJar)
      throws IOException {
    // Output jar in-memory jar file
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    JarOutputStream jos = new JarOutputStream(bos);
    try {
      jos.putNextEntry(new ZipEntry(pathInJar));
      jos.write(rawDef);
    } finally {
      jos.close();
    }

    Key key = Key.make(keyName);
    DKV.put(key, new Value(key, bos.toByteArray()));

    return new CFuncRef(lang, keyName, funcName);
  }

  public static ClassLoader getSkippingClassloader(ClassLoader parent,
                                                   final String[] skipClassNames) {
    return getSkippingClassloader(parent, skipClassNames, new String[] {});
  }
  
  public static ClassLoader getSkippingClassloader(ClassLoader parent,
                                                   final String[] skipClassNames,
                                                   final String[] skipResources) {
    return new ClassLoader(parent) {

      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // For test classes avoid loading from parent
        return ArrayUtils.contains(skipClassNames, name) ? null : super.loadClass(name, resolve);
      }

      @Override
      public URL getResource(String name) {
        return ArrayUtils.contains(skipResources, name) ? null : super.getResource(name);
      }
    };
  }

  static CBlock.CRow mockedRow(int len, double value) {
    CBlock.CRow row = mock(CBlock.CRow.class);
    when(row.len()).thenReturn(len);
    when(row.readDouble(anyInt())).thenReturn(value);
    when(row.readDoubles()).thenReturn(ArrayUtils.constAry(len, value));
    return row;
  }
}
