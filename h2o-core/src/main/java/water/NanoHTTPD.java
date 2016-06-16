package water;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import water.api.RequestServer;
import water.api.StreamWriter;
import water.fvec.UploadFileVec;
import water.util.Log;

/**
 * A simple, tiny, nicely embeddable HTTP 1.0 (partially 1.1) server in Java
 *
 * <p> NanoHTTPD version 1.25,
 * Copyright &copy; 2001,2005-2012 Jarno Elonen (elonen@iki.fi, http://iki.fi/elonen/)
 * and Copyright &copy; 2010 Konstantinos Togias (info@ktogias.gr, http://ktogias.gr)
 *
 * <p><b>Features + limitations: </b><ul>
 *
 *    <li> Only one Java file </li>
 *    <li> Java 1.1 compatible </li>
 *    <li> Released as open source, Modified BSD licence </li>
 *    <li> No fixed config files, logging, authorization etc. (Implement yourself if you need them.) </li>
 *    <li> Supports parameter parsing of GET and POST methods (+ rudimentary PUT support in 1.25) </li>
 *    <li> Supports both dynamic content and file serving </li>
 *    <li> Supports file upload (since version 1.2, 2010) </li>
 *    <li> Supports partial content (streaming)</li>
 *    <li> Supports ETags</li>
 *    <li> Never caches anything </li>
 *    <li> Doesn't limit bandwidth, request time or simultaneous connections </li>
 *    <li> Default code serves files and shows all HTTP parameters and headers</li>
 *    <li> File server supports directory listing, index.html and index.htm</li>
 *    <li> File server supports partial content (streaming)</li>
 *    <li> File server supports ETags</li>
 *    <li> File server does the 301 redirection trick for directories without '/'</li>
 *    <li> File server supports simple skipping for files (continue download) </li>
 *    <li> File server serves also very long files without memory overhead </li>
 *    <li> Contains a built-in list of most common mime types </li>
 *    <li> All header names are converted lowercase so they don't vary between browsers/clients </li>
 *
 * </ul>
 *
 * <p><b>Ways to use: </b><ul>
 *
 *    <li> Run as a standalone app, serves files and shows requests</li>
 *    <li> Subclass serve() and embed to your own program </li>
 *    <li> Call serveFile() from serve() with your own base directory </li>
 *
 * </ul>
 *
 * See the end of the source file for distribution license
 * (Modified BSD licence)
 */
public class NanoHTTPD
{
  // ==================================================
  // API parts
  // ==================================================

  /**
   * Override this to customize the server.<p>
   *
   * (By default, this delegates to serveFile() and allows directory listing.)
   *
   * @param uri	Percent-decoded URI without parameters, for example "/index.cgi"
   * @param method	"GET", "POST" etc.
   * @param parms	Parsed, percent decoded parameters from URI and, in case of POST, data.
   * @param header	Header entries, percent decoded
   * @return HTTP response, see class Response for details
   */
  public Response serve( String uri, String method, Properties header, Properties parms )
  {
    myOut.println( method + " '" + uri + "' " );

    Enumeration e = header.propertyNames();
    while ( e.hasMoreElements())
    {
      String value = (String)e.nextElement();
      myOut.println( "  HDR: '" + value + "' = '" +
          header.getProperty( value ) + "'" );
    }
    e = parms.propertyNames();
    while ( e.hasMoreElements())
    {
      String value = (String)e.nextElement();
      myOut.println( "  PRM: '" + value + "' = '" +
          parms.getProperty( value ) + "'" );
    }

    return serveFile( uri, header, myRootDir, true );
  }

  /**
   * HTTP response.
   * Return one of these from serve().
   */
  public static class Response
  {
    /**
     * Default constructor: response = HTTP_OK, data = mime = 'null'
     */
    public Response()
    {
      this.status = HTTP_OK;
    }

    /**
     * Basic constructor.
     */
    public Response( String status, String mimeType, InputStream data )
    {
      this.status = status;
      this.mimeType = mimeType;
      this.data = data;
    }

    /**
     * Convenience method that makes an InputStream out of
     * given text.
     */
    public Response( String status, String mimeType, String txt )
    {
      this.status = status;
      this.mimeType = mimeType;
      try
      {
        this.data = new ByteArrayInputStream( txt.getBytes("UTF-8"));
      }
      catch ( java.io.UnsupportedEncodingException e ) { Log.err(e); }
    }

    /**
     * Adds given line to the header.
     */
    public void addHeader( String name, String value )
    {
      header.put( name, value );
    }

    /**
     * HTTP status code after processing, e.g. "200 OK", HTTP_OK
     */
    public String status;

    /**
     * MIME type of content, e.g. "text/html"
     */
    public String mimeType;

    /**
     * Data of the response, may be null.
     */
    public InputStream data;

    /**
     * Headers for the HTTP response. Use addHeader()
     * to add lines.
     */
    public Properties header = new Properties();
  }

  public static class StreamResponse extends Response {

    public StreamResponse(String status, String mimeType, StreamWriter streamWriter) {
      this.status = status;
      this.mimeType = mimeType;
      this.streamWriter = streamWriter;
    }

    public StreamWriter streamWriter;
  }
  

  /**
   * Some HTTP response status codes
   */
  public static final String
      HTTP_OK = "200 OK",
      HTTP_CREATED = "201 Created",
      HTTP_ACCEPTED = "202 Accepted",
      HTTP_NO_CONTENT = "204 No Content",
      HTTP_PARTIAL_CONTENT = "206 Partial Content",
      HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable",
      HTTP_REDIRECT = "301 Moved Permanently",
      HTTP_NOT_MODIFIED = "304 Not Modified",
      HTTP_BAD_REQUEST = "400 Bad Request",
      HTTP_UNAUTHORIZED = "401 Unauthorized",
      HTTP_FORBIDDEN = "403 Forbidden",
      HTTP_NOT_FOUND = "404 Not Found",
      HTTP_BAD_METHOD = "405 Method Not Allowed",
      HTTP_TOO_LONG_REQUEST = "414 Request-URI Too Long",
      HTTP_TEAPOT = "418 I'm a Teapot",
      HTTP_THROTTLE = "429 Too Many Requests",
      HTTP_INTERNAL_ERROR = "500 Internal Server Error",
      HTTP_NOT_IMPLEMENTED = "501 Not Implemented",
      HTTP_SERVICE_NOT_AVAILABLE = "503 Service Unavailable";

  /**
   * Common mime types for dynamic content
   */
  public static final String
      MIME_PLAINTEXT = "text/plain",
      MIME_HTML = "text/html",
      MIME_JSON = "application/json",
      MIME_DEFAULT_BINARY = "application/octet-stream",
      MIME_XML = "text/xml";


  // ==================================================
  // Socket & server code
  // ==================================================

  /**
   * Jetty supercedes Nano.
   */
  public NanoHTTPD() {
    myServerSocket = null;
  }

  /**
   * Starts a HTTP server to given port.<p>
   * Throws an IOException if the socket is already in use
   */
  public NanoHTTPD( ServerSocket socket, File wwwroot ) throws IOException {
    myRootDir = wwwroot;
    myServerSocket = socket;
    myServerSocket.setReuseAddress(true);
    myThread = new Thread(new Runnable() {
      public void run() {
        try {
          while( true )
            new HTTPSession( myServerSocket.accept());
        } catch ( IOException e ) { }
      }
    }, "NanoHTTPD Thread");
    myThread.setDaemon( true );
    myThread.start();
  }

  /**
   * Stops the server.
   */
  public void stop() {
    try {
      myServerSocket.close();
      myThread.join();
    } catch ( IOException | InterruptedException e ) {
    }
  }


  /**
   * Starts as a standalone file server and waits for Enter.
   */
  public static void main( String[] args ) {
    myOut.println( "NanoHTTPD 1.25 (C) 2001,2005-2011 Jarno Elonen and (C) 2010 Konstantinos Togias\n" +
        "(Command line options: [-p port] [-d root-dir] [--licence])\n" );

    // Defaults
    int port = 80;
    File wwwroot = new File(".").getAbsoluteFile();

    // Show licence if requested
    for ( int i=0; i<args.length; ++i )
      if(args[i].equalsIgnoreCase("-p"))
        port = Integer.parseInt( args[i+1] );
      else if(args[i].equalsIgnoreCase("-d"))
        wwwroot = new File( args[i+1] ).getAbsoluteFile();
      else if ( args[i].toLowerCase().endsWith( "licence" ))
      {
        myOut.println( LICENCE + "\n" );
        break;
      }

    try {
      new NanoHTTPD( new ServerSocket(port), wwwroot );
    } catch( IOException ioe ) {
      Log.err("Couldn't start server:\n", ioe );
      H2O.shutdown( -1 );
    }

    myOut.println( "Now serving files in port " + port + " from \"" + wwwroot + "\"" );
    myOut.println( "Hit Enter to stop.\n" );

    try { System.in.read(); } catch( Throwable t ) { Log.err(t); }
  }

  /**
   * Handles one session, i.e. parses the HTTP request
   * and returns the response.
   */
  private class HTTPSession implements Runnable {
    private final long startMillis = System.currentTimeMillis();

    public HTTPSession( Socket s ) {
      mySocket = s;
      Thread t = new Thread( this, "NanoHTTPD Session" );
      t.setDaemon( true );
      t.setPriority(Thread.MAX_PRIORITY-1);
      t.start();
    }

    /** Maximal supported header. */
    static final int MAX_HEADER_BUFFER_SIZE = 1 << 16; // 64k
    public void run() {
      try (Socket mySocket=this.mySocket ) { // Try-with-resources; auto-close on exit
        InputStream is = new BufferedInputStream(mySocket.getInputStream());
        is.mark(MAX_HEADER_BUFFER_SIZE);

        // Read the first 8192 bytes.
        // The full header should fit in here.
        // Apache's default header limit is 8KB.
        int bufsize = 8192;
        byte[] buf = new byte[bufsize];
        boolean nl = false;     // Saw a nl
        int rlen=0;
        while( rlen < MAX_HEADER_BUFFER_SIZE ) {
          int b = is.read();
          if( b == -1 ) return;
          buf[rlen++] = (byte)b;
          if( b == '\n' ) {
            if(nl) break; // 2nd nl in a row ==> done with header
            nl = true;
          } else if( b != '\r' ) nl = false;
          if (rlen == buf.length) buf = Arrays.copyOf(buf, 2*buf.length);
        }

        if (rlen == MAX_HEADER_BUFFER_SIZE)
          sendError(HTTP_TOO_LONG_REQUEST, "Requested URL is too long!");

        // Create a BufferedReader for parsing the header.
        ByteArrayInputStream hbis = new ByteArrayInputStream(buf, 0, rlen);
        BufferedReader hin = new BufferedReader( new InputStreamReader( hbis ));
        Properties pre = new Properties();
        Properties parms = new Properties();
        Properties header = new Properties();

        // Decode the header into parms and header java properties
        decodeHeader(hin, pre, parms, header);
        String method = pre.getProperty("method");
        String uri = pre.getProperty("uri");

        long size = 0x7FFFFFFFFFFFFFFFl;
        String contentLength = header.getProperty("content-length");
        if (contentLength != null) {
          try { size = Integer.parseInt(contentLength); }
          catch (NumberFormatException ex) {}
        }

        // We are looking for the byte separating header from body.
        // It must be the last byte of the first two sequential new lines.
        int splitbyte = 0;
        boolean sbfound = false;
        while (splitbyte < rlen) {
          if (buf[splitbyte] == '\r' && buf[++splitbyte] == '\n' && buf[++splitbyte] == '\r' && buf[++splitbyte] == '\n') {
            sbfound = true;
            break;
          }
          splitbyte++;
        }
        splitbyte++;
        is.reset();
        is.skip(splitbyte);

        // While Firefox sends on the first read all the data fitting
        // our buffer, Chrome and Opera sends only the headers even if
        // there is data for the body. So we do some magic here to find
        // out whether we have already consumed part of body, if we
        // have reached the end of the data to be sent or we should
        // expect the first byte of the body at the next read.
        if (splitbyte < rlen)
          size -= rlen - splitbyte +1;
        else if (!sbfound || size == 0x7FFFFFFFFFFFFFFFl)
          size = 0;

        // If the method is POST, there may be parameters
        // in data section, too, read it:
        BufferedReader in = new BufferedReader( new InputStreamReader(is));
        if ( method.equalsIgnoreCase( "POST" ))
        {
          String contentType = "";
          String contentTypeHeader = header.getProperty("content-type");
          if (contentTypeHeader == null)
            contentTypeHeader = "";
          StringTokenizer st = new StringTokenizer( contentTypeHeader , "; " );
          if ( st.hasMoreTokens()) {
            contentType = st.nextToken();
          }

          if (contentType.equalsIgnoreCase("multipart/form-data"))
          {
            // Presumably this entire block is never called anymore.
            // Handle multipart/form-data
            if (!st.hasMoreTokens())
              sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html" );
            String boundaryExp = st.nextToken();
            st = new StringTokenizer( boundaryExp , "=" );
            if (st.countTokens() != 2)
              sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but boundary syntax error. Usage: GET /example/file.html" );
            st.nextToken();
            String boundary = st.nextToken();
            String paddedMethod = String.format("%-6s", method);
            Log.info("Method: " + paddedMethod, ", URI: " + uri + ", route: " + "(special case)" + ", parms: " + parms);
            // RequestServer.alwaysLogRequest(uri, "POST", parms);  <-- this was throwing H2O.fail()
            boolean handled = fileUpload(boundary, is, parms, uri);
            if (handled) {
              return;
            }
          } else {
            // Handle application/x-www-form-urlencoded

            String postLine = "";
            if (size >= 0) {
              //
              // content-length is specified.  Use it.
              //
              char pbuf[] = new char[4096];
              long bytesRead = 0;
              long bytesToRead = size;
              StringBuilder sb = new StringBuilder();
              while (bytesRead < bytesToRead) {
                int n = in.read(pbuf);
                if (n < 0) {
                  break;
                }
                else if (n == 0) {
                  // this is supposed to be blocking, so i don't know what this means.
                  // but it isn't good.
                  assert(false);
                  break;
                }
                bytesRead += n;
                sb.append(pbuf, 0, n);
              }
              postLine = sb.toString();
            }
            else {
              //
              // The original path for x-www-form-urlencoded.
              // Don't have content-length.  Look for \r\n to stop the input.
              //
              char pbuf[] = new char[512];
              int read = in.read(pbuf);
              while ( read >= 0 && !postLine.endsWith("\r\n") )
              {
                postLine += String.valueOf(pbuf, 0, read);
                read = in.read(pbuf);
              }
              postLine = postLine.trim();
            }

            if (contentType.equalsIgnoreCase("application/json")) {
              parms.put("_post_body", postLine); // JSON text; we'll deserialize later, e.g. into a subclass of ModelParametersSchema
            } else {
              decodeParms(postLine, parms);
            }
          }
        }

        // Ok, now do the serve()
        Response r = serve( uri, method, header, parms );
        if ( r == null )
          sendError(HTTP_INTERNAL_ERROR, "SERVER INTERNAL ERROR: Serve() returned a null response." );
        else
          sendResponse( r.status, r.mimeType, r.header, r.data );

        in.close();
        is.close();
      } catch ( IOException ioe ) {
        try {
          sendError(HTTP_INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
        } catch ( Throwable t ) { Log.err(t); }
      } catch ( InterruptedException e ) {
        // Thrown by sendError, ignore and exit the thread.
      }
    }

    /**
     * Decodes the sent headers and loads the data into
     * java Properties' key - value pairs
     **/
    private  void decodeHeader(BufferedReader in, Properties pre, Properties parms, Properties header)
        throws InterruptedException
        {
      try {
        // Read the request line
        String inLine = in.readLine();
        if (inLine == null) return;
        StringTokenizer st = new StringTokenizer( inLine );
        if ( !st.hasMoreTokens())
          sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html" );

        String method = st.nextToken();
        pre.put("method", method);

        if ( !st.hasMoreTokens())
          sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html" );

        String uri = st.nextToken();

        // Decode parameters from the URI
        int qmi = uri.indexOf( '?' );
        if ( qmi >= 0 )
        {
          decodeParms( uri.substring( qmi+1 ), parms );
          uri = decodePercent( uri.substring( 0, qmi ));
        }
        else uri = decodePercent(uri);

        // If there's another token, it's protocol version,
        // followed by HTTP headers. Ignore version but parse headers.
        // NOTE: this now forces header names lowercase since they are
        // case insensitive and vary by client.
        if ( st.hasMoreTokens())
        {
          String line = in.readLine();
          while ( line != null && line.trim().length() > 0 )
          {
            int p = line.indexOf( ':' );
            if ( p >= 0 )
              header.put( line.substring(0,p).trim().toLowerCase(), line.substring(p+1).trim());
            line = in.readLine();
          }
        }

        pre.put("uri", uri);
      } catch ( IOException ioe ) {
        sendError(HTTP_INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
      }
    }

    public String readLine(InputStream in) throws IOException {
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


    private int readBufOrLine(InputStream in, byte[] mem) throws IOException {
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

    private boolean validKeyName(String name) {
      byte[] arr = name.getBytes();
      for (byte b : arr) {
        if (b == '"') return false;
        if (b == '\\') return false;
      }

      return true;
    }

    private boolean fileUpload(String boundary, InputStream in, Properties parms, String uri) throws InterruptedException {
      try {
        String line = readLine(in);
        int i = line.indexOf(boundary);
        if (i!=2)
          sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html" );
        if (line.substring(i+boundary.length()).startsWith("--"))
          return false;
        // read the header
        Properties item = new Properties();
        line = readLine(in);
        while ((line != null) && (line.trim().length()>0)) {
          int p = line.indexOf(':');
          if (p != -1)
            item.put( line.substring(0,p).trim().toLowerCase(), line.substring(p+1).trim());
          line = readLine(in);
        }
        // analyze the header
        if( line!=null ) {
          String contentDisposition = item.getProperty("content-disposition");
          if( contentDisposition == null)
            sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html" );

          {
            String key = parms.getProperty("key");
            boolean nps = Pattern.matches("/[^/]+/NodePersistentStorage.*", uri);
            if (nps) {
              Pattern p = Pattern.compile(".*NodePersistentStorage/([^/]+)/([^/]+)");
              Matcher m = p.matcher(uri);
              boolean b = m.matches();
              if (!b) {
                sendError(HTTP_BAD_REQUEST, "NodePersistentStorage URL malformed");
              }
              String categoryName = m.group(1);
              String keyName = m.group(2);
              H2O.getNPS().put(categoryName, keyName, new InputStreamWrapper(in, boundary.getBytes()));
              long length = H2O.getNPS().get_length(categoryName, keyName);
              String responsePayload = "{ " +
                      "\"category\" : "     + "\"" + categoryName + "\", " +
                      "\"name\" : "         + "\"" + keyName      + "\", " +
                      "\"total_bytes\" : "  +        length       + " " +
                      "}";
              sendResponse(HTTP_OK, MIME_JSON, null, new ByteArrayInputStream(responsePayload.getBytes("UTF-8")));
              return true;
            }
          }

          {
            String destination_key = parms.getProperty("destination_frame");
            if (destination_key == null) {
              destination_key = "upload" + Key.rand();
            }
            if (!validKeyName(destination_key)) {
              sendError(HTTP_BAD_REQUEST, "Invalid key name, contains illegal characters");
            }
            boolean uploadFile = Pattern.matches("/PostFile", uri) ||               // no version
                                 Pattern.matches("/[1-9][0-9]*/PostFile", uri) ||   // numbered version
                                 Pattern.matches("/LATEST/PostFile", uri);          // use LATEST
            if (uploadFile) {
              //
              // Here is an example of how to upload a file from the command line.
              //
              // curl -v -F "file=@allyears2k_headers.zip" "http://localhost:54321/PostFile?key=a.zip"
              //
              // This call is handled as a POST request in method NanoHTTPD#fileUpload
              //
              // JSON Payload returned is:
              //     { "destination_key": "key_name", "total_bytes": nnn }
              //
              UploadFileVec.ReadPutStats stats = new UploadFileVec.ReadPutStats();
              UploadFileVec.readPut(destination_key, new InputStreamWrapper(in, boundary.getBytes()), stats);
              // TODO: Figure out how to marshal a response here Ray-style so that docs, etc. are generated properly.
              String responsePayload = "{ \"destination_frame\": \"" + destination_key + "\", \"total_bytes\": " + stats.total_bytes + " }\n";
              sendResponse(HTTP_OK, MIME_JSON, null, new ByteArrayInputStream(responsePayload.getBytes("UTF-8")));
              return true;
            }
          }

          sendError(HTTP_NOT_FOUND, "(Attempt to upload data) URL not found");
        }
      }
      catch (Exception e) {
        sendError(HTTP_INTERNAL_ERROR, "SERVER INTERNAL ERROR: Exception: " + e.getMessage());
      }
      return false;
    }

    /**
     * Decodes the percent encoding scheme. <br/>
     * For example: "an+example%20string" -> "an example string"
     */
    private String decodePercent( String str ) throws InterruptedException
    {
      try
      {
        StringBuilder sb = new StringBuilder();
        for( int i=0; i<str.length(); i++ )
        {
          char c = str.charAt( i );
          switch ( c )
          {
          case '+':
            sb.append( ' ' );
            break;
          case '%':
            sb.append((char)Integer.parseInt( str.substring(i+1,i+3), 16 ));
            i += 2;
            break;
          default:
            sb.append( c );
            break;
          }
        }
        return sb.toString();
      }
      catch( Exception e ) {
        sendError(HTTP_BAD_REQUEST, "BAD REQUEST: Bad percent-encoding." );
        return null;
      }
    }

    /**
     * Decodes parameters in percent-encoded URI-format
     * ( e.g. "name=Jack%20Daniels&pass=Single%20Malt" ) and
     * adds them to given Properties. NOTE: this doesn't support multiple
     * identical keys due to the simplicity of Properties -- if you need multiples,
     * you might want to replace the Properties with a Hashtable of Vectors or such.
     */
    private void decodeParms( String parms, Properties p )
        throws InterruptedException
        {
      if ( parms == null )
        return;

      StringTokenizer st = new StringTokenizer( parms, "&" );
      while ( st.hasMoreTokens())
      {
        String e = st.nextToken();
        int sep = e.indexOf( '=' );
        if ( sep >= 0 ) {
          String key = decodePercent( e.substring( 0, sep ) ).trim();
          String value = decodePercent( e.substring( sep+1 ) );
          String old = p.getProperty(key, null);
          p.put(key, old == null ? value : (old+","+value));
        }
      }
    }

    /**
     * Returns an error message as a HTTP response and
     * throws InterruptedException to stop further request processing.
     */
    private void sendError( String status, String msg ) throws InterruptedException
    {
      long deltaMillis = System.currentTimeMillis() - startMillis;
      String s = "         HTTP_status: " + status + ", millis: " + deltaMillis;
      Log.httpd(s);
      sendResponse2(status, MIME_PLAINTEXT, null, new ByteArrayInputStream( msg.getBytes()));
      throw new InterruptedException();
    }

    private void sendResponse( String status, String mime, Properties header, InputStream data ) {
      long deltaMillis = System.currentTimeMillis() - startMillis;
      String s = "         HTTP_status: " + status + ", millis: " + deltaMillis;
      Log.httpd(s);
      sendResponse2(status, mime, header, data);
    }

    /**
     * Sends given response to the socket.
     */
    private void sendResponse2(String status, String mime, Properties header, InputStream data )
    {
      try
      {
        if ( status == null )
          throw new RuntimeException( "sendResponse(): Status can't be null." );

        OutputStream out = mySocket.getOutputStream();
        PrintWriter pw = new PrintWriter( out );
        pw.print("HTTP/1.0 " + status + " \r\n");

        if ( mime != null )
          pw.print("Content-Type: " + mime + "\r\n");

        if ( header == null || header.getProperty( "Date" ) == null )
          pw.print( "Date: " + gmtFrmt.format( new Date()) + "\r\n");

        if ( header != null )
        {
          Enumeration e = header.keys();
          while ( e.hasMoreElements())
          {
            String key = (String)e.nextElement();
            String value = header.getProperty( key );
            pw.print( key + ": " + value + "\r\n");
          }
        }

        // Add these three HTTP headers to every response no matter what.
        pw.print("X-h2o-build-project-version" + ": " + H2O.ABV.projectVersion() + "\r\n");
        pw.print("X-h2o-rest-api-version-max" + ": " + RequestServer.H2O_REST_API_VERSION + "\r\n");
        pw.print("X-h2o-cluster-id" + ": " + H2O.CLUSTER_ID + "\r\n");
        pw.print("X-h2o-cluster-good" + ": " + H2O.CLOUD.healthy() + "\r\n");

        pw.print("\r\n");
        pw.flush();

        if ( data != null )
        {
          int pending = data.available();	// This is to support partial sends, see serveFile()
          byte[] buff = new byte[theBufferSize];
          while (pending>0)
          {
            int read = data.read( buff, 0, ( (pending>theBufferSize) ?  theBufferSize : pending ));
            if (read <= 0)	break;
            out.write( buff, 0, read );
            //pending -= read;
            pending = data.available();
          }
        }
        out.flush();
        out.close();
        if ( data != null )
          data.close();
      }
      catch( IOException e ) {
        Log.err(e);
        // Couldn't write? No can do.
        try { mySocket.close(); } catch( IOException ignore ) { }
      }

      if (H2O.getShutdownRequested()) {
        H2O.shutdown(0);
      }
    }

    private Socket mySocket;
  }

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

  /**
   * URL-encodes everything between "/"-characters.
   * Encodes spaces as '%20' instead of '+'.
   * @throws UnsupportedEncodingException
   */
  private String encodeUri( String uri ) {
    String newUri = "";
    StringTokenizer st = new StringTokenizer( uri, "/ ", true );
    while ( st.hasMoreTokens()) {
      String tok = st.nextToken();
      switch (tok) {
        case "/":
          newUri += "/";
          break;
        case " ":
          newUri += "%20";
          break;
        default:
          try {
            newUri += URLEncoder.encode(tok, "UTF-8");
          } catch (UnsupportedEncodingException e) {
            throw Log.throwErr(e);
          }
          break;
      }
    }
    return newUri;
  }

  private final ServerSocket myServerSocket;
  private Thread myThread;
  private File myRootDir;

  // ==================================================
  // File server code
  // ==================================================

  /**
   * Serves file from homeDir and its' subdirectories (only).
   * Uses only URI, ignores all headers and HTTP parameters.
   */
  public Response serveFile( String uri, Properties header, File homeDir,
      boolean allowDirectoryListing )
  {
    Response res = null;

    // Make sure we won't die of an exception later
    if ( !homeDir.isDirectory())
      res = new Response(HTTP_INTERNAL_ERROR, MIME_PLAINTEXT,
          "INTERNAL ERROR: serveFile(): given homeDir is not a directory." );

    if ( res == null )
    {
      // Remove URL arguments
      uri = uri.trim().replace( File.separatorChar, '/' );
      if ( uri.indexOf( '?' ) >= 0 )
        uri = uri.substring(0, uri.indexOf( '?' ));

      // Prohibit getting out of current directory
      if ( uri.startsWith( ".." ) || uri.endsWith( ".." ) || uri.contains("../"))
        res = new Response( HTTP_FORBIDDEN, MIME_PLAINTEXT,
            "FORBIDDEN: Won't serve ../ for security reasons." );
    }

    File f = new File( homeDir, uri );
    if ( res == null && !f.exists())
      res = new Response(HTTP_NOT_FOUND, MIME_PLAINTEXT,
          "Error 404, file not found." );

    // List the directory, if necessary
    if ( res == null && f.isDirectory())
    {
      // Browsers get confused without '/' after the
      // directory, send a redirect.
      if ( !uri.endsWith( "/" ))
      {
        uri += "/";
        res = new Response( HTTP_REDIRECT, MIME_HTML,
            "<html><body>Redirected: <a href=\"" + uri + "\">" +
                uri + "</a></body></html>");
        res.addHeader( "Location", uri );
      }

      if ( res == null )
      {
        // First try index.html and index.htm
        if ( new File( f, "index.html" ).exists())
          f = new File( homeDir, uri + "/index.html" );
        else if ( new File( f, "index.htm" ).exists())
          f = new File( homeDir, uri + "/index.htm" );
        // No index file, list the directory if it is readable
        else if ( allowDirectoryListing && f.canRead() )
        {
          String[] files = f.list();
          String msg = "<html><body><h1>Directory " + uri + "</h1><br/>";

          if ( uri.length() > 1 )
          {
            String u = uri.substring( 0, uri.length()-1 );
            int slash = u.lastIndexOf( '/' );
            if ( slash >= 0 && slash  < u.length())
              msg += "<b><a href=\"" + uri.substring(0, slash+1) + "\">..</a></b><br/>";
          }

          if (files!=null)
          {
            for ( int i=0; i<files.length; ++i )
            {
              File curFile = new File( f, files[i] );
              boolean dir = curFile.isDirectory();
              if ( dir )
              {
                msg += "<b>";
                files[i] += "/";
              }

              msg += "<a href=\"" + encodeUri( uri + files[i] ) + "\">" +
                  files[i] + "</a>";

              // Show file size
              if ( curFile.isFile())
              {
                long len = curFile.length();
                msg += " &nbsp;<font size=2>(";
                if ( len < 1024 )
                  msg += len + " bytes";
                else if ( len < 1024 * 1024 )
                  msg += len/1024 + "." + (len%1024/10%100) + " KB";
                else
                  msg += len/(1024*1024) + "." + len%(1024*1024)/10%100 + " MB";

                msg += ")</font>";
              }
              msg += "<br/>";
              if ( dir ) msg += "</b>";
            }
          }
          msg += "</body></html>";
          res = new Response( HTTP_OK, MIME_HTML, msg );
        }
        else
        {
          res = new Response( HTTP_FORBIDDEN, MIME_PLAINTEXT,
              "FORBIDDEN: No directory listing." );
        }
      }
    }

    try
    {
      if ( res == null )
      {
        // Get MIME type from file name extension, if possible
        String mime = null;
        int dot = f.getCanonicalPath().lastIndexOf( '.' );
        if ( dot >= 0 )
          mime = (String)theMimeTypes.get( f.getCanonicalPath().substring( dot + 1 ).toLowerCase());
        if ( mime == null )
          mime = MIME_DEFAULT_BINARY;

        // Calculate etag
        String etag = Integer.toHexString((f.getAbsolutePath() + f.lastModified() + "" + f.length()).hashCode());

        // Support (simple) skipping:
        long startFrom = 0;
        long endAt = -1;
        String range = header.getProperty( "range" );
        if ( range != null )
        {
          if ( range.startsWith( "bytes=" ))
          {
            range = range.substring( "bytes=".length());
            int minus = range.indexOf( '-' );
            try {
              if ( minus > 0 )
              {
                startFrom = Long.parseLong( range.substring( 0, minus ));
                endAt = Long.parseLong( range.substring( minus+1 ));
              }
            }
            catch ( NumberFormatException e ) { Log.err(e); }
          }
        }

        // Change return code and add Content-Range header when skipping is requested
        long fileLen = f.length();
        if (range != null && startFrom >= 0)
        {
          if ( startFrom >= fileLen)
          {
            res = new Response( HTTP_RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "" );
            res.addHeader( "Content-Range", "bytes 0-0/" + fileLen);
            res.addHeader( "ETag", etag);
          }
          else
          {
            if ( endAt < 0 )
              endAt = fileLen-1;
            long newLen = endAt - startFrom + 1;
            if ( newLen < 0 ) newLen = 0;

            final long dataLen = newLen;
            try (FileInputStream fis = new FileInputStream(f) {
              public int available() throws IOException {
                return (int) dataLen;
              }
            }) {
              fis.skip(startFrom);

              res = new Response(HTTP_PARTIAL_CONTENT, mime, fis);
              res.addHeader("Content-Length", "" + dataLen);
              res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
              res.addHeader("ETag", etag);
            }
          }
        }
        else
        {
          if (etag.equals(header.getProperty("if-none-match")))
            res = new Response(HTTP_NOT_MODIFIED, mime, "");
          else
          {
            res = new Response( HTTP_OK, mime, new FileInputStream( f ));
            res.addHeader( "Content-Length", "" + fileLen);
            res.addHeader( "ETag", etag);
          }
        }
      }
    }
    catch( IOException e )  {
      Log.err(e);
      res = new Response( HTTP_FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed." );
    }

    res.addHeader( "Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requestes
    return res;
  }

  /**
   * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
   */
  private static Hashtable theMimeTypes = new Hashtable();
  static
  {
    StringTokenizer st = new StringTokenizer(
        "css		text/css "+
            "htm		text/html "+
            "html		text/html "+
            "xml		text/xml "+
            "txt		text/plain "+
            "asc		text/plain "+
            "gif		image/gif "+
            "jpg		image/jpeg "+
            "jpeg		image/jpeg "+
            "png		image/png "+
            "mp3		audio/mpeg "+
            "m3u		audio/mpeg-url " +
            "mp4		video/mp4 " +
            "ogv		video/ogg " +
            "flv		video/x-flv " +
            "mov		video/quicktime " +
            "swf		application/x-shockwave-flash " +
            "js			application/javascript "+
            "pdf		application/pdf "+
            "doc		application/msword "+
            "ogg		application/x-ogg "+
            "zip		application/octet-stream "+
            "exe		application/octet-stream "+
        "class		application/octet-stream " );
    while ( st.hasMoreTokens())
      theMimeTypes.put( st.nextToken(), st.nextToken());
  }

  private static int theBufferSize = 16 * 1024;

  // Change this if you want to log to somewhere else than stdout
  protected static final PrintStream myOut = System.out;

  /**
   * GMT date formatter
   */
  private static java.text.SimpleDateFormat gmtFrmt;
  static
  {
    gmtFrmt = new java.text.SimpleDateFormat( "E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
    gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  /**
   * The distribution licence
   */
  private static final String LICENCE =
      "Copyright (C) 2001,2005-2011 by Jarno Elonen <elonen@iki.fi>\n"+
          "and Copyright (C) 2010 by Konstantinos Togias <info@ktogias.gr>\n"+
          "\n"+
          "Redistribution and use in source and binary forms, with or without\n"+
          "modification, are permitted provided that the following conditions\n"+
          "are met:\n"+
          "\n"+
          "Redistributions of source code must retain the above copyright notice,\n"+
          "this list of conditions and the following disclaimer. Redistributions in\n"+
          "binary form must reproduce the above copyright notice, this list of\n"+
          "conditions and the following disclaimer in the documentation and/or other\n"+
          "materials provided with the distribution. The name of the author may not\n"+
          "be used to endorse or promote products derived from this software without\n"+
          "specific prior written permission. \n"+
          " \n"+
          "THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR\n"+
          "IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES\n"+
          "OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.\n"+
          "IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,\n"+
          "INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT\n"+
          "NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,\n"+
          "DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY\n"+
          "THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT\n"+
          "(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE\n"+
          "OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";
}

