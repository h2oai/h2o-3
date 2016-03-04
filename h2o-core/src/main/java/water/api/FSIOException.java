package water.api;

public class FSIOException extends APIException {
  public FSIOException(String path, Throwable t) {
    super( "FS IO Failure: \n"
            + " accessed path : " + path
            + " caused by: " + (t != null ? t.getMessage() : "NA"), t);
  }

  public FSIOException(String path, String msg) {
    super( "FS IO Failure: \n"
            + " accessed path : " + path
            + " msg: " + msg);
  }
}
