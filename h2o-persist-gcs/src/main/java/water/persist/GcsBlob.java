package water.persist;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import water.Key;
import water.api.FSIOException;
import water.fvec.Vec;

import java.net.URI;
import java.util.Arrays;

class GcsBlob {
  static final String KEY_PREFIX = "gs://";
  private static final int KEY_PREFIX_LENGTH = KEY_PREFIX.length();
  private final String canonical;
  private final BlobId blobId;
  private final Key key;

  private GcsBlob(String bucket, String key) {
    canonical = toCanonical(bucket, key);
    blobId = BlobId.of(bucket, key);
    this.key = Key.make(canonical);
  }

  static GcsBlob of(String bucket, String key) {
    return new GcsBlob(bucket, key);
  }

  static GcsBlob of(String s) {
    final String canonical = toCanonical(s);
    final String[] bk = canonical.substring(KEY_PREFIX_LENGTH).split("/", 2);
    if (bk.length == 2) {
      return GcsBlob.of(bk[0], bk[1]);
    } else {
      throw new FSIOException(s, "Cannot parse blob name");
    }
  }

  static GcsBlob of(URI uri) {
    return GcsBlob.of(uri.toString());
  }

  static GcsBlob of(Key k) {
    final String s = new String((k._kb[0] == Key.CHK) ? Arrays.copyOfRange(k._kb, Vec.KEY_PREFIX_LEN, k._kb.length) : k._kb);
    return GcsBlob.of(s);
  }

  static GcsBlob of(BlobId blobId) {
    return GcsBlob.of(blobId.getBucket(), blobId.getName());
  }

  static GcsBlob of(Blob blob) {
    return GcsBlob.of(blob.getBlobId());
  }

  String getCanonical() {
    return canonical;
  }

  BlobId getBlobId() {
    return blobId;
  }

  BlobInfo getBlobInfo() {
    return BlobInfo.newBuilder(blobId).build();
  }

  Key getKey() {
    return key;
  }

  static String toCanonical(String s) {
    if (s.startsWith(KEY_PREFIX)) {
      return s;
    } else if (s.startsWith("/")) {
      return KEY_PREFIX + s.substring(1);
    } else {
      return KEY_PREFIX + s;
    }
  }

  private static String toCanonical(String bucket, String key) {
    return KEY_PREFIX + bucket + '/' + key;
  }

  static String removePrefix(String s) {
    if (s.startsWith(KEY_PREFIX)) {
      return s.substring(KEY_PREFIX_LENGTH);
    } else if (s.startsWith("/")) {
      return s.substring(1);
    } else {
      return s;
    }
  }
}
