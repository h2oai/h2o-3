package water.api;

public class HDFSIOException extends APIException {
  public HDFSIOException(String hdfsURI, String hdfsConf, Exception e) {
    super( "HDFS IO Failure: \n"
            + " accessed URI : " + hdfsURI + "\n"
            + " configuration: " + hdfsConf + "\n"
            + " " + e);
  }
}
