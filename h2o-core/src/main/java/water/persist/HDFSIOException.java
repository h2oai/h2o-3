package water.persist;

public class HDFSIOException extends RuntimeException {
  public HDFSIOException(String hdfsURI, String hdfsConf, Exception e) {
    super( "HDFS IO Failure: \n"
            + " accessed URI : " + hdfsURI + "\n"
            + " configuration: " + hdfsConf + "\n"
            + " " + e);
  }
}
