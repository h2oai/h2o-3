package water.api;

import water.Iced;
import water.api.schemas3.SchemaV3;

/**
 * Schema to represent schema.
 */
public class StreamingSchema extends SchemaV3<Iced, StreamingSchema> {

  private final transient StreamWriter streamWriter;
  private final transient String filename;

  public StreamingSchema() {
    this(null);
  }

  public StreamingSchema(StreamWriter streamWriter) {
    this(streamWriter, null);
  }

  public StreamingSchema(StreamWriter streamWriter, String filename) {
    this.streamWriter = streamWriter;
    this.filename = filename;
  }

  /**
   * Returns stream writer providing output stream.
   * @return writer outputing to given output stream.
   */
  public StreamWriter getStreamWriter() {
    return this.streamWriter;
  }

  public String getFilename() {
    return filename;
  }
}
