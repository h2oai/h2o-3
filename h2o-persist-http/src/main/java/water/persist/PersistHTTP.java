package water.persist;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import water.Key;
import water.MemoryManager;
import water.Value;
import water.fvec.FileVec;
import water.fvec.HTTPFileVec;
import water.fvec.Vec;
import water.util.ByteStreams;
import water.util.HttpResponseStatus;
import water.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

import static water.H2O.OptArgs.SYSTEM_PROP_PREFIX;

/**
 * Implementation of the Persist interface for HTTP/HTTPS data sources
 * Only subset of the API is supported.
 */
public class PersistHTTP extends PersistEagerHTTP {

  private final static String ENABLE_LAZY_LOAD_KEY = SYSTEM_PROP_PREFIX + "persist.http.enableLazyLoad";

  @Override
  public final byte[] load(Value v) throws IOException {
    final byte[] b = MemoryManager.malloc1(v._max);
    final Key k = v._key;
    final long offset = (k._kb[0] == Key.CHK) ? FileVec.chunkOffset(k) : 0L;

    URI source = decodeKey(k);
    HttpRequestBase req = createReq(source, false);
    req.setHeader(HttpHeaders.RANGE, "bytes=" + offset + "-" + (offset+v._max-1));

    try (CloseableHttpClient client = HttpClientBuilder.create().build();
         CloseableHttpResponse response = client.execute(req)) {

      if (response.getStatusLine().getStatusCode() != HttpResponseStatus.PARTIAL_CONTENT.getCode()) {
        throw new IllegalStateException("Expected to retrieve a partial content response (status: " + response.getStatusLine() + ").");
      }
      if (readContentLength(response) != v._max) {
        throw new IllegalStateException("Received incorrect amount of data (expected: " + v._max + "B," +
                " received: " + response.getEntity().getContentLength() + "B).");
      }

      try (InputStream s = response.getEntity().getContent()) {
        ByteStreams.readFully(s, b);
      }
    }

    return b;
  }
  
  static long readContentLength(HttpResponse response) {
    long len = response.getEntity().getContentLength();
    if (len >= 0)
      return len;
    final Header contentRange = response.getFirstHeader(HttpHeaders.CONTENT_RANGE);
    try {
      return parseContentRangeLength(contentRange);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to determine response length: " + contentRange, e);
    }
  }

  private static long parseContentRangeLength(Header contentRange) {
    if (contentRange == null || contentRange.getValue() == null)
      throw new IllegalStateException("Range not available");
    if (!contentRange.getValue().startsWith("bytes"))
      throw new IllegalStateException("Only 'bytes' range is supported: " + contentRange);
    String value = contentRange.getValue().substring("bytes".length()).trim();
    String[] crParts = value.split("/");
    if (crParts.length != 2)
      throw new IllegalStateException("Invalid HTTP response. Cannot parse header " + HttpHeaders.CONTENT_RANGE + ": " + contentRange.getValue());
    String[] range = crParts[0].split("-");
    if (range.length != 2)
      throw new IllegalStateException("Invalid HTTP response. Cannot interpret range value in response header " + HttpHeaders.CONTENT_RANGE + ": " + contentRange.getValue());
    return 1 + Long.valueOf(range[1]) - Long.valueOf(range[0]);
  }

  private static URI decodeKey(Key k) {
    return URI.create(new String((k._kb[0] == Key.CHK) ? Arrays.copyOfRange(k._kb, Vec.KEY_PREFIX_LEN, k._kb.length) : k._kb));
  }

  /**
   * Tests whether a given URI can be accessed using range-requests.
   *
   * @param uri resource identifier
   * @return -1 if range-requests are not supported, otherwise content length of the requested resource
   * @throws IOException when communication fails
   */
  long checkRangeSupport(URI uri) throws IOException {
    HttpRequestBase req = createReq(uri, true);
    try (CloseableHttpClient client = HttpClientBuilder.create().build();
         CloseableHttpResponse response = client.execute(req)) {
      Header acceptRangesHeader = response.getFirstHeader(HttpHeaders.ACCEPT_RANGES);
      Header contentLengthHeader = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
      boolean acceptByteRange = (acceptRangesHeader != null) && "bytes".equalsIgnoreCase(acceptRangesHeader.getValue());
      if (!acceptByteRange || contentLengthHeader == null) {
        return -1L;
      }

      return Long.valueOf(contentLengthHeader.getValue());
    }
  }

  private HttpRequestBase createReq(URI uri, boolean isHead) {
    HttpRequestBase req = isHead ? new HttpHead(uri) : new HttpGet(uri);
    req.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
    return req;
  }

  @Override
  public void importFiles(String path, String pattern,
                          /*OUT*/ ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {

    boolean lazyLoadEnabled = Boolean.parseBoolean(System.getProperty(ENABLE_LAZY_LOAD_KEY, "true"));
    if (lazyLoadEnabled) {
      try {
        URI source = URI.create(path);
        long length = checkRangeSupport(source);
        if (length >= 0) {
          final Key destination_key = HTTPFileVec.make(path, length);
          files.add(path);
          keys.add(destination_key.toString());
          return;
        }
      } catch (Exception e) {
        Log.debug("Failed to detect range support for " + path, e);
      }
    } else
      Log.debug("HTTP lazy load disabled by user.");

    // Fallback - load the key eagerly if range-requests are not supported
    super.importFiles(path, pattern, files, keys, fails, dels);
  }

}
