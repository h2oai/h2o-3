package water.persist;

import water.Key;
import water.api.FSIOException;
import water.fvec.Vec;

import java.net.URI;
import java.util.Arrays;

/**
 * Utility class for parsing Azure Blob Storage and ADLS Gen2 URIs into their components.
 *
 * Supported URI formats:
 *   abfs[s]://container@account.dfs.core.windows.net/path/to/blob
 *   wasb[s]://container@account.blob.core.windows.net/path/to/blob
 *
 * The storage account endpoint is derived from the URI authority:
 *   abfs/abfss  → https://account.dfs.core.windows.net
 *   wasb/wasbs  → https://account.blob.core.windows.net
 */
class AzureBlob {

  static final String SCHEME_ABFS  = "abfs";
  static final String SCHEME_ABFSS = "abfss";
  static final String SCHEME_WASB  = "wasb";
  static final String SCHEME_WASBS = "wasbs";

  private final String canonical;   // Original URI string (normalized)
  private final String endpoint;    // https://account.blob.core.windows.net or .dfs.core.windows.net
  private final String container;   // Container/filesystem name
  private final String blobPath;    // Path within the container, without leading '/'
  private final Key   key;

  private AzureBlob(String canonical, String endpoint, String container, String blobPath) {
    this.canonical = canonical;
    this.endpoint  = endpoint;
    this.container = container;
    this.blobPath  = blobPath;
    this.key       = Key.make(canonical);
  }

  /**
   * Parse an Azure URI string into an AzureBlob.
   * Accepts abfs, abfss, wasb, or wasbs schemes.
   */
  static AzureBlob of(String path) {
    URI uri;
    try {
      uri = URI.create(path);
    } catch (IllegalArgumentException e) {
      throw new FSIOException(path, "Cannot parse Azure URI: " + path);
    }
    return of(uri);
  }

  static AzureBlob of(URI uri) {
    String scheme = uri.getScheme();
    if (scheme == null || !isAzureScheme(scheme)) {
      throw new FSIOException(uri.toString(), "Unsupported Azure scheme: " + scheme +
          ". Expected one of: abfs, abfss, wasb, wasbs");
    }

    // Authority is: container@account.dfs.core.windows.net
    String authority = uri.getAuthority();
    if (authority == null || !authority.contains("@")) {
      throw new FSIOException(uri.toString(),
          "Invalid Azure URI authority. Expected format: container@account.{dfs|blob}.core.windows.net");
    }

    int atIdx = authority.indexOf('@');
    String container = authority.substring(0, atIdx);
    String host      = authority.substring(atIdx + 1);

    // Determine HTTPS endpoint: preserve the host (dfs or blob)
    String endpoint = "https://" + host;

    // Blob path is the URI path minus the leading '/'
    String rawPath = uri.getPath();
    String blobPath = (rawPath != null && rawPath.startsWith("/"))
        ? rawPath.substring(1)
        : (rawPath != null ? rawPath : "");

    String canonical = scheme + "://" + authority + (rawPath != null ? rawPath : "");
    return new AzureBlob(canonical, endpoint, container, blobPath);
  }

  static AzureBlob of(Key k) {
    String s = new String(
        (k._kb[0] == Key.CHK)
            ? Arrays.copyOfRange(k._kb, Vec.KEY_PREFIX_LEN, k._kb.length)
            : k._kb
    );
    return of(s);
  }

  static boolean isAzureScheme(String scheme) {
    return SCHEME_ABFS.equals(scheme)  || SCHEME_ABFSS.equals(scheme)
        || SCHEME_WASB.equals(scheme)  || SCHEME_WASBS.equals(scheme);
  }

  static boolean isAzurePath(String path) {
    if (path == null) return false;
    String lower = path.toLowerCase();
    return lower.startsWith(SCHEME_ABFS + "://")
        || lower.startsWith(SCHEME_ABFSS + "://")
        || lower.startsWith(SCHEME_WASB + "://")
        || lower.startsWith(SCHEME_WASBS + "://");
  }

  String getCanonical() { return canonical; }
  String getEndpoint()  { return endpoint; }
  String getContainer() { return container; }
  String getBlobPath()  { return blobPath; }
  Key    getKey()       { return key; }

  @Override
  public String toString() {
    return canonical;
  }
}
