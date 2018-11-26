package water.server;

import water.H2O;
import water.H2OError;
import water.api.schemas3.H2OErrorV3;
import water.exceptions.H2OAbstractRuntimeException;
import water.exceptions.H2OFailException;
import water.util.HttpResponseStatus;
import water.util.Log;
import water.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Arrays;

/**
 * Utilities supporting HTTP server-side functionality, without depending on specific version of Jetty, or on Jetty at all.
 */
public class ServletUtils {
  private static final ThreadLocal<Long> _startMillis = new ThreadLocal<>();
  private static final ThreadLocal<Integer> _status = new ThreadLocal<>();
  public static final ThreadLocal<String> _userAgent = new ThreadLocal<>();

  private ServletUtils() {
    // not instantiable
  }

  /**
   * Called from {@link water.JettyHTTPD}.
   */
  public static void startRequestLifecycle() {
    _startMillis.set(System.currentTimeMillis());
    _status.set(999);
  }

  private static void setStatus(int sc) {
    _status.set(sc);
  }

  private static int getStatus() {
    return _status.get();
  }

  protected static long getStartMillis() {
    return _startMillis.get();
  }

  public static void startTransaction(String userAgent) {
    _userAgent.set(userAgent);
  }

  public static void endTransaction() {
    _userAgent.remove();
  }

  /**
   * @return Thread-local User-Agent for this transaction.
   */
  public static String getUserAgent() {
    return _userAgent.get();
  }

  public static void setResponseStatus(HttpServletResponse response, int sc) {
    setStatus(sc);
    response.setStatus(sc);
  }

  public static void sendResponseError(HttpServletResponse response, int sc, String msg) throws java.io.IOException {
    setStatus(sc);
    response.sendError(sc, msg);
  }

  public static InputStream extractPartInputStream (HttpServletRequest request, HttpServletResponse response) throws
      IOException {
    String ct = request.getContentType();
    if (! ct.startsWith("multipart/form-data")) {
      setResponseStatus(response, HttpServletResponse.SC_BAD_REQUEST);
      response.getWriter().write("Content type must be multipart/form-data");
      return null;
    }

    String boundaryString;
    int idx = ct.indexOf("boundary=");
    if (idx < 0) {
      setResponseStatus(response, HttpServletResponse.SC_BAD_REQUEST);
      response.getWriter().write("Boundary missing");
      return null;
    }

    boundaryString = ct.substring(idx + "boundary=".length());
    byte[] boundary = StringUtils.bytesOf(boundaryString);

    // Consume headers of the mime part.
    InputStream is = request.getInputStream();
    String line = readLine(is);
    while ((line != null) && (line.trim().length()>0)) {
      line = readLine(is);
    }

    return new InputStreamWrapper(is, boundary);
  }

  public static void sendErrorResponse(HttpServletResponse response, Exception e, String uri) {
    if (e instanceof H2OFailException) {
      H2OFailException ee = (H2OFailException) e;
      H2OError error = ee.toH2OError(uri);

      Log.fatal("Caught exception (fatal to the cluster): " + error.toString());
      throw(H2O.fail(error.toString()));
    }
    else if (e instanceof H2OAbstractRuntimeException) {
      H2OAbstractRuntimeException ee = (H2OAbstractRuntimeException) e;
      H2OError error = ee.toH2OError(uri);

      Log.warn("Caught exception: " + error.toString());
      setResponseStatus(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

      // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
      try {
        @SuppressWarnings("unchecked")
        String s = new H2OErrorV3().fillFromImpl(error).toJsonString();
        response.getWriter().write(s);
      }
      catch (Exception ignore) {
        ignore.printStackTrace();
      }
    }
    else { // make sure that no Exception is ever thrown out from the request
      H2OError error = new H2OError(e, uri);

      // some special cases for which we return 400 because it's likely a problem with the client request:
      if (e instanceof IllegalArgumentException)
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      else if (e instanceof FileNotFoundException)
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      else if (e instanceof MalformedURLException)
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      setResponseStatus(response, error._http_status);

      Log.warn("Caught exception: " + error.toString());

      // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
      try {
        @SuppressWarnings("unchecked")
        String s = new H2OErrorV3().fillFromImpl(error).toJsonString();
        response.getWriter().write(s);
      }
      catch (Exception ignore) {
        ignore.printStackTrace();
      }
    }
  }

  public static String getDecodedUri(HttpServletRequest request) {
    try {
      return URLDecoder.decode(request.getRequestURI(), "UTF-8");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean isXhrRequest(final HttpServletRequest request) {
    final String requestedWithHeader = request.getHeader("X-Requested-With");
    return "XMLHttpRequest".equals(requestedWithHeader);
  }

  public static void setCommonResponseHttpHeaders(HttpServletResponse response, final boolean xhrRequest) {
    if (xhrRequest) {
      response.setHeader("Cache-Control", "no-cache");
    }
    response.setHeader("X-h2o-build-project-version", H2O.ABV.projectVersion());
    response.setHeader("X-h2o-rest-api-version-max", Integer.toString(water.api.RequestServer.H2O_REST_API_VERSION));
    response.setHeader("X-h2o-cluster-id", Long.toString(H2O.CLUSTER_ID));
    response.setHeader("X-h2o-cluster-good", Boolean.toString(H2O.CLOUD.healthy()));
    response.setHeader("X-h2o-context-path", sanatizeContextPath(H2O.ARGS.context_path));
  }

  private static String sanatizeContextPath(String context_path) {
    if(null == context_path || context_path.isEmpty()) {
      return "/";
    }
    return context_path + "/";
  }

  @SuppressWarnings("unused")
  public static void logRequest(String method, HttpServletRequest request, HttpServletResponse response) {
    Log.httpd(method, request.getRequestURI(), getStatus(), System.currentTimeMillis() - getStartMillis());
  }

  private static String readLine(InputStream in) throws IOException {
    StringBuilder sb = new StringBuilder();
    byte[] mem = new byte[1024];
    while (true) {
      int sz = readBufOrLine(in,mem);
      sb.append(new String(mem,0,sz));
      if (sz < mem.length)
        break;
      if (mem[sz-1]=='\n')
        break;
    }
    if (sb.length()==0)
      return null;
    String line = sb.toString();
    if (line.endsWith("\r\n"))
      line = line.substring(0,line.length()-2);
    else if (line.endsWith("\n"))
      line = line.substring(0,line.length()-1);
    return line;
  }

  @SuppressWarnings("all")
  private static int readBufOrLine(InputStream in, byte[] mem) throws IOException {
    byte[] bb = new byte[1];
    int sz = 0;
    while (true) {
      byte b;
      byte b2;
      if (sz==mem.length)
        break;
      try {
        in.read(bb,0,1);
        b = bb[0];
        mem[sz++] = b;
      } catch (EOFException e) {
        break;
      }
      if (b == '\n')
        break;
      if (sz==mem.length)
        break;
      if (b == '\r') {
        try {
          in.read(bb,0,1);
          b2 = bb[0];
          mem[sz++] = b2;
        } catch (EOFException e) {
          break;
        }
        if (b2 == '\n')
          break;
      }
    }
    return sz;
  }

  @SuppressWarnings("all")
  private static final class InputStreamWrapper extends InputStream {
    static final byte[] BOUNDARY_PREFIX = { '\r', '\n', '-', '-' };
    final InputStream _wrapped;
    final byte[] _boundary;
    final byte[] _lookAheadBuf;
    int _lookAheadLen;

    public InputStreamWrapper(InputStream is, byte[] boundary) {
      _wrapped = is;
      _boundary = Arrays.copyOf(BOUNDARY_PREFIX, BOUNDARY_PREFIX.length + boundary.length);
      System.arraycopy(boundary, 0, _boundary, BOUNDARY_PREFIX.length, boundary.length);
      _lookAheadBuf = new byte[_boundary.length];
      _lookAheadLen = 0;
    }

    @Override public void close() throws IOException { _wrapped.close(); }
    @Override public int available() throws IOException { return _wrapped.available(); }
    @Override public long skip(long n) throws IOException { return _wrapped.skip(n); }
    @Override public void mark(int readlimit) { _wrapped.mark(readlimit); }
    @Override public void reset() throws IOException { _wrapped.reset(); }
    @Override public boolean markSupported() { return _wrapped.markSupported(); }

    @Override public int read() throws IOException { throw new UnsupportedOperationException(); }
    @Override public int read(byte[] b) throws IOException { return read(b, 0, b.length); }
    @Override public int read(byte[] b, int off, int len) throws IOException {
      if(_lookAheadLen == -1)
        return -1;
      int readLen = readInternal(b, off, len);
      if (readLen != -1) {
        int pos = findBoundary(b, off, readLen);
        if (pos != -1) {
          _lookAheadLen = -1;
          return pos - off;
        }
      }
      return readLen;
    }

    private int readInternal(byte b[], int off, int len) throws IOException {
      if (len < _lookAheadLen ) {
        System.arraycopy(_lookAheadBuf, 0, b, off, len);
        _lookAheadLen -= len;
        System.arraycopy(_lookAheadBuf, len, _lookAheadBuf, 0, _lookAheadLen);
        return len;
      }

      if (_lookAheadLen > 0) {
        System.arraycopy(_lookAheadBuf, 0, b, off, _lookAheadLen);
        off += _lookAheadLen;
        len -= _lookAheadLen;
        int r = Math.max(_wrapped.read(b, off, len), 0) + _lookAheadLen;
        _lookAheadLen = 0;
        return r;
      } else {
        return _wrapped.read(b, off, len);
      }
    }

    private int findBoundary(byte[] b, int off, int len) throws IOException {
      int bidx = -1; // start index of boundary
      int idx = 0; // actual index in boundary[]
      for(int i = off; i < off+len; i++) {
        if (_boundary[idx] != b[i]) { // reset
          idx = 0;
          bidx = -1;
        }
        if (_boundary[idx] == b[i]) {
          if (idx == 0) bidx = i;
          if (++idx == _boundary.length) return bidx; // boundary found
        }
      }
      if (bidx != -1) { // it seems that there is boundary but we did not match all boundary length
        assert _lookAheadLen == 0; // There should not be not read lookahead
        _lookAheadLen = _boundary.length - idx;
        int readLen = _wrapped.read(_lookAheadBuf, 0, _lookAheadLen);
        if (readLen < _boundary.length - idx) { // There is not enough data to match boundary
          _lookAheadLen = readLen;
          return -1;
        }
        for (int i = 0; i < _boundary.length - idx; i++)
          if (_boundary[i+idx] != _lookAheadBuf[i])
            return -1; // There is not boundary => preserve lookahead buffer
        // Boundary found => do not care about lookAheadBuffer since all remaining data are ignored
      }

      return bidx;
    }
  }
}
