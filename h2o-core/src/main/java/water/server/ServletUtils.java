package water.server;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import water.H2O;
import water.H2OError;
import water.api.RapidsHandler;
import water.api.RequestServer;
import water.api.schemas3.H2OErrorV3;
import water.exceptions.H2OAbstractRuntimeException;
import water.exceptions.H2OFailException;
import water.rapids.Session;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities supporting HTTP server-side functionality, without depending on specific version of Jetty, or on Jetty at all.
 */
public class ServletUtils {
  
  private static final Logger LOG = LogManager.getLogger(RequestServer.class);

  /**
   * Adds headers that disable browser-side Cross-Origin Resource checks - allows requests
   * to this server from any origin.
   */
  private static final boolean DISABLE_CORS = Boolean.getBoolean(H2O.OptArgs.SYSTEM_PROP_PREFIX + "disable.cors");

  /**
   * Sets header that allows usage in i-frame. Off by default for security reasons.
   */
  private static final boolean ENABLE_XFRAME_SAMEORIGIN = Boolean.getBoolean(H2O.OptArgs.SYSTEM_PROP_PREFIX + "enable.xframe.sameorigin");

  private static final String TRACE_METHOD = "TRACE";

  private static final ThreadLocal<Long> _startMillis = new ThreadLocal<>();
  private static final ThreadLocal<Integer> _status = new ThreadLocal<>();
  private static final ThreadLocal<Transaction> _transaction = new ThreadLocal<>();

  private ServletUtils() {
    // not instantiable
  }

  /**
   * Called from JettyHTTPD.
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

  private static long getStartMillis() {
    return _startMillis.get();
  }

  public static void startTransaction(String userAgent, String sessionKey) {
    _transaction.set(new Transaction(userAgent, sessionKey));
  }

  public static void endTransaction() {
    _transaction.remove();
  }

  private static class Transaction {
    final String _userAgent;
    final String _sessionKey;

    Transaction(String userAgent, String sessionKey) {
      _userAgent = userAgent;
      _sessionKey = sessionKey;
    }
  }

  /**
   * @return Thread-local User-Agent for this transaction.
   */
  public static String getUserAgent() {
    Transaction t = _transaction.get();
    return t != null ? t._userAgent : null;
  }

  public static String getSessionProperty(String key, String defaultValue) {
    Transaction t = _transaction.get();
    if (t == null || t._sessionKey == null) {
      return defaultValue;
    }
    Session session = RapidsHandler.getSession(t._sessionKey);
    if (session == null) {
      return defaultValue;
    }
    return session.getProperty(key, defaultValue);
  }

  public static void setResponseStatus(HttpServletResponse response, int sc) {
    setStatus(sc);
    response.setStatus(sc);
  }

  public static void sendResponseError(HttpServletResponse response, int sc, String msg) throws java.io.IOException {
    setStatus(sc);
    response.sendError(sc, msg);
  }

  public static InputStream extractInputStream(HttpServletRequest request, HttpServletResponse response) throws IOException {
    final InputStream is;
    final String contentType = request.getContentType();
    
    // The Python client sends requests with null content-type when uploading large files,
    // whereas Sparkling Water proxy sends requests with content-type set to application/octet-stream.
    if (contentType == null || contentType.equals("application/octet-stream")) {
      is = request.getInputStream();
    } else {
      is = extractPartInputStream(request, response);
    }
    return is;
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

  public static void sendErrorResponse(HttpServletResponse response, Exception exception, String uri) {
    if (exception instanceof H2OFailException) {
      final H2OFailException ee = (H2OFailException) exception;
      final H2OError error = ee.toH2OError(uri);

      Log.fatal("Caught exception (fatal to the cluster): " + error.toString());
      throw(H2O.fail(error.toString()));
    }
    else if (exception instanceof H2OAbstractRuntimeException) {
      final H2OAbstractRuntimeException ee = (H2OAbstractRuntimeException) exception;
      final H2OError error = ee.toH2OError(uri);

      Log.warn("Caught exception: " + error.toString());
      setResponseStatus(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      writeResponseErrorBody(response, error);
    }
    else { // make sure that no Exception is ever thrown out from the request
      final H2OError error = new H2OError(exception, uri);

      // some special cases for which we return 400 because it's likely a problem with the client request:
      if (exception instanceof IllegalArgumentException) {
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      } else if (exception instanceof FileNotFoundException) {
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      } else if (exception instanceof MalformedURLException) {
        error._http_status = HttpResponseStatus.BAD_REQUEST.getCode();
      }
      setResponseStatus(response, error._http_status);

      Log.warn("Caught exception: " + error.toString());
      writeResponseErrorBody(response, error);
    }
  }

  private static void writeResponseErrorBody(HttpServletResponse response, H2OError error) {
    // Note: don't use Schema.schema(version, error) because we have to work at bootstrap:
    try {
      @SuppressWarnings("unchecked")
      final String s = new H2OErrorV3().fillFromImpl(error).toJsonString();
      response.getWriter().write(s);
    } catch (IOException e) {
      throw new RuntimeException(e);
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
  
  public static String[] parseUriParams(String uri, HttpServletResponse response, Pattern p, int numParams) throws IOException {
    Matcher m = p.matcher(uri);
    if (!m.matches()) {
      ServletUtils.setResponseStatus(response, HttpServletResponse.SC_BAD_REQUEST);
      response.getWriter().write("Improperly formatted URI");
      return null;
    } else {
      String[] result = new String[numParams];
      for (int i = 0; i < numParams; i++) {
        result[i] = m.group(i+1);
      }
      return result;
    }
  }

  public static boolean isXhrRequest(final HttpServletRequest request) {
    final String requestedWithHeader = request.getHeader("X-Requested-With");
    return "XMLHttpRequest".equals(requestedWithHeader);
  }

  public static boolean isTraceRequest(final HttpServletRequest request) {
    return TRACE_METHOD.equalsIgnoreCase(request.getMethod());
  }
  
  public static void setCommonResponseHttpHeaders(HttpServletResponse response, final boolean xhrRequest) {
    if (xhrRequest) {
      response.setHeader("Cache-Control", "no-cache");
    }
    if (DISABLE_CORS) {
      response.setHeader("Access-Control-Allow-Origin", "*");
      response.setHeader("Access-Control-Allow-Headers", "*");
      response.setHeader("Access-Control-Allow-Methods", "*");
    }
    response.setHeader("X-h2o-build-project-version", H2O.ABV.projectVersion());
    response.setHeader("X-h2o-rest-api-version-max", Integer.toString(water.api.RequestServer.H2O_REST_API_VERSION));
    response.setHeader("X-h2o-cluster-id", Long.toString(H2O.CLUSTER_ID));
    response.setHeader("X-h2o-cluster-good", Boolean.toString(H2O.CLOUD.healthy()));
    // Security headers
    if (ENABLE_XFRAME_SAMEORIGIN) {
      response.setHeader("X-Frame-Options", "sameorigin");
    } else {
      response.setHeader("X-Frame-Options", "deny");
    }
    response.setHeader("X-XSS-Protection", "1; mode=block");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Content-Security-Policy", "default-src 'self' 'unsafe-eval' 'unsafe-inline'; img-src 'self' data:");
    // Note: ^^^ unsafe-eval/-inline are essential for Flow to work
    //           this will also kill the component "Star H2O on Github" in Flow - see https://github.com/h2oai/private-h2o-3/issues/44
    // Custom headers - using addHeader - can be multi-value and cannot overwrite the security headers
    for (H2O.KeyValueArg header : H2O.ARGS.extra_headers) {
      response.addHeader(header._key, header._value);
    }
  }
  
  public static void logRequest(String method, HttpServletRequest request, HttpServletResponse response) {
    LOG.info(
        String.format(
            "  %-6s  %3d  %6d ms  %s", 
            method, getStatus(), System.currentTimeMillis() - getStartMillis(), request.getRequestURI()
        )
    );
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
