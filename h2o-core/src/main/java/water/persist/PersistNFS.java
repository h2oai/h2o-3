package water.persist;

import java.io.*;
import java.nio.channels.FileChannel;

import water.*;
import water.util.Log;

// Persistence backend for network file system.
// Just for loading or storing files.
//
// @author cliffc
public final class PersistNFS extends Persist {

  static final String KEY_PREFIX = "nfs:"+File.separator;
  static final int KEY_PREFIX_LENGTH = KEY_PREFIX.length();

  // file implementation -------------------------------------------------------

  /** Key from file */
  public static Key decodeFile(File f) {
    return Key.make(KEY_PREFIX + f.toString());
  }

  // Returns the file for given key.
  private static File getFileForKey(Key k) {
    final int off = k._kb[0]==Key.CHK ? water.fvec.Vec.KEY_PREFIX_LEN : 0;
    assert new String(k._kb,off,KEY_PREFIX_LENGTH).equals(KEY_PREFIX) : "Not an NFS key: "+k;
    String s = new String(k._kb,KEY_PREFIX_LENGTH+off,k._kb.length-(KEY_PREFIX_LENGTH+off));
    return new File(s);
  }

  /** InputStream from a NFS-based Key */
  public static InputStream openStream(Key k) throws IOException {
    return new FileInputStream(getFileForKey(k));
  }

  @Override public byte[] load(Value v) throws IOException {
    assert v.isPersisted();
    // Convert a file chunk into a long-offset from the base file.
    Key k = v._key;
    long skip = k.isChunkKey() ? water.fvec.NFSFileVec.chunkOffset(k) : 0;
    try (FileInputStream s = new FileInputStream(getFileForKey(k))) {
        FileChannel fc = s.getChannel();
        fc.position(skip);
        AutoBuffer ab = new AutoBuffer(fc, true, Value.NFS);
        byte[] b = ab.getA1(v._max);
        ab.close();
        return b;
    }
  }

  @Override public void store(Value v) {
    // Only the home node does persistence on NFS
    if( !v._key.home() ) return;
    // A perhaps useless cutout: the upper layers should test this first.
    if( v.isPersisted() ) return;
    try {
      File f = getFileForKey(v._key);
      if( !f.mkdirs() ) throw new IOException("Unable to create directory "+f);
      try (FileOutputStream s = new FileOutputStream(f)) {
        byte[] m = v.memOrLoad();
        assert (m == null || m.length == v._max); // Assert not saving partial files
        if (m != null) new AutoBuffer(s.getChannel(), false, Value.NFS).putA1(m, m.length).close();
        v.setdsk(); // Set as write-complete to disk
      }
    } catch( IOException e ) { Log.err(e); }
  }

  @Override public void delete(Value v) { throw H2O.fail(); }
}
