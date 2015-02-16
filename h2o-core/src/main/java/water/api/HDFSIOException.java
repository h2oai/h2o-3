package water.api;

public class HDFSIOException extends APIException {
  public HDFSIOException(String hdfsURI, String hdfsConf, Throwable t) {
    super( "HDFS IO Failure: \n"
            + " accessed URI : " + hdfsURI
            + " configuration: " + hdfsConf, t);
  }
}
