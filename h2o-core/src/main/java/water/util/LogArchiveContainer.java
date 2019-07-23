package water.util;

import water.api.RequestServer;

import java.io.ByteArrayOutputStream;

public enum LogArchiveContainer {
  ZIP(RequestServer.MIME_DEFAULT_BINARY) {
    @Override
    public LogArchiveWriter createLogArchiveWriter(ByteArrayOutputStream baos) {
      return new ZipLogArchiveWriter(baos);
    }
  },
  LOG(RequestServer.MIME_PLAINTEXT) {
    @Override
    public LogArchiveWriter createLogArchiveWriter(ByteArrayOutputStream baos) {
      return new ConcatenatedLogArchiveWriter(baos);
    }
  };

  private final String _mime_type;

  LogArchiveContainer(String mimeType) {
    _mime_type = mimeType;
  }

  public abstract LogArchiveWriter createLogArchiveWriter(ByteArrayOutputStream baos);

  public String getFileExtension() {
    return name().toLowerCase();
  }
  
  public String getMimeType() {
    return _mime_type;
  }
  
}
