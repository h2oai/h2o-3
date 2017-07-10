package water.persist;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import water.DKV;
import water.fvec.Vec;

import java.io.IOException;
import java.net.URI;

/**
 * Virtual implementation of a Hadoop FileSystem backed by a Vec.
 * Instances of this class provide read-only access to a single provided Vec.
 * The Vec instance is injected using a ContextAwareConfiguration - the Vec object constitutes the context in this case.
 */
public class VecFileSystem extends FileSystem {

  private static final String KEY_PROP = "fs.hex.vec.key";

  public static Path VEC_PATH = new Path("hex:/vec");

  private Vec _v;

  @Override
  @SuppressWarnings("unchecked")
  public void initialize(URI name, Configuration conf) throws IOException {
    String keyStr = conf.get(KEY_PROP);
    if (keyStr == null) {
      throw new IllegalArgumentException("Configuration needs to a reference to a Vec (set property 'fs.hex.vec.key').");
    }
    _v = DKV.getGet(keyStr);
    super.initialize(name, conf);
  }

  @Override public FileStatus getFileStatus(Path p){
    return new FileStatus(_v.length(),false,1,_v.length()/_v.nChunks(),0l,VecFileSystem.VEC_PATH);
  }

  @Override
  public URI getUri() {
    return URI.create("hex:/");
  }

  @Override
  public FSDataInputStream open(Path f, int bufferSize) throws IOException {
    if (! f.equals(VEC_PATH)) {
      throw new IllegalArgumentException("Invalid path specified, expected " + VEC_PATH);
    }
    return new FSDataInputStream(new VecDataInputStream(_v));
  }

  @Override
  public FSDataOutputStream create(Path f, FsPermission permission, boolean overwrite, int bufferSize, short replication, long blockSize, Progressable progress) throws IOException {
    throw new UnsupportedOperationException("This is a virtual file system backed by a single Vec, 'create' not supported!");
  }

  @Override
  public FSDataOutputStream append(Path f, int bufferSize, Progressable progress) throws IOException {
    throw new UnsupportedOperationException("This is a virtual file system backed by a single Vec, 'append' not supported!");
  }

  @Override
  public boolean rename(Path src, Path dst) throws IOException {
    throw new UnsupportedOperationException("This is a virtual file system backed by a single Vec, 'rename' not supported!");
  }

  @Override
  public boolean delete(Path f, boolean recursive) throws IOException {
    throw new UnsupportedOperationException("This is a virtual file system backed by a single Vec, 'delete' not supported!");
  }

  @Override
  public FileStatus[] listStatus(Path f) throws IOException {
    return new FileStatus[0];
  }

  @Override
  public boolean mkdirs(Path f, FsPermission permission) throws IOException {
    throw new UnsupportedOperationException("This is a virtual file system backed by a single Vec, 'mkdirs' not supported!");
  }

  @Override
  public void setWorkingDirectory(Path newDir) {

  }

  @Override
  public Path getWorkingDirectory() {
    return null;
  }

  public static Configuration makeConfiguration(Vec v) {
    Configuration conf = new Configuration(false);
    conf.setBoolean("fs.hex.impl.disable.cache", true);
    conf.setClass("fs.hex.impl", VecFileSystem.class, FileSystem.class);
    conf.set("fs.hex.vec.key", v._key.toString());
    return conf;
  }

}
