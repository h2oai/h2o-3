package water.api;

import java.io.OutputStream;

/**
 * Stream provider.
 */
public interface StreamWriter {

  /**
   * The implementation write its content to given output stream.
   * @param os  output stream provided by framework
   */
  void writeTo(OutputStream os);

}
