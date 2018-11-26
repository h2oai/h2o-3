package water.persist;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;

import water.*;
import water.api.FSIOException;
import water.fvec.NFSFileVec;
import water.util.Log;

/**
 * Persistence backend using local file system.
 */
public final class PersistFS extends Persist {
  final File _root;
  final File _dir;

  PersistFS(File root) {
    _root = root;
    _dir = new File(root, "ice" + H2O.API_PORT);
    //deleteRecursive(_dir);
    // Make the directory as-needed
    root.mkdirs();
    if( !(root.isDirectory() && root.canRead() && root.canWrite()) )
      H2O.die("ice_root not a read/writable directory");
  }

  public void cleanUp() { deleteRecursive(_dir); }

  private static void deleteRecursive(File path) {
    if( !path.exists() ) return;
    if( path.isDirectory() )
      for (File f : path.listFiles())
        deleteRecursive(f);
    path.delete();
  }

  /**
   * Get destination file where value is stored
   *
   * @param v  any value from K/V
   * @return  location of file where value is/could be stored
   */
  public File getFile(Value v) {
    return new File(_dir, getIceName(v));
  }

  @Override public byte[] load(Value v) throws IOException {
    File f = getFile(v);
    if( f.length() < v._max ) { // Should be fully on disk...
      // or it's a racey delete of a spilled value
      assert !v.isPersisted() : f.length() + " " + v._max + " " + v._key;
      return null; // No value
    }
    try (FileInputStream s = new FileInputStream(f)) {
        AutoBuffer ab = new AutoBuffer(s.getChannel(), true, Value.ICE);
        byte[] b = ab.getA1(v._max);
        ab.close();
        return b;
      }
  }

  // Store Value v to disk.
  @Override public void store(Value v) throws IOException {
    assert !v.isPersisted();
    File dirs = new File(_dir, getIceDirectory(v._key));
    if( !dirs.mkdirs() && !dirs.exists() )
      throw new java.io.IOException("mkdirs failed making "+dirs);
    try(FileOutputStream s = new FileOutputStream(getFile(v))) {
        byte[] m = v.memOrLoad(); // we are not single threaded anymore
        if( m != null && m.length != v._max ) {
          Log.warn("Value size mismatch? " + v._key + " byte[].len=" + m.length+" v._max="+v._max);
          v._max = m.length; // Implies update of underlying POJO, then re-serializing it without K/V storing it
        }
        new AutoBuffer(s.getChannel(), false, Value.ICE).putA1(m, m.length).close();
      } catch( AutoBuffer.AutoBufferException abe ) {
      throw abe._ioe;
    }
  }

  @Override
  public boolean delete(String path) {
    return new File(URI.create(path)).delete();
  }

  @Override public void delete(Value v) {
    getFile(v).delete();        // Silently ignore errors
    // Attempt to delete empty containing directory
    new File(_dir, getIceDirectory(v._key)).delete();
  }

  @Override public long getUsableSpace() {
    return _root.getUsableSpace();
  }

  @Override public long getTotalSpace() {
    return _root.getTotalSpace();
  }

  @Override
  public Key uriToKey(URI uri) {
    return NFSFileVec.make(new File(uri.toString()))._key;
  }

  @Override
  public ArrayList<String> calcTypeaheadMatches(String src, int limit) {
    assert false;
    return new ArrayList<>();
  }

  @Override
  public void importFiles(String path, String pattern, ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
    assert false;
  }

  @Override
  public OutputStream create(String path, boolean overwrite) {
    File f;
    boolean windowsPath = path.matches("^[a-zA-Z]:.*$");
    if (windowsPath) {
      f = new File(path);
    }
    else {
      f = new File(URI.create(path));
    }
    if (f.exists() && !overwrite)
      throw new FSIOException(path, "File already exists");

    try {
      if (!f.getParentFile().exists()) {
        // Shortcut since we know that this is local FS
        f.getParentFile().mkdirs();
      }
      return new FileOutputStream(f, false);
    } catch (IOException e) {
      throw new FSIOException(path, e);
    }
  }

  @Override
  public PersistEntry[] list(String path) {
    File f = new File(URI.create(path));
    if (f.isFile()) {
      return new PersistEntry[] { getPersistEntry(f) };
    } else if (f.isDirectory()) {
      File[] files = f.listFiles();
      PersistEntry[] entries = new PersistEntry[files.length];
      for (int i = 0; i < files.length; i++) {
        entries[i] = getPersistEntry(files[i]);
      }
      return entries;
    }

    throw H2O.unimpl();
  }

  @Override
  public InputStream open(String path) {
    try {
      File f = new File(URI.create(path));
      return new FileInputStream(f);
    } catch (FileNotFoundException e) {
      throw new FSIOException(path, "File not found");
    } catch (Exception e) {
      throw new FSIOException(path, e);
    }
  }

  @Override
  public boolean mkdirs(String path) {
    return new File(URI.create(path)).mkdirs();
  }

  @Override
  public boolean exists(String path) {
    return new File(URI.create(path)).exists();
  }

  @Override
  public String getParent(String path) {
    return new File(URI.create(path)).getParentFile().toURI().toString();
  }

  @Override
  public boolean isDirectory(String path) {
    return new File(URI.create(path)).isDirectory();
  }

  private PersistEntry getPersistEntry(File f) {
    return new PersistEntry(f.getName(), f.length(), f.lastModified());
  }
}
