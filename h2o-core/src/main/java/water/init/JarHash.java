package water.init;

import water.util.Log;

import java.io.*;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;


/** Self-jar file MD5 hash, to help make sure clusters are made from the same jar. */
public abstract class JarHash {
  static final String JARPATH; // Path to self-jar, or NULL if we cannot find it
  static public final byte[] JARHASH; // MD5 hash of self-jar, or 0xFF's if we cannot figure it out

  static {
    JARPATH = cl_init_jarpath();
    JARHASH = cl_init_md5(JARPATH);
  }

  private static String cl_init_jarpath() {
    try {
      final String ownJar = JarHash.class.getProtectionDomain().getCodeSource().getLocation().getPath();
      if( ownJar.endsWith(".jar") ) // Path if run from a Jar
        return URLDecoder.decode(ownJar, "UTF-8");
      if( !ownJar.endsWith(".jar/") ) return null; // Not a Jar?
      // Some hadoop versions (like Hortonworks) will unpack the jar file on their own.
      String stem = "h2o.jar";
      File f = new File(ownJar + stem);
      if( !f.exists() ) return null; // Still not a jar
      return URLDecoder.decode(ownJar + stem, "UTF-8");
    } catch( IOException ie ) {
      return null;
    }
  }

  private static byte[] cl_init_md5(String jarpath) {
    byte[] ffHash = new byte[16];
    Arrays.fill(ffHash, (byte)0xFF); // The default non-MD5
    if( jarpath==null ) return ffHash;
    // Ok, pop Jar open & make MD5
    InputStream is = null;
    try {
      is = new FileInputStream(jarpath);
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      byte[] buf = new byte[4096];
      int pos;
      while( (pos = is.read(buf)) > 0 ) md5.update(buf, 0, pos);
      return md5.digest();      // haz md5!
    } catch( IOException | NoSuchAlgorithmException e ) {
      Log.err(e);               // No MD5 algo handy???
    } finally {
      try { if( is != null ) is.close(); } catch( IOException ignore ) { }
    }
    return ffHash;
  }


  private static final ArrayList<File> RESOURCE_FILES = new ArrayList<>();

  public static void registerResourceRoot(File f) {
    if (f.exists()) {
      RESOURCE_FILES.add(f);
    }
  }

  // Look for resources (JS files, PNG's, etc) from the self-jar first, then
  // from a possible local dev build.
  public static InputStream getResource2(String uri) {
    try {
      // If -Dwebdev=1 is set in VM args, we're in front end dev mode, so skip the class loader.
      // This is to allow the front end scripts/styles/templates to be loaded from the build
      //  directory during development.

      // Try all registered locations
      for( File f : RESOURCE_FILES ) {
        File f2 = new File(f,uri);
        if( f2.exists() )
          return new FileInputStream(f2);
      }

      // Fall through to jar file mode.
      ClassLoader cl = ClassLoader.getSystemClassLoader();
      InputStream is = loadResource(uri, cl);
      if (is == null && (cl=Thread.currentThread().getContextClassLoader())!=null) {
        is = loadResource(uri, cl);
      }
      if (is == null && (cl=JarHash.class.getClassLoader())!=null) {
        is = loadResource(uri, cl);
      }
      if (is != null) return is;

    } catch (FileNotFoundException ignore) {}

    Log.warn("Resource not found: " + uri);
    return null;
  }

  private static InputStream loadResource(String uri, ClassLoader cl) {
    Log.info("Trying to load resource " + uri + " via classloader " + cl,false);
    InputStream is = cl.getResourceAsStream("resources/www" + uri);
    if( is != null ) return is;
    is = cl.getResourceAsStream("resources/main/www" + uri);
    if( is != null ) return is;
    // This is the right file location of resource inside jar bundled by gradle
    is = cl.getResourceAsStream("www" + uri);
    return is;
  }
}
