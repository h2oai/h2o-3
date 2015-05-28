package water.serial;

import java.io.IOException;

import org.apache.hadoop.fs.*;

import water.*;

/**
 * Model serializer targeting file based output.
 */
public class Model2HDFSBinarySerializer extends BinarySerializer<Model, Path, Path> {

  private final FileSystem _hfs;
  private final boolean _force;

  public Model2HDFSBinarySerializer(FileSystem fs, boolean force) {
    _hfs = fs;
    _force = force;
  }

  @Override public void save(Model m, Path f) throws IOException {
    assert m!=null : "Model cannot be null!";
    AutoBuffer ab = null;
    // Save given mode to autobuffer

    m.getModelSerializer().save(m,
        saveHeader( m,ab=ab4write() ) );
    // Spill it into disk
    _hfs.mkdirs(f.getParent());
    FSDataOutputStream os = _hfs.create(f, _force);
    try {
      os.write(ab.buf());
    } finally {
      os.close();
    }
  }

  @Override public Model load(Path f) throws IOException {
    FSDataInputStream is = _hfs.open(f);
    byte buf[] = MemoryManager.malloc1((int) _hfs.getContentSummary(f).getLength());
    try {
      is.readFully(buf);
    } finally { is.close(); }
    AutoBuffer ab=ab4read(buf);
    Model m = loadHeader(ab);
    m.getModelSerializer().load(m, ab);
    if (m._key!=null) {
      DKV.put(m._key, m);
    }
    return m;
  }

  @Override public Model load(Model m, Path f) throws IOException {
    throw new UnsupportedOperationException();
  }

  private AutoBuffer ab4read  (byte[] b) { return new AutoBufferWithoutTypeIds(b); }
  private AutoBuffer ab4write () { return new AutoBufferWithoutTypeIds(); }
}
