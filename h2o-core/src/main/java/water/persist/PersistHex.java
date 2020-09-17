package water.persist;

import water.DKV;
import water.H2O;
import water.Key;
import water.Value;
import water.fvec.C1NChunk;
import water.util.FrameUtils;
import water.util.Log;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class PersistHex extends Persist {

  static final String HEX_PATH_PREFIX = PersistManager.Schemes.HEX + "://";

  Key<?> fromHexPath(String path) {
    Key<?> key = Key.make(path.substring(HEX_PATH_PREFIX.length()));
    if (! key.isChunkKey()) {
      throw new IllegalArgumentException("Only Chunk keys are supported for HEX schema");
    }
    return key;
  }

  InputStream open(Key<?> key) {
    byte[] bytes = ((C1NChunk) DKV.getGet(key)).getBytes();
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public InputStream open(String path) {
    return open(fromHexPath(path));
  }

  @Override
  public OutputStream create(String path, boolean overwrite) {
    Key<?> ck = fromHexPath(path);
    return new ByteChunkOutputStream(ck);
  }

  private static class ByteChunkOutputStream extends ByteArrayOutputStream {
    private final Key<?> _chunkKey;

    public ByteChunkOutputStream(Key<?> chunkKey) {
      super();
      _chunkKey = chunkKey;
    }

    @Override
    public void close() throws IOException {
      super.close();
      byte[] myBytes = toByteArray();
      DKV.put(_chunkKey, new Value(_chunkKey, new C1NChunk(myBytes)));
    }
  }

  /* ********************************************* */
  /* UNIMPLEMENTED methods (inspired by PersistS3) */
  /* ********************************************* */

  @Override
  public List<String> calcTypeaheadMatches(String filter, int limit) {
    throw H2O.unimpl();
  }

  @Override
  public void importFiles(String path, String pattern, ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
    throw H2O.unimpl();
  }

  @Override
  public Key uriToKey(URI uri) {
    throw H2O.unimpl();
  }

  // Store Value v to disk.
  @Override public void store(Value v) {
    if( !v._key.home() ) return;
    throw H2O.unimpl();         // VA only
  }

  @Override
  public void delete(Value v) {
    throw H2O.unimpl();
  }

  @Override
  public void cleanUp() {
    throw H2O.unimpl();
  }

  @Override
  public byte[] load(Value v) {
    throw H2O.unimpl();
  }

}
