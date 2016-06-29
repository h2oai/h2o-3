package water.api;

import water.util.FileUtils;
import water.util.Log;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * HTTP response.
 * Return one of these from serve().
 */
public class NanoResponse {

  /**
   * Default constructor: response = HTTP_OK, data = mime = 'null'
   */
  public NanoResponse() {
    this.status = RequestServer.HTTP_OK;
  }

  /**
   * Basic constructor.
   */
  public NanoResponse(String status, String mimeType, InputStream data) {
    this.status = status;
    this.mimeType = mimeType;
    this.data = data;
  }

  /**
   * Convenience method that makes an InputStream out of given text.
   */
  public NanoResponse(String status, String mimeType, String txt) {
    this.status = status;
    this.mimeType = mimeType;
    try {
      this.data = new ByteArrayInputStream(txt.getBytes("UTF-8"));
    }
    catch (java.io.UnsupportedEncodingException e) {
      Log.err(e);
    }
  }

  public void writeTo(OutputStream os) {
    FileUtils.copyStream(data, os, 1024);
  }

  /**
   * Adds given line to the header.
   */
  public void addHeader(String name, String value) {
    header.put(name, value);
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
