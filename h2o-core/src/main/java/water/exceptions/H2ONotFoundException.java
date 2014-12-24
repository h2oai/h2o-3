package water.exceptions;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

public class H2ONotFoundException extends H2ORuntimeException {
  protected int HTTP_RESPONSE_CODE() { return HttpResponseStatus.NOT_FOUND.getCode(); }

  public H2ONotFoundException(String msg, String dev_msg) {
    super(msg, dev_msg);
  }
}
