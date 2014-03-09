package water.persist;

import java.io.*;

import water.*;
import water.util.Log;

/**
 * Persistence backend using local file system.
 */
public final class PersistFS extends Persist {
  public final File _dir;

  PersistFS(File root) {
    _dir = new File(root, "ice" + H2O.API_PORT);
    // Make the directory as-needed
    root.mkdirs();
    if( !(root.isDirectory() && root.canRead() && root.canWrite()) )
      H2O.die("ice_root not a read/writable directory");
  }

  @Override public String getPath() { return _dir.toString(); }
  @Override public void clear() { clear(_dir); }
  private void clear(File f) {
    File[] cs = f.listFiles();
    if( cs != null ) {
      for( File c : cs ) {
        if( c.isDirectory() ) clear(c);
        c.delete();
      }
    }
  }

  private File getFile(Value v) {
    return new File(_dir, getIceName(v));
  }

  @Override public byte[] load(Value v) {
    File f = getFile(v);
    if( f.length() < v._max ) { // Should be fully on disk...
      // or it's a racey delete of a spilled value
      assert !v.isPersisted() : f.length() + " " + v._max + " " + v._key;
      return null; // No value
    }
    try {
      FileInputStream s = new FileInputStream(f);
      try {
        AutoBuffer ab = new AutoBuffer(s.getChannel(), true, Value.ICE);
        byte[] b = ab.getA1(v._max);
        ab.close();
        return b;
      } finally {
        s.close();
      }
    } catch( IOException e ) {  // Broken disk / short-file???
      throw Log.throwErr(e);
    }
  }

  // Store Value v to disk.
  @Override public void store(Value v) {
    assert !v.isPersisted();
    new File(_dir, getIceDirectory(v._key)).mkdirs();
    // Nuke any prior file.
    FileOutputStream s = null;
    try {
      s = new FileOutputStream(getFile(v));
    } catch( FileNotFoundException e ) {
      Log.throwErr(e);
    }
    try {
      byte[] m = v.memOrLoad(); // we are not single threaded anymore
      assert m != null && m.length == v._max : " " + v._key + " " + m; // Assert not saving partial files
      new AutoBuffer(s.getChannel(), false, Value.ICE).putA1(m, m.length).close();
      v.setdsk();             // Set as write-complete to disk
    } finally {
      if( s!=null ) try { s.close(); } catch( IOException _ ) { }
    }
  }

  @Override public void delete(Value v) {
    assert !v.isPersisted();   // Upper layers already cleared out
    File f = getFile(v);
    f.delete();
    if( v.isArray() ) { // Also nuke directory if the top-level ValueArray dies
      f = new File(_dir.toString(), getIceDirectory(v._key));
      f.delete();
    }
  }

  @Override public long getUsableSpace() {
    return _dir.getUsableSpace();
  }

  @Override public long getTotalSpace() {
    return _dir.getTotalSpace();
  }
}
