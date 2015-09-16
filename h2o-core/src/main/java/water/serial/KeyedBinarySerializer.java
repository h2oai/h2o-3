package water.serial;

import com.google.common.io.ByteStreams;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import water.AutoBuffer;
import water.DKV;
import water.H2O;
import water.Keyed;
import water.MemoryManager;
import water.persist.Persist;
import water.util.FileUtils;

/**
 * A generic single Keyed-object serializer targeting file based output on
 * different media.
 *
 * A file and media is referenced by URI.
 */
public class KeyedBinarySerializer extends BinarySerializer<Keyed, URI> {

  /** Do DKV after load put on loaded object if it has defined key. */
  final boolean dkvPutAfterLoad;
  /** Do DKV put after load even the object already exists in DKV */
  final boolean overrideInDkv;
  /* During save override the destination file. */
  final boolean overrideFile;

  public KeyedBinarySerializer() { this(true, true, true); }

  public KeyedBinarySerializer(boolean dkvPutAfterLoad, boolean overrideDkv, boolean overrideFile) {
    this.dkvPutAfterLoad = dkvPutAfterLoad;
    this.overrideInDkv = overrideDkv;
    this.overrideFile = overrideFile;
  }

  @Override public void save(Keyed k, URI uri) throws IOException {
    assert k != null : "Object to save cannot be null!";
    Persist persistLayer = H2O.getPM().getPersistForURI(uri);
    OutputStream fo = null;
    AutoBuffer ab = null;
    // Save data into AB
    k.getBinarySerializer().save(k, saveHeader(k, ab = ab4write()));
    // Flush AB into the file
    try {
      fo = persistLayer.create(uri.toString(), true);
      fo.write(ab.buf());
    } finally {
      FileUtils.close(fo);
    }
  }

  @Override public Keyed load(URI uri) throws IOException {
    Persist persistLayer = H2O.getPM().getPersistForURI(uri);
    InputStream fi = null;
    AutoBuffer ab = null;
    Persist.PersistEntry[] entries = persistLayer.list(uri.toString());
    if (entries.length == 0)
      throw new FileNotFoundException(uri.toString());
    if (entries.length > 1)
      throw new IOException("Found more files matching given URI");
    Persist.PersistEntry entry = entries[0];
    // Load everything to memory and deserialize
    byte buf[] = MemoryManager.malloc1((int) entry._size);
    try {
      fi = persistLayer.open(uri.toString());
      ByteStreams.readFully(fi, buf);
    } finally {
      FileUtils.close(fi);
    }

    Keyed k = loadHeader(ab = ab4read(buf));
    k.getBinarySerializer().load(k, ab);
    if (dkvPutAfterLoad && k._key != null) {
      if (overrideInDkv) {
        DKV.put(k._key, k);
      } else if (DKV.get(k._key) != null) {
        throw new IOException("The object with " + k._key + " key already exists in DKV!");
      }
    }
    return k;
  }

  @Override public Keyed load(Keyed k, URI f) throws IOException {
    throw new UnsupportedOperationException();
  }

  /** Returns AutoBuffer configured for reading from given source. */
  private AutoBuffer ab4read  (byte[] buf) { return new AutoBufferWithClassNames(buf); }
  /** Returns AutoBuffer configured for writing. */
  private AutoBuffer ab4write (/* no destination */) { return new AutoBufferWithClassNames(); }
}
