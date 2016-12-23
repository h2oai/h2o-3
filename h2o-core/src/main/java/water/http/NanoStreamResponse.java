package water.http;

import water.api.StreamWriter;
import water.http.NanoResponse;

import java.io.OutputStream;

/**
 */
public class NanoStreamResponse extends NanoResponse {

  public NanoStreamResponse(String status, String mimeType, StreamWriter streamWriter) {
    this.status = status;
    this.mimeType = mimeType;
    this.streamWriter = streamWriter;
  }

  @Override
  public void writeTo(OutputStream os) {
    streamWriter.writeTo(os);
  }

  public StreamWriter streamWriter;
}
