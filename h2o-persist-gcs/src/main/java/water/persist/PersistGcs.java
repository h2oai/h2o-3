package water.persist;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.*;
import water.H2O;
import water.Key;
import water.Value;
import water.api.FSIOException;
import water.fvec.GcsFileVec;
import water.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistence backend for GCS
 */
public final class PersistGcs extends Persist {

  private static final Storage storage = StorageOptions.getDefaultInstance().getService();

  @Override
  public byte[] load(Value v) throws IOException {
    final BlobId blobId = GcsBlob.of(v._key).getBlobId();
    Log.info("Loading: " + blobId.toString());
    return storage.get(blobId).getContent();
  }

  @Override
  public Key uriToKey(URI uri) throws IOException {
    final GcsBlob blob = GcsBlob.of(uri);
    final Long contentSize = storage.get(blob.getBlobId()).getSize();
    return GcsFileVec.make(blob.getCanonical(), contentSize);
  }

  @Override
  public void store(Value v) throws IOException {
    if (!v._key.home()) return;
    final byte payload[] = v.memOrLoad();
    final GcsBlob blob = GcsBlob.of(v._key);
    Log.info("Storing: " + blob.toString());
    final ByteBuffer buffer = ByteBuffer.wrap(payload);
    storage.create(blob.getBlobInfo()).writer().write(buffer);
  }

  @Override
  public void delete(Value v) {
    final BlobId blobId = GcsBlob.of(v._key).getBlobId();
    Log.info("Deleting: " + blobId.toString());
    storage.get(blobId).delete();
  }

  @Override
  public void cleanUp() {
    throw H2O.unimpl();
  }

  private static final Map<String, StringCache> keyCache = new HashMap<>();
  private static final StringCache bucketCache = new StringCache() {
    @Override
    List<String> update() {
      final List<String> c = new ArrayList<>();
      for (Bucket b : storage.list().iterateAll()) {
        c.add(b.getName());
      }
      return c;
    }
  };

  @Override
  public List<String> calcTypeaheadMatches(String filter, int limit) {
    final String input = GcsBlob.removePrefix(filter);
    final String[] bk = input.split("/", 2);
    if (bk.length == 1) {
      return bucketCache.fetch(GcsBlob.KEY_PREFIX, bk[0], limit);
    } else if (bk.length == 2) {
      if (!keyCache.containsKey(bk[0])) {
        StringCache sc = new StringCache() {
          @Override
          List<String> update() {
            final List<String> cache = new ArrayList<>();
            for (Blob b : storage.get(bk[0]).list().iterateAll()) {
              cache.add(b.getName());
            }
            return cache;
          }
        };
        keyCache.put(bk[0], sc);
      }
      return keyCache.get(bk[0]).fetch(GcsBlob.KEY_PREFIX + bk[0] + "/", bk[1], limit);
    }
    return new ArrayList<>(0);
  }

  @Override
  public void importFiles(String path,
                          String pattern,
                          ArrayList<String> files,
                          ArrayList<String> keys,
                          ArrayList<String> fails,
                          ArrayList<String> dels) {
    final String bk[] = GcsBlob.removePrefix(path).split("/", 2);
    final Bucket bucket = storage.get(bk[0]);
    for (Blob blob : bucket.list().iterateAll()) {
      if (bk.length == 1 || blob.getName().startsWith(bk[1])) {
        final GcsBlob gcsBlob = GcsBlob.of(blob.getBlobId());
        Log.info("Importing: " + gcsBlob.toString());
        try {
          final Key k = GcsFileVec.make(gcsBlob.getCanonical(), blob.getSize());
          keys.add(k.toString());
          files.add(gcsBlob.getCanonical());
        } catch (Throwable t) {
          fails.add(gcsBlob.getCanonical());
        }
      }
    }
  }

  @Override
  public InputStream open(final String path) {
    final GcsBlob gcsBlob = GcsBlob.of(path);
    Log.info("Openning: " + gcsBlob.toString());
    final Blob blob = storage.get(gcsBlob.getBlobId());
    return new InputStream() {
      @Override
      public int read() throws IOException {
        // very naive version with reading byte by byte
        int res = -1;
        try (ReadChannel reader = blob.reader()) {
          ByteBuffer bytes = ByteBuffer.allocate(1);
          while (reader.read(bytes) > 0) {
            res = bytes.getInt();
          }
        } catch (IOException e) {
          throw new FSIOException(path, e);
        }
        return res;
      }

      @Override
      public int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
          throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
          throw new IndexOutOfBoundsException();
        } else if (len == 0) {
          return 0;
        }
        final ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
        final ReadChannel reader = blob.reader();
        return reader.read(buffer);
      }
    };
  }
}
