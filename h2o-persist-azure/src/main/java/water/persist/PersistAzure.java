package water.persist;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.ListBlobsOptions;
import water.H2O;
import water.Key;
import water.MemoryManager;
import water.Value;
import water.api.FSIOException;
import water.fvec.AzureFileVec;
import water.fvec.FileVec;
import water.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * H2O persistence backend for Azure Blob Storage and Azure Data Lake Storage Gen2.
 *
 * Supported URI schemes:
 *   abfs[s]://container@account.dfs.core.windows.net/path/to/file   (ADLS Gen2)
 *   wasb[s]://container@account.blob.core.windows.net/path/to/file  (Azure Blob Storage)
 *
 * Authentication is handled by {@link AzureStorageProvider} via {@code DefaultAzureCredential},
 * which supports Azure Workload Identity, Managed Identity, Service Principal, and Azure CLI.
 * No explicit credential configuration is required when running in a Kubernetes pod that has been
 * set up with Azure Workload Identity.
 */
@SuppressWarnings("unused")
public final class PersistAzure extends Persist {

  private final AzureStorageProvider storageProvider = new AzureStorageProvider();

  // -------------------------------------------------------
  // Core DKV persistence: load / store / delete
  // -------------------------------------------------------

  @Override
  public byte[] load(Value v) throws IOException {
    final AzureBlob blob = AzureBlob.of(v._key);
    final Key k = v._key;

    // Determine the byte range to read based on chunk offset within the file
    long offset = 0;
    if (k._kb[0] == Key.CHK) {
      offset = FileVec.chunkOffset(k);
    }

    BlobClient client = getBlobClient(blob);
    try {
      byte[] buf = MemoryManager.malloc1(v._max);
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream(v._max)) {
        client.downloadStreamWithResponse(
            baos,
            new com.azure.storage.blob.models.BlobRange(offset, (long) v._max),
            new com.azure.storage.blob.models.DownloadRetryOptions(),
            null, false, null, null);
        byte[] downloaded = baos.toByteArray();
        System.arraycopy(downloaded, 0, buf, 0, Math.min(downloaded.length, buf.length));
      }
      return buf;
    } catch (Exception e) {
      throw new FSIOException(blob.getCanonical(), e);
    }
  }

  @Override
  public void store(Value v) throws IOException {
    if (!v._key.home()) return;
    throw H2O.unimpl("Azure Blob Storage is read-only from H2O's DKV perspective.");
  }

  @Override
  public void delete(Value v) {
    throw H2O.unimpl("Azure Blob Storage delete via DKV is not supported.");
  }

  @Override
  public void cleanUp() {
    throw H2O.unimpl();
  }

  // -------------------------------------------------------
  // URI / Key mapping
  // -------------------------------------------------------

  @Override
  public Key uriToKey(URI uri) throws IOException {
    final AzureBlob blob = AzureBlob.of(uri);
    BlobClient client = getBlobClient(blob);
    BlobProperties props = client.getProperties();
    return AzureFileVec.make(blob.getCanonical(), props.getBlobSize());
  }

  // -------------------------------------------------------
  // File import: list blobs and register them as lazy FileVecs
  // -------------------------------------------------------

  @Override
  public void importFiles(String path, String pattern,
                          ArrayList<String> files,
                          ArrayList<String> keys,
                          ArrayList<String> fails,
                          ArrayList<String> dels) {
    final AzureBlob blob = AzureBlob.of(path);
    BlobContainerClient containerClient =
        storageProvider.getContainerClient(blob.getEndpoint(), blob.getContainer());

    // If blobPath is empty or ends with '/', treat as a directory prefix listing
    String prefix = blob.getBlobPath();
    try {
      ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix);
      PagedIterable<BlobItem> items = containerClient.listBlobs(options, null);

      for (BlobItem item : items) {
        // Skip "virtual directory" markers that appear when HNS is enabled
        if (Boolean.TRUE.equals(item.isPrefix())) continue;

        String blobName = item.getName();
        String canonical = buildCanonicalUri(blob, blobName);

        long size = item.getProperties() != null && item.getProperties().getContentLength() != null
            ? item.getProperties().getContentLength() : 0L;

        try {
          Key k = AzureFileVec.make(canonical, size);
          keys.add(k.toString());
          files.add(canonical);
        } catch (Throwable t) {
          Log.err("Failed to create Azure file key for: " + canonical, t);
          fails.add(canonical);
        }
      }
    } catch (Throwable t) {
      Log.err("Failed to list Azure blobs at: " + path, t);
      fails.add(path);
    }
  }

  // -------------------------------------------------------
  // Open / stream operations
  // -------------------------------------------------------

  @Override
  public InputStream open(String path) {
    final AzureBlob blob = AzureBlob.of(path);
    BlobClient client = getBlobClient(blob);
    return client.openInputStream();
  }

  @Override
  public boolean isSeekableOpenSupported() {
    return false;
  }

  // -------------------------------------------------------
  // Metadata operations
  // -------------------------------------------------------

  @Override
  public boolean exists(String path) {
    try {
      final AzureBlob blob = AzureBlob.of(path);
      String blobPath = blob.getBlobPath();
      if (blobPath.isEmpty()) {
        // Container-level check
        return storageProvider.getContainerClient(blob.getEndpoint(), blob.getContainer()).exists();
      }
      return getBlobClient(blob).exists();
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public boolean isDirectory(String path) {
    final AzureBlob blob = AzureBlob.of(path);
    // Treat empty blob path (container root) or trailing-slash paths as directories
    return blob.getBlobPath().isEmpty() || path.endsWith("/");
  }

  @Override
  public long length(String path) {
    final AzureBlob blob = AzureBlob.of(path);
    return getBlobClient(blob).getProperties().getBlobSize();
  }

  @Override
  public String getParent(String path) {
    final AzureBlob blob = AzureBlob.of(path);
    String blobPath = blob.getBlobPath();
    int lastSlash = blobPath.lastIndexOf('/');
    if (lastSlash <= 0) {
      // Parent is the container root
      return blob.getCanonical().substring(0,
          blob.getCanonical().length() - blobPath.length());
    }
    String parentBlobPath = blobPath.substring(0, lastSlash);
    return buildCanonicalUri(blob, parentBlobPath);
  }

  @Override
  public PersistEntry[] list(String path) {
    final AzureBlob blob = AzureBlob.of(path);
    BlobContainerClient containerClient =
        storageProvider.getContainerClient(blob.getEndpoint(), blob.getContainer());

    String prefix = blob.getBlobPath();
    int prefixLen = prefix.length();
    ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix);
    List<PersistEntry> results = new ArrayList<>();

    for (BlobItem item : containerClient.listBlobs(options, null)) {
      if (Boolean.TRUE.equals(item.isPrefix())) continue;
      String name = item.getName().substring(prefixLen);
      if (name.startsWith("/")) name = name.substring(1);
      long size = item.getProperties() != null && item.getProperties().getContentLength() != null
          ? item.getProperties().getContentLength() : 0L;
      long lastModified = item.getProperties() != null && item.getProperties().getLastModified() != null
          ? item.getProperties().getLastModified().toInstant().toEpochMilli() : 0L;
      results.add(new PersistEntry(name, size, lastModified));
    }
    return results.toArray(new PersistEntry[0]);
  }

  // -------------------------------------------------------
  // Write operations (limited support)
  // -------------------------------------------------------

  @Override
  public OutputStream create(String path, boolean overwrite) {
    final AzureBlob blob = AzureBlob.of(path);
    BlobClient client = getBlobClient(blob);
    if (!overwrite && client.exists()) {
      throw new IllegalArgumentException("Azure blob already exists: " + path);
    }
    return client.getBlockBlobClient().getBlobOutputStream(overwrite);
  }

  @Override
  public boolean delete(String path) {
    try {
      final AzureBlob blob = AzureBlob.of(path);
      getBlobClient(blob).delete();
      return true;
    } catch (Exception e) {
      Log.warn("Failed to delete Azure blob: " + path, e);
      return false;
    }
  }

  @Override
  public boolean mkdirs(String path) {
    // Azure Blob Storage has no true directories; creating a container is the only meaningful operation
    final AzureBlob blob = AzureBlob.of(path);
    try {
      BlobContainerClient containerClient =
          storageProvider.getContainerClient(blob.getEndpoint(), blob.getContainer());
      if (!containerClient.exists()) {
        containerClient.create();
      }
      return true;
    } catch (Exception e) {
      Log.err("Failed to create Azure container: " + blob.getContainer(), e);
      return false;
    }
  }

  @Override
  public boolean rename(String fromPath, String toPath) {
    // Azure Blob Storage doesn't support atomic rename; copy + delete is required
    final AzureBlob src = AzureBlob.of(fromPath);
    final AzureBlob dst = AzureBlob.of(toPath);
    try {
      BlobClient srcClient = getBlobClient(src);
      BlobClient dstClient = getBlobClient(dst);
      dstClient.copyFromUrl(srcClient.getBlobUrl());
      srcClient.delete();
      return true;
    } catch (Exception e) {
      Log.err("Failed to rename Azure blob from " + fromPath + " to " + toPath, e);
      return false;
    }
  }

  // -------------------------------------------------------
  // Typeahead for the H2O Flow UI path autocomplete
  // -------------------------------------------------------

  @Override
  public List<String> calcTypeaheadMatches(String filter, int limit) {
    List<String> results = new ArrayList<>();
    try {
      AzureBlob blob = AzureBlob.of(filter);
      BlobContainerClient containerClient =
          storageProvider.getContainerClient(blob.getEndpoint(), blob.getContainer());
      String prefix = blob.getBlobPath();
      ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix);

      for (BlobItem item : containerClient.listBlobs(options, null)) {
        if (Boolean.TRUE.equals(item.isPrefix())) continue;
        results.add(buildCanonicalUri(blob, item.getName()));
        if (--limit == 0) break;
      }
    } catch (Exception e) {
      Log.warn("Azure typeahead failed for filter: " + filter, e);
    }
    return results;
  }

  // -------------------------------------------------------
  // Helpers
  // -------------------------------------------------------

  private BlobClient getBlobClient(AzureBlob blob) {
    return storageProvider
        .getContainerClient(blob.getEndpoint(), blob.getContainer())
        .getBlobClient(blob.getBlobPath());
  }

  /**
   * Reconstruct a canonical Azure URI from a parsed {@link AzureBlob} and a (possibly different) blob path.
   * E.g. abfs://mycontainer@myadls.dfs.core.windows.net/some/path
   */
  private static String buildCanonicalUri(AzureBlob templateBlob, String blobPath) {
    // Derive scheme and authority from the canonical URI of the template blob
    String canonical = templateBlob.getCanonical();
    int pathStart = canonical.indexOf('/', canonical.indexOf("://") + 3);
    // Include leading '/' in path
    String prefix = pathStart >= 0 ? canonical.substring(0, pathStart) : canonical;
    return prefix + "/" + blobPath;
  }
}
