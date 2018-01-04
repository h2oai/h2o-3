package water.udf;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import water.DKV;
import water.Key;

import static water.udf.DkvClassLoader.DkvUrlStreamHandler.PROTO;

/**
 * An classloader which use content of a jar file stored in K/V store under given key
 * to search and load classes.
 */
class DkvClassLoader extends ClassLoader {

  private final Map<String, byte[]> jarCache;
  private final Key jarKey;

  public DkvClassLoader(CFuncRef cFuncRef, ClassLoader parent) {
    this(cFuncRef.keyName, parent);
  }

  public DkvClassLoader(String jarKeyName, ClassLoader parent) {
    this(Key.make(jarKeyName), parent);
  }
  
  public DkvClassLoader(Key jarKey, ClassLoader parent) {
    super(parent);
    this.jarKey = jarKey;
    this.jarCache = buildJarCache(jarKey);
  }
  
  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    try {
      return super.findClass(name);
    } catch (ClassNotFoundException e) {
      // Parent does not contain the requested class, look into cache we built.
      String path = name.replace('.', '/').concat(".class");
      byte[] klazzBytes = jarCache.get(path);
      if (klazzBytes != null && klazzBytes.length > 0) {
        return defineClass(name, klazzBytes, 0, klazzBytes.length);
      }

      throw new ClassNotFoundException(name);
    }
  }
  @Override
  protected URL findResource(String name) {
    return url(name);
  }

  @Override
  protected Enumeration<URL> findResources(String name) {
    URL url = url(name);
    return url == null
           ? Collections.<URL>emptyEnumeration()
           : Collections.enumeration(Collections.singletonList(url));
  }

  protected URL url(String name) {
    URL url = null;
    byte[] content = jarCache.get(name);
    if (content != null) {
      try {
        // Create a nice URL representing the resource following concept of JarUrl.
        // Note: this is just for sake of clarity, but at this
        // point we use cached content to serve content of URL.
        url = new URL(PROTO, "", -1,
                      this.jarKey + (name.startsWith("/") ? "!" : "!/") + name,
                      new DkvUrlStreamHandler());
      } catch (MalformedURLException e) {
        // Fail quickly since this is not expected to fail
        throw new RuntimeException(e);
      }
    }
    return url;
  }

  static Map<String, byte[]> buildJarCache(Key jarKey) {
    Map<String, byte[]> jarCache = new HashMap<>();
    try(JarInputStream jis = new JarInputStream(new ByteArrayInputStream(DKV.get(jarKey).memOrLoad()))) {
      JarEntry entry = null;
      while ((entry = jis.getNextJarEntry()) != null) {
        if (entry.isDirectory()) continue;
        byte[] content = readJarEntry(jis, entry);
        jarCache.put(entry.getName(), content);
      }
    } catch (IOException e) {
      // Fail quickly
      throw new RuntimeException(e);
    }
    return jarCache;
  }

  static byte[] readJarEntry(JarInputStream jis, JarEntry entry) throws IOException {
    int len = (int) entry.getSize();
    return len > 0 ? IOUtils.toByteArray(jis, len) : IOUtils.toByteArray(jis);
  }

  final class DkvUrlStreamHandler extends URLStreamHandler {
    
    public static final String PROTO = "dkv";

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
      if (!url.getProtocol().equals(PROTO)) {
        throw new IOException("Cannot handle protocol: " + url.getProtocol());
      }
      String path = url.getPath();
      int separator = path.indexOf("!/");
      if (separator == -1) {
        throw new MalformedURLException("Cannot find '!/' in DKV URL!");
      }
      String file = path.substring(separator + 2);
      byte[] content = jarCache.get(file);
      assert content != null : " DkvUrlStreamHandler is not created properly to point to file resource: " + url.toString();

      return new ByteArrayUrlConnection(url, new ByteArrayInputStream(content));
    }
  }

  protected static class ByteArrayUrlConnection extends URLConnection {

    /**
     * The input stream to return for this connection.
     */
    private final InputStream inputStream;

    /**
     * Creates a new byte array URL connection.
     *
     * @param url         The URL that this connection represents.
     * @param inputStream The input stream to return from this connection.
     */
    protected ByteArrayUrlConnection(URL url, InputStream inputStream) {
      super(url);
      this.inputStream = inputStream;
    }

    @Override
    public void connect() {
      connected = true;
    }

    @Override
    public InputStream getInputStream() {
      connect(); // Mimics the semantics of an actual URL connection.
      return inputStream;
    }
  }
}
