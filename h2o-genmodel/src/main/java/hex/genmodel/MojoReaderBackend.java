package hex.genmodel;

import java.io.BufferedReader;
import java.io.IOException;

/**
 */
public interface MojoReaderBackend {

  BufferedReader getTextFile(String filename) throws IOException;

  byte[] getBinaryFile(String filename) throws IOException;

  boolean exists(String filename);
}
