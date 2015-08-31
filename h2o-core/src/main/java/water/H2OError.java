package water;

import water.util.HttpResponseStatus;
import water.util.IcedHashMap;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class which represents a back-end error which will be returned to the client.  Such
 * errors may be caused by the user (specifying an object which has been removed) or due
 * to a failure which is out of the user's control.
 */
public class H2OError extends Iced {
  /** Milliseconds since the epoch for the time that this H2OError instance was created.  Generally this is a short time since the underlying error ocurred. */
  public long _timestamp;

  public String _error_url = null;

  /** Message intended for the end user (a data scientist). */
  public String _msg;

  /** Potentially more detailed message intended for a developer (e.g. a front end engineer or someone designing a language binding). */
  public String _dev_msg;

  /** HTTP status code for this error. */
  public int _http_status;

  /** Unique ID for this error instance, so that later we can build a dictionary of errors for docs and I18N.
      public int _error_id;*/

  /** Any values that are relevant to reporting or handling this error.  Examples are a key name if the error is on a key, or a field name and object name if it's on a specific field. */
  public IcedHashMap.IcedHashMapStringObject _values;

  /** Exception type, if any. */
  public String _exception_type;

  /** Raw exception message, if any. */
  public String _exception_msg;

  /** Stacktrace, if any. */
  public String[] _stacktrace;

  public H2OError(String error_url, String msg, String dev_msg, int http_status, IcedHashMap.IcedHashMapStringObject values, Exception e) {
    this(System.currentTimeMillis(), error_url, msg, dev_msg, http_status, values, e);
  }

  public H2OError(long timestamp, String error_url, String msg, String dev_msg, int http_status, IcedHashMap.IcedHashMapStringObject values, Exception e) {
    this._timestamp = timestamp;
    this._error_url = error_url;
    this._msg = msg;
    this._dev_msg = dev_msg;
    this._http_status = http_status;
    this._values = values;

    if (null == this._msg) {
      // It's crazy, but some Java exceptions like NullPointerException do not have a message!
      if (null != e) {
        this._msg = "Caught exception: " + e.getClass().getCanonicalName();
        this._dev_msg = this._msg + " from: " + e.getStackTrace()[0];
      } else {
        this._msg = "Unknown error";
        this._dev_msg = this._msg;
      }
    }

    if (null != e) {
      this._exception_type = e.getClass().getCanonicalName();
      this._exception_msg = e.getMessage();
      ArrayList<String> arr = new ArrayList<>();
      StackTraceElement[] trace = e.getStackTrace();
      for (StackTraceElement ste : trace) {
        String s = ste.toString();
        arr.add(s);
        if (s.startsWith("org.eclipse.jetty")) {
          // Don't need humongous jetty stack traces.
          break;
        }
      }
      this._stacktrace = arr.toArray(new String[0]);
    }
  }

  public H2OError(Exception e, String error_url) {
    this(System.currentTimeMillis(), error_url, e.getMessage(), e.getMessage(), HttpResponseStatus.INTERNAL_SERVER_ERROR.getCode(), new IcedHashMap.IcedHashMapStringObject(), e);
  }

  static public String httpStatusHeader(int status_code) {
    switch (status_code) {
    case 200: return "200 OK";
    case 201: return "201 Created";

    case 400: return "400 Bad Request";
    case 401: return "401 Unauthorized";
    case 403: return "403 Forbidden";
    case 404: return "404 Not Found";
    case 409: return "409 Conflict";
    case 410: return "410 Gone";
    case 412: return "412 Precondition Failed";

    case 500: return "500 Internal Server Error";
    case 501: return "501 Not Implemented";
    case 503: return "503 Service Unavailable";

    default: return status_code + " Unimplemented http status code";
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(this._dev_msg != null ? this._dev_msg : this._msg);
    sb.append("; ");
    sb.append("Stacktrace: ").append(Arrays.toString(this._stacktrace));
    if (!this._values.isEmpty()) {
      sb.append("; Values: ");
      sb.append(this._values.toJsonString());
    }
  return sb.toString();
  }
}
