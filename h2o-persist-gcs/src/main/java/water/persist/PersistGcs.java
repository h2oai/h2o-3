package water.persist;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.*;
import water.H2O;
import water.Key;
import water.MemoryManager;
import water.Value;
import water.api.FSIOException;
import water.fvec.FileVec;
import water.fvec.GcsFileVec;
import water.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Persistence backend for GCS
 */
public final class PersistGcs extends Persist {

  private static final Storage storage = StorageOptions.getDefaultInstance().getService();

  @Override
  public byte[] load(final Value v) throws IOException {
    final BlobId blobId = GcsBlob.of(v._key).getBlobId();
    
    final byte[] contentBytes = MemoryManager.malloc1(v._max);
    final ByteBuffer wrappingBuffer = ByteBuffer.wrap(contentBytes);
    final Key k = v._key;
    long offset = 0;
    // Skip offset based on chunk number
    if(k._kb[0] == Key.CHK) {
      offset = FileVec.chunkOffset(k); // The offset
    }
    
    final ReadChannel reader = storage.reader(blobId);
    reader.seek(offset);
    reader.read(wrappingBuffer);
    
    return contentBytes;
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
    Log.debug("Storing: " + blob.toString());
    final ByteBuffer buffer = ByteBuffer.wrap(payload);
    storage.create(blob.getBlobInfo()).writer().write(buffer);
  }

  @Override
  public void delete(Value v) {
    final BlobId blobId = GcsBlob.of(v._key).getBlobId();
    Log.debug("Deleting: " + blobId.toString());
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
    // bk[0] is bucket name, bk[1] is file name - file name is optional.
    final String bk[] = GcsBlob.removePrefix(path).split("/", 2);

    if (bk.length < 2) {
      parseBucket(bk[0], files, keys, fails);
    } else {
      try {
        Blob blob = storage.get(bk[0], bk[1]);
        final GcsBlob gcsBlob = GcsBlob.of(blob.getBlobId());
        final Key k = GcsFileVec.make(path, blob.getSize());
        keys.add(k.toString());
        files.add(path);
      } catch (Throwable t) {
        fails.add(path);
      }
    }

  }

  private void parseBucket(String bucketId,
                           ArrayList<String> files,
                           ArrayList<String> keys,
                           ArrayList<String> fails) {
    final Bucket bucket = storage.get(bucketId);
    for (Blob blob : bucket.list().iterateAll()) {
        final GcsBlob gcsBlob = GcsBlob.of(blob.getBlobId());
        Log.debug("Importing: " + gcsBlob.toString());
        try {
          final Key k = GcsFileVec.make(gcsBlob.getCanonical(), blob.getSize());
          keys.add(k.toString());
          files.add(gcsBlob.getCanonical());
        } catch (Throwable t) {
          fails.add(gcsBlob.getCanonical());
        }
    }
  }

  @Override
  public InputStream open(final String path) {
    final GcsBlob gcsBlob = GcsBlob.of(path);
    Log.debug("Opening: " + gcsBlob.toString());
    final Blob blob = storage.get(gcsBlob.getBlobId());
    return new InputStream() {
    final ReadChannel reader = blob.reader();
      @Override
      public int read() throws IOException {
        // very naive version with reading byte by byte
        try {
          ByteBuffer bytes = ByteBuffer.wrap(MemoryManager.malloc1(1));
          int numRed = reader.read(bytes);
          if(numRed == 0) return -1;
          return bytes.get(0);
        } catch (IOException e) {
          throw new FSIOException(path, e);
        }
      }

      @Override
      public int read(byte bytes[], int off, int len) throws IOException {
        Objects.requireNonNull(bytes);
        
        if (off < 0 || len < 0 || len > bytes.length - off) {
          throw new IndexOutOfBoundsException("Length of byte array is " + bytes.length + ". Offset is " + off
              + " and length is " + len);
        } else if (len == 0) {
          return 0;
        }
        final ByteBuffer buffer = ByteBuffer.wrap(bytes, off, len);
        final ReadChannel reader = blob.reader();
        return reader.read(buffer);
      }
    };
  }
}
