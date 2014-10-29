package water.api;

/**
 * The exception to report various errors during
 * handling API requests.
 */
abstract public class APIException extends RuntimeException {

  public APIException(String s, Throwable t) {
    super(s,t);
  }
}

class HDFSIOException extends APIException {

  public HDFSIOException(String hdfsURI, String hdfsConf, Throwable t) {
    super( "HDFS IO Failure: \n"
         + " accessed URI : " + hdfsURI
         + " configuration: " + hdfsConf, t);

  }

}
