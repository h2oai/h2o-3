package water.api;

public class FSIOException extends APIException {
  public FSIOException(String path, Throwable t) {
    super( "FS IO Failure: \n"
            + " accessed path : " + path, t);
  }

  public FSIOException(String path, String msg) {
    super( "FS IO Failure: \n"
            + " accessed path : " + path
            + " msg: " + msg);
  }
}
