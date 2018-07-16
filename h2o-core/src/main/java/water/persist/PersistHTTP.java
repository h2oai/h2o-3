package water.persist;

import com.google.common.io.ByteStreams;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClientBuilder;
import water.H2O;
import water.Key;
import water.MemoryManager;
import water.Value;
import water.fvec.FileVec;
import water.fvec.HTTPFileVec;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.HttpResponseStatus;
import water.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of the Persist interface for HTTP/HTTPS data sources
 * Only subset of the API is supported.
 */
public class PersistHTTP extends Persist {

  @Override
  public byte[] load(Value v) throws IOException {
    final byte[] b = MemoryManager.malloc1(v._max);
    final Key k = v._key;
    final long offset = (k._kb[0] == Key.CHK) ? FileVec.chunkOffset(k) : 0L;

    URI source = decodeKey(k);
    HttpGet req = new HttpGet(source);
    req.setHeader(HttpHeaders.RANGE, "bytes=" + offset + "-" + (offset+v._max-1));

    HttpClient client = HttpClientBuilder.create().build();
    HttpResponse response = client.execute(req);

    if (response.getStatusLine().getStatusCode() != HttpResponseStatus.PARTIAL_CONTENT.getCode()) {
      throw new IllegalStateException("Expected to retrieve a partial content response (status: " + response.getStatusLine() + ").");
    }
    if (response.getEntity().getContentLength() != v._max) {
      throw new IllegalStateException("Received incorrect amount of data (expected: " + v._max + "B," +
              " received: " + response.getEntity().getContentLength() + "B).");
    }

    try (InputStream s = response.getEntity().getContent()) {
      ByteStreams.readFully(s, b);
    }

    return b;
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
    HttpHead req = new HttpHead(uri);

    HttpClient client = HttpClientBuilder.create().build();
    HttpResponse response = client.execute(req);
    Header acceptRangesHeader = response.getFirstHeader(HttpHeaders.ACCEPT_RANGES);
    Header contentLengthHeader = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
    boolean acceptByteRange = (acceptRangesHeader != null) && "bytes".equalsIgnoreCase(acceptRangesHeader.getValue());
    if (! acceptByteRange || contentLengthHeader == null) {
      return -1L;
    }

    return Long.valueOf(contentLengthHeader.getValue());
  }

  @Override
  public void importFiles(String path, String pattern,
                          /*OUT*/ ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
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

    // Fallback - load the key eagerly if range-requests are not supported
    try {
      Key destination_key = FrameUtils.eagerLoadFromHTTP(path);
      files.add(path);
      keys.add(destination_key.toString());
    } catch( Throwable e) {
      fails.add(path); // Fails for e.g. broken sockets silently swallow exceptions and just record the failed path
    }
  }

  /* ********************************************* */
  /* UNIMPLEMENTED methods (inspired by PersistS3) */
  /* ********************************************* */

  @Override
  public Key uriToKey(URI uri) {
    throw new UnsupportedOperationException();
  }

  // Store Value v to disk.
  @Override public void store(Value v) {
    if( !v._key.home() ) return;
    throw H2O.unimpl();         // VA only
  }

  @Override
  public void delete(Value v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void cleanUp() {
    throw H2O.unimpl(); /* user-mode swapping not implemented */
  }

  @Override
  public List<String> calcTypeaheadMatches(String filter, int limit) {
    throw new UnsupportedOperationException();
  }

}
