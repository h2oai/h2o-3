package hex.genmodel;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class InMemoryMojoReaderBackend implements MojoReaderBackend, Closeable {

  private static final Map<String, byte[]> CLOSED = Collections.unmodifiableMap(new HashMap<String, byte[]>());

  private Map<String, byte[]> _mojoContent;

  public InMemoryMojoReaderBackend(Map<String, byte[]> mojoContent) {
    _mojoContent = mojoContent;
  }

  @Override
  public BufferedReader getTextFile(String filename) throws IOException {
    checkOpen();
    byte[] data = _mojoContent.get(filename);
    if (data == null)
      throw new IOException("MOJO doesn't contain resource " + filename);
    return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)));
  }

  @Override
  public byte[] getBinaryFile(String filename) throws IOException {
    checkOpen();
    return _mojoContent.get(filename);
  }

  @Override
  public boolean exists(String filename) {
    checkOpen();
    return _mojoContent.containsKey(filename);
  }

  @Override
  public void close() throws IOException {
    _mojoContent = CLOSED;
  }

  private void checkOpen() {
    if (_mojoContent == CLOSED)
      throw new IllegalStateException("ReaderBackend was already closed");
  }

}
