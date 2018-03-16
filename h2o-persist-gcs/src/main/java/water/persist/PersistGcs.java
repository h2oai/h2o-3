package water.persist;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Persistence backend for GCS
 */
public final class PersistGcs extends Persist {

  private final Storage storage = StorageOptions.getDefaultInstance().getService();

  @Override
  public byte[] load(final Value v) throws IOException {
    final BlobId blobId = GcsBlob.of(v._key).getBlobId();

    final byte[] contentBytes = MemoryManager.malloc1(v._max);
    final ByteBuffer wrappingBuffer = ByteBuffer.wrap(contentBytes);
    final Key k = v._key;
    long offset = 0;
    // Skip offset based on chunk number
    if (k._kb[0] == Key.CHK) {
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

  private final LoadingCache<String, List<String>> keyCache = CacheBuilder.newBuilder()
      .maximumSize(1000)
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .build(new CacheLoader<String, List<String>>() {
        @Override
        public List<String> load(String key) {
          final List<String> blobs = new ArrayList<>();
          for (Blob b : storage.get(key).list().iterateAll()) {
            blobs.add(b.getName());
          }
          return blobs;
        }
      });

  private final LoadingCache<Object, List<String>> bucketCache = CacheBuilder.newBuilder()
      .maximumSize(1000)
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .build(new CacheLoader<Object, List<String>>() {
        @Override
        public List<String> load(Object key) {
          final List<String> fileNames = new ArrayList<>();
          for (Bucket b : storage.list().iterateAll()) {
            fileNames.add(b.getName());
          }
          return fileNames;
        }
      });

  @Override
  public List<String> calcTypeaheadMatches(String filter, int limit) {
    final String input = GcsBlob.removePrefix(filter);
    final String[] bk = input.split("/", 2);
    List<String> results = new ArrayList<>();
    try {
      if (bk.length == 1) {
        List<String> buckets = bucketCache.get("all");
        for (String s : buckets) {
          results.add(GcsBlob.KEY_PREFIX + s);
        }
      } else if (bk.length == 2) {
        List<String> objects = keyCache.get(bk[0]);
        for (String s : objects) {
          if(s.startsWith(bk[1])) {
            results.add(GcsBlob.KEY_PREFIX + bk[0] + "/" + s);
          }
        }
      }
    } catch (ExecutionException e) {
      Log.err(e);
    }
    return results;
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
        Log.err(t);
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
        Log.err(t);
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
          if (numRed == 0) return -1;
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
        return reader.read(buffer);
      }

      @Override
      public int available() throws IOException {
        return 1;
      }

      @Override
      public void close() throws IOException {
        reader.close();
      }
    };
  }
}
