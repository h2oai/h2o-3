
/** Self-jar file MD5 hash, to help make sure clusters are made from the same jar. */
public abstract class JarHash {
  public static final String JARPATH;
  public static final byte[] JARHASH;

  static {
    String path = null;
    byte[] jarHash = new byte[16];
    Arrays.fill(jarHash, (byte)0xFF);

    try {
      final String ownJar = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
      // do nothing if not run from jar
      if( ownJar.endsWith(".jar") ) {
        path = URLDecoder.decode(ownJar, "UTF-8");
      } else {
        if( !ownJar.endsWith(".jar/") ) return;
        // Some hadoop versions (like Hortonworks) will unpack the jar file on their own.
        String stem = "h2o.jar";
        File f = new File(ownJar + stem);
        if( !f.exists() ) return;
        path = URLDecoder.decode(ownJar + stem, "UTF-8");
      }

      InputStream is = new FileInputStream(path);
....
      try {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] buf = new byte[4096];
        int pos;
        while( (pos = is.read(buf)) > 0 ) md5.update(buf, 0, pos);
        return md5.digest();
      } catch( NoSuchAlgorithmException e ) {
        throw  Log.errRTExcept(e);
      } finally {
        is.close();
      }
      _h2oJar = new ZipFile(_jarPath);
    } finally {
      JARPATH = path;
      JARHASH = jarHash;
    }
  }

}
