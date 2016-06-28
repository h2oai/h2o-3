package water;

import org.eclipse.jetty.plus.jaas.JAASLoginService;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import water.api.DatasetServlet;
import water.api.NpsBinServlet;
import water.api.PostFileServlet;
import water.api.RequestServer;
import water.api.schemas3.H2OErrorV3;
import water.exceptions.H2OAbstractRuntimeException;
import water.exceptions.H2OFailException;
import water.util.HttpResponseStatus;
import water.util.Log;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.*;

/**
 * Embedded Jetty instance inside H2O.
 * This is intended to be a singleton per H2O node.
 */
public class JettyHTTPD {
  //------------------------------------------------------------------------------------------
  // Thread-specific things.
  //------------------------------------------------------------------------------------------

  private static final ThreadLocal<Long> _startMillis = new ThreadLocal<>();
  private static final ThreadLocal<Integer> _status = new ThreadLocal<>();

  private static final ThreadLocal<String> _userAgent = new ThreadLocal<>();

  private static void startRequestLifecycle() {
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

  //------------------------------------------------------------------------------------------
  //------------------------------------------------------------------------------------------

  public static void setResponseStatus(HttpServletResponse response, int sc) {
    setStatus(sc);
    response.setStatus(sc);
  }

  public static void sendResponseError(HttpServletResponse response, int sc, String msg) throws java.io.IOException {
    setStatus(sc);
    response.sendError(sc, msg);
  }

  //------------------------------------------------------------------------------------------
  // Object-specific things.
  //------------------------------------------------------------------------------------------
  private static volatile boolean _acceptRequests = false;

  private String _ip;
  private int _port;

  // Jetty server object.
  private Server _server;

  /**
   * Create bare Jetty object.
   */
  public JettyHTTPD() {
  }

  /**
   * @return URI scheme
   */
  public String getScheme() {
    if (H2O.ARGS.jks != null) {
      return "https";
    }
    else {
      return "http";
    }
  }

  /**
   * @return Port number
   */
  public int getPort() {
    return _port;
  }

  /**
   * @return IP address
   */
  public String getIp() {
    return _ip;
  }

  /**
   * @return Server object
   */
  public Server getServer() {
    return _server;
  }

  public void setServer(Server value) {
    _server = value;
  }

  public void setup(String ip, int port) {
    _ip = ip;
    _port = port;
    System.setProperty("org.eclipse.jetty.server.Request.maxFormContentSize", Integer.toString(Integer.MAX_VALUE));
  }

  /**
   * Choose a Port and IP address and start the Jetty server.
   *
   * @throws Exception
   */
  public void start(String ip, int port) throws Exception {
    setup(ip, port);
    if (H2O.ARGS.jks != null) {
      startHttps();
    }
    else {
      startHttp();
    }
  }

  public void acceptRequests() {
    _acceptRequests = true;
  }

  protected void createServer(Connector connector) throws Exception {
    _server.setConnectors(new Connector[]{connector});

    if (H2O.ARGS.hash_login || H2O.ARGS.ldap_login || H2O.ARGS.kerberos_login) {
      // REFER TO http://www.eclipse.org/jetty/documentation/9.1.4.v20140401/embedded-examples.html#embedded-secured-hello-handler
      if (H2O.ARGS.login_conf == null) {
        Log.err("Must specify -login_conf argument");
        H2O.exit(1);
      }

      LoginService loginService;
      if (H2O.ARGS.hash_login) {
        Log.info("Configuring HashLoginService");
        loginService = new HashLoginService("H2O", H2O.ARGS.login_conf);
      }
      else if (H2O.ARGS.ldap_login) {
        Log.info("Configuring JAASLoginService (with LDAP)");
        System.setProperty("java.security.auth.login.config", H2O.ARGS.login_conf);
        loginService = new JAASLoginService("ldaploginmodule");
      }
      else if (H2O.ARGS.kerberos_login) {
        Log.info("Configuring JAASLoginService (with Kerberos)");
        System.setProperty("java.security.auth.login.config",H2O.ARGS.login_conf);
        loginService = new JAASLoginService("krb5loginmodule");
      }
      else {
        throw H2O.fail();
      }
      IdentityService identityService = new DefaultIdentityService();
      loginService.setIdentityService(identityService);
      _server.addBean(loginService);

      // Set a security handler as the first handler in the chain.
      ConstraintSecurityHandler security = new ConstraintSecurityHandler();

      // Set up a constraint to authenticate all calls, and allow certain roles in.
      Constraint constraint = new Constraint();
      constraint.setName("auth");
      constraint.setAuthenticate(true);

      // Configure role stuff (to be disregarded).  We are ignoring roles, and only going off the user name.
      //
      //   Jetty 8 and prior.
      //
      //     Jetty 8 requires the security.setStrict(false) and ANY_ROLE.
      security.setStrict(false);
      constraint.setRoles(new String[]{Constraint.ANY_ROLE});

      //   Jetty 9 and later.
      //
      //     Jetty 9 and later uses a different servlet spec, and ANY_AUTH gives the same behavior
      //     for that API version as ANY_ROLE did previously.  This required some low-level debugging
      //     to figure out, so I'm documenting it here.
      //     Jetty 9 did not require security.setStrict(false).
      //
      // constraint.setRoles(new String[]{Constraint.ANY_AUTH});

      ConstraintMapping mapping = new ConstraintMapping();
      mapping.setPathSpec("/*"); // Lock down all API calls
      mapping.setConstraint(constraint);
      security.setConstraintMappings(Collections.singletonList(mapping));

      // Authentication / Authorization
      security.setAuthenticator(new BasicAuthenticator());
      security.setLoginService(loginService);

      // Pass-through to H2O if authenticated.
      registerHandlers(security);
      _server.setHandler(security);
    } else {
      registerHandlers(_server);
    }

    _server.start();
  }

  protected void startHttp() throws Exception {
    _server = new Server();

//    QueuedThreadPool p = new QueuedThreadPool();
//    p.setName("jetty-h2o");
//    p.setMinThreads(3);
//    p.setMaxThreads(50);
//    p.setMaxIdleTimeMs(3000);
//    _server.setThreadPool(p);

    Connector connector=new SocketConnector();
    connector.setHost(_ip);
    connector.setPort(_port);

    createServer(connector);
  }

  /**
   * This implementation is based on http://blog.denevell.org/jetty-9-ssl-https.html
   *
   * @throws Exception
   */
  private void startHttps() throws Exception {
    _server = new Server();

    SslContextFactory sslContextFactory = new SslContextFactory(H2O.ARGS.jks);
    sslContextFactory.setKeyStorePassword(H2O.ARGS.jks_pass);

    SslSocketConnector httpsConnector = new SslSocketConnector(sslContextFactory);

    if (getIp() != null) {
      httpsConnector.setHost(getIp());
    }
    httpsConnector.setPort(getPort());

    createServer(httpsConnector);
  }

  /**
   * Stop Jetty server after it has been started.
   * This is unlikely to ever be called by H2O until H2O supports graceful shutdown.
   *
   * @throws Exception
   */
  public void stop() throws Exception {
    if (_server != null) {
      _server.stop();
    }
  }

  /**
   * Hook up Jetty handlers.  Do this before start() is called.
   */
  public void registerHandlers(HandlerWrapper handlerWrapper) {

    ServletContextHandler context = new ServletContextHandler(
        ServletContextHandler.SECURITY | ServletContextHandler.SESSIONS
    );
    context.setContextPath("/");
    context.addServlet(NpsBinServlet.class,   "/3/NodePersistentStorage.bin/*");
    context.addServlet(PostFileServlet.class, "/3/PostFile.bin");
    context.addServlet(PostFileServlet.class, "/3/PostFile");
    context.addServlet(DatasetServlet.class,  "/3/DownloadDataset");
    context.addServlet(DatasetServlet.class,  "/3/DownloadDataset.bin");
    context.addServlet(RequestServer.class,   "/");

    HandlerCollection hc = new HandlerCollection();
    hc.setHandlers(new Handler[]{
        new GateHandler(),
        new AuthenticationHandler(),
        context,
    });
    handlerWrapper.setHandler(hc);
  }

  public class GateHandler extends AbstractHandler {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
      startRequestLifecycle();
      while (!_acceptRequests) {
        try { Thread.sleep(100); }
        catch (Exception ignore) {}
      }
      setCommonResponseHttpHeaders(response);
    }
  }

  public class AuthenticationHandler extends AbstractHandler {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

      if (!H2O.ARGS.ldap_login && !H2O.ARGS.kerberos_login) return;

      String loginName = request.getUserPrincipal().getName();
      if (!loginName.equals(H2O.ARGS.user_name)) {
        Log.warn("Login name (" + loginName + ") does not match cluster owner name (" + H2O.ARGS.user_name + ")");
        sendResponseError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login name does not match cluster owner name");
        baseRequest.setHandled(true);
      }
    }
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
    byte[] boundary = boundaryString.getBytes();

    // Consume headers of the mime part.
    InputStream is = request.getInputStream();
    String line = readLine(is);
    while ((line != null) && (line.trim().length()>0)) {
      line = readLine(is);
    }

    return new InputStreamWrapper(is, boundary);
  }

  public static boolean validKeyName(String name) {
    byte[] arr = name.getBytes();
    for (byte b : arr) {
      if (b == '"') return false;
      if (b == '\\') return false;
    }

    return true;
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
      catch (Exception ignore) {}
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
      catch (Exception ignore) {}
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

  private static void setCommonResponseHttpHeaders(HttpServletResponse response) {
    response.setHeader("X-h2o-build-project-version", H2O.ABV.projectVersion());
    response.setHeader("X-h2o-rest-api-version-max", Integer.toString(water.api.RequestServer.H2O_REST_API_VERSION));
    response.setHeader("X-h2o-cluster-id", Long.toString(H2O.CLUSTER_ID));
    response.setHeader("X-h2o-cluster-good", Boolean.toString(H2O.CLOUD.healthy()));
  }


  //--------------------------------------------------

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
