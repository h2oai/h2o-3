package water.exceptions;

import water.util.HttpResponseStatus;

public class H2ONotFoundArgumentException extends H2OIllegalArgumentException {
  final protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.NOT_FOUND.getCode(); }

  public H2ONotFoundArgumentException(String msg, String dev_msg) {
    super(msg, dev_msg);
  }

  public H2ONotFoundArgumentException(String msg) {
    super(msg, msg);
  }
}
