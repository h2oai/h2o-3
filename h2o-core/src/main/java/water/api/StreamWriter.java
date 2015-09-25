package water.api;

import java.io.OutputStream;

/**
 * Stream provider.
 */
public abstract class StreamWriter {

  /**
   * The implementation write its content to given output stream.
   * @param os  output stream provided by framework
   */
  abstract public void writeTo(OutputStream os);

}
