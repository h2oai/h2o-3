package water.init;

import java.io.*;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import water.util.Log;


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
    } catch( IOException _ ) {
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

  // Look for resources (JS files, PNG's, etc) from the self-jar first, then
  // from a possible local dev build.
  public static InputStream getResource2(String uri) {
    try {
      ClassLoader systemLoader = ClassLoader.getSystemClassLoader();

      // Jar file mode.
      if (JARPATH != null) {
        return systemLoader.getResourceAsStream("www" + uri);
      }

      // Class path mode.
      File resources;
      String h2oClasses = JarHash.class.getProtectionDomain().getCodeSource().getLocation().getPath();
      resources = new File(h2oClasses + "www");
      if (resources.exists()) {
        return new FileInputStream(new File(resources, uri));
      }

      // Source file mode.
      resources = new File("src/main/resources/www");
      if (resources.exists()) {
        return new FileInputStream(new File(resources, uri));
      }
    }
    catch (FileNotFoundException xe) {}

    Log.info("Resource not found: " + uri);
    return null;
  }
}
