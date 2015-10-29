package water;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import water.UDPRebooted.ShutdownTsk;
import water.api.H2OErrorV3;
import water.exceptions.H2OAbstractRuntimeException;
import water.exceptions.H2OFailException;
import water.fvec.Frame;
import water.fvec.UploadFileVec;
import water.init.NodePersistentStorage;
import water.util.FileUtils;
import water.util.HttpResponseStatus;
import water.util.Log;

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

  private static void startTransaction(String userAgent) {
    _userAgent.set(userAgent);
  }

  private static void endTransaction() {
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

  protected static void setResponseStatus(HttpServletResponse response, int sc) {
    setStatus(sc);
    response.setStatus(sc);
  }

  @SuppressWarnings("unused")
  protected static void sendResponseError(HttpServletResponse response, int sc, String msg) throws java.io.IOException {
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
    return "http";
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
    startHttp();
  }

  public void acceptRequests() {
    _acceptRequests = true;
  }

  protected void createServer(Connector connector) throws Exception {
    _server.setConnectors(new Connector[]{connector});
    registerHandlers(_server);
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
    if (_ip != null) {
      connector.setHost(_ip);
    }
    connector.setPort(_port);

    createServer(connector);
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
  public void registerHandlers(HandlerWrapper s) {
    GateHandler gh = new GateHandler();
    AddCommonResponseHeadersHandler rhh = new AddCommonResponseHeadersHandler();
    ExtensionHandler1 eh1 = new ExtensionHandler1();

    ServletContextHandler context = new ServletContextHandler(
            ServletContextHandler.SECURITY | ServletContextHandler.SESSIONS
    );
    context.setContextPath("/");

    context.addServlet(H2oNpsBinServlet.class,   "/3/NodePersistentStorage.bin/*");
    context.addServlet(H2oPostFileServlet.class, "/3/PostFile.bin");
    context.addServlet(H2oPostFileServlet.class, "/3/PostFile");
    context.addServlet(H2oDatasetServlet.class,   "/3/DownloadDataset");
    context.addServlet(H2oDatasetServlet.class,   "/3/DownloadDataset.bin");
    context.addServlet(H2oDefaultServlet.class,  "/");

    Handler[] handlers = {gh, rhh, eh1, context};
    HandlerCollection hc = new HandlerCollection();
    hc.setHandlers(handlers);
    s.setHandler(hc);
  }

  public class GateHandler extends AbstractHandler {
    public GateHandler() {}

    public void handle( String target,
                        Request baseRequest,
                        HttpServletRequest request,
                        HttpServletResponse response ) throws IOException, ServletException {
      startRequestLifecycle();

      while (! _acceptRequests) {
        try {
          Thread.sleep(100);
        }
        catch (Exception ignore) {}
      }
    }
  }

  @SuppressWarnings("unused")
  protected void handle1(String target,
                         Request baseRequest,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException, ServletException {}

  public class ExtensionHandler1 extends AbstractHandler {
    public ExtensionHandler1() {}

    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException, ServletException {
      H2O.getJetty().handle1(target, baseRequest, request, response);
    }
  }

  public class AddCommonResponseHeadersHandler extends AbstractHandler {
    public AddCommonResponseHeadersHandler() {}

    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException, ServletException {
      setCommonResponseHttpHeaders(response);
    }
  }

  public static class H2oNpsBinServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws IOException, ServletException {
      String uri = getDecodedUri(request);
      try {
        Pattern p = Pattern.compile(".*/NodePersistentStorage.bin/([^/]+)/([^/]+)");
        Matcher m = p.matcher(uri);
        boolean b = m.matches();
        if (!b) {
          setResponseStatus(response, HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().write("Improperly formatted URI");
          return;
        }

        String categoryName = m.group(1);
        String keyName = m.group(2);
        NodePersistentStorage nps = H2O.getNPS();
        AtomicLong length = new AtomicLong();
        InputStream is = nps.get(categoryName, keyName, length);
        if (length.get() > (long)Integer.MAX_VALUE) {
          throw new Exception("NPS value size exceeds Integer.MAX_VALUE");
        }
        response.setContentType("application/octet-stream");
        response.setContentLength((int) length.get());
        response.addHeader("Content-Disposition", "attachment; filename=" + keyName + ".flow");
        setResponseStatus(response, HttpServletResponse.SC_OK);
        OutputStream os = response.getOutputStream();
        water.util.FileUtils.copyStream(is, os, 2048);
      }
      catch (Exception e) {
        sendErrorResponse(response, e, uri);
      }
      finally {
        logRequest("GET", request, response);
      }
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws IOException, ServletException {
      String uri = getDecodedUri(request);
      try {
        Pattern p = Pattern.compile(".*NodePersistentStorage.bin/([^/]+)/([^/]+)");
        Matcher m = p.matcher(uri);
        boolean b = m.matches();
        if (!b) {
          setResponseStatus(response, HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().write("Improperly formatted URI");
          return;
        }

        String categoryName = m.group(1);
        String keyName = m.group(2);

        InputStream is = extractPartInputStream(request, response);
        if (is == null) {
          return;
        }

        H2O.getNPS().put(categoryName, keyName, is);
        long length = H2O.getNPS().get_length(categoryName, keyName);
        String responsePayload = "{ " +
                "\"category\" : "     + "\"" + categoryName + "\", " +
                "\"name\" : "         + "\"" + keyName      + "\", " +
                "\"total_bytes\" : "  +        length       + " " +
                "}\n";
        response.setContentType("application/json");
        response.getWriter().write(responsePayload);
      }
      catch (Exception e) {
        sendErrorResponse(response, e, uri);
      }
      finally {
        logRequest("POST", request, response);
      }
    }
  }

  public static class H2oPostFileServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws IOException, ServletException {
      String uri = getDecodedUri(request);

      try {
        String destination_frame = request.getParameter("destination_frame");
        if (destination_frame == null) {
          destination_frame = "upload" + Key.rand();
        }
        if (!validKeyName(destination_frame)) {
          setResponseStatus(response, HttpServletResponse.SC_BAD_REQUEST);
          response.getWriter().write("Invalid key name, contains illegal characters");
          return;
        }

        //
        // Here is an example of how to upload a file from the command line.
        //
        // curl -v -F "file=@allyears2k_headers.zip" "http://localhost:54321/PostFile.bin?destination_frame=a.zip"
        //
        // JSON Payload returned is:
        //     { "destination_frame": "key_name", "total_bytes": nnn }
        //
        InputStream is = extractPartInputStream(request, response);
        if (is == null) {
          return;
        }

        UploadFileVec.ReadPutStats stats = new UploadFileVec.ReadPutStats();
        UploadFileVec.readPut(destination_frame, is, stats);
        String responsePayload = "{ "       +
                "\"destination_frame\": \"" + destination_frame   + "\", " +
                "\"total_bytes\": "         + stats.total_bytes + " " +
                "}\n";
        response.setContentType("application/json");
        response.getWriter().write(responsePayload);
      }
      catch (Exception e) {
        sendErrorResponse(response, e, uri);
      } finally {
        logRequest("POST", request, response);
      }
    }
  }

  private static InputStream extractPartInputStream (HttpServletRequest request, HttpServletResponse response) throws IOException{
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

  private static boolean validKeyName(String name) {
    byte[] arr = name.getBytes();
    for (byte b : arr) {
      if (b == '"') return false;
      if (b == '\\') return false;
    }

    return true;
  }

  private static void sendErrorResponse(HttpServletResponse response, Exception e, String uri) {
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

  private static String getDecodedUri(HttpServletRequest request) {
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
  }

  public static class H2oDatasetServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws IOException, ServletException {
      String uri = getDecodedUri(request);
      try {
        boolean use_hex = false;
        String f_name = request.getParameter("frame_id");
        String hex_string = request.getParameter("hex_string");
        if (f_name == null) {
          throw new RuntimeException("Cannot find value for parameter \'frame_id\'");
        }
        if (hex_string != null && hex_string.toLowerCase().equals("true")) {
          use_hex = true;
        }
        
        Frame dataset = DKV.getGet(f_name);
        // TODO: Find a way to determing the hex_string parameter. It should not always be false
        InputStream is = dataset.toCSV(true, use_hex);
        response.setContentType("application/octet-stream");
        // Clean up the file name
        int x = f_name.length()-1;
        boolean dot=false;
        for( ; x >= 0; x-- )
          if( !Character.isLetterOrDigit(f_name.charAt(x)) && f_name.charAt(x)!='_' )
            if( f_name.charAt(x)=='.' && !dot ) dot=true;
            else break;
        String suggested_fname = f_name.substring(x+1).replace(".hex", ".csv");
        if( !suggested_fname.endsWith(".csv") )
          suggested_fname = suggested_fname+".csv";
        f_name = suggested_fname;
        response.addHeader("Content-Disposition", "attachment; filename=" + f_name);
        setResponseStatus(response, HttpServletResponse.SC_OK);
        OutputStream os = response.getOutputStream();
        water.util.FileUtils.copyStream(is, os, 2048);
      }
      catch (Exception e) {
        sendErrorResponse(response, e, uri);
      }
      finally {
        logRequest("GET", request, response);
      }
    }
  }

  @SuppressWarnings("serial")
  public static class H2oDefaultServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws IOException, ServletException {
      doGeneric("GET", request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                         HttpServletResponse response) throws IOException, ServletException {
      doGeneric("POST", request, response);
    }

    @Override
    protected void doHead(HttpServletRequest request,
                          HttpServletResponse response) throws IOException, ServletException {
      doGeneric("HEAD", request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request,
                          HttpServletResponse response) throws IOException, ServletException {
      doGeneric("DELETE", request, response);
    }

    @Override
    protected void doPut(HttpServletRequest request,
                            HttpServletResponse response) throws IOException, ServletException {
      doGeneric("PUT", request, response);
    }

    public void doGeneric(String method,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException, ServletException {
      try {
        startTransaction(request.getHeader("User-Agent"));

        // Marshal Jetty request parameters to Nano-style.

        // Note that getServletPath does an un-escape so that the %24 of job id's are turned into $ characters.
        String uri = request.getServletPath();

        Properties headers = new Properties();
        Enumeration<String> en = request.getHeaderNames();
        while (en.hasMoreElements()) {
          String key = en.nextElement();
          String value = request.getHeader(key);
          headers.put(key, value);
        }

        Properties parms = new Properties();
        Map<String, String[]> parameterMap;
        parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
          String key = entry.getKey();
          String[] values = entry.getValue();
          for (String value : values) {
            parms.put(key, value);
          }
        }

        // Make Nano call.
        NanoHTTPD.Response resp = water.api.RequestServer.SERVER.serve(uri, method, headers, parms);

        // Un-marshal Nano response back to Jetty.
        String choppedNanoStatus = resp.status.substring(0, 3);
        assert (choppedNanoStatus.length() == 3);
        int sc = Integer.parseInt(choppedNanoStatus);
        setResponseStatus(response, sc);

        response.setContentType(resp.mimeType);

        Properties header = resp.header;
        Enumeration<Object> en2 = header.keys();
        while (en2.hasMoreElements()) {
          String key = (String) en2.nextElement();
          String value = header.getProperty(key);
          response.setHeader(key, value);
        }

        OutputStream os = response.getOutputStream();
        if (resp instanceof NanoHTTPD.StreamResponse) {
          NanoHTTPD.StreamResponse ssr = (NanoHTTPD.StreamResponse) resp;
          ssr.streamWriter.writeTo(os);
        } else {
          InputStream is = resp.data;
          FileUtils.copyStream(is, os, 1024);
        }
      } finally {
        logRequest(method, request, response);
        // Handle shutdown if it was requested.
        if (H2O.getShutdownRequested()) {
          (new Thread() {
            public void run() {
              boolean [] confirmations = new boolean[H2O.CLOUD.size()];
              if (H2O.SELF.index() >= 0) {
                confirmations[H2O.SELF.index()] = true;
              }
              for(H2ONode n:H2O.CLOUD._memary) {
                if(n != H2O.SELF)
                  new RPC(n, new ShutdownTsk(H2O.SELF,n.index(), 1000, confirmations)).call();
              }
              try { Thread.sleep(2000); }
              catch (Exception ignore) {}
              int failedToShutdown = 0;
              // shutdown failed
              for(boolean b:confirmations)
                if(!b) failedToShutdown++;
              Log.info("Orderly shutdown: " + (failedToShutdown > 0? failedToShutdown + " nodes failed to shut down! ":"") + " Shutting down now.");
              H2O.closeAll();
              H2O.exit(failedToShutdown);
            }
          }).start();
        }
        endTransaction();
      }
    }
  }

  //--------------------------------------------------

  @SuppressWarnings("unused")
  protected static void logRequest(String method, HttpServletRequest request, HttpServletResponse response) {
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
