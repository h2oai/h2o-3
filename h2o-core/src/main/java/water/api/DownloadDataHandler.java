package water.api;

@SuppressWarnings("unused")
public class DownloadDataHandler extends Handler {
  public DownloadDataV3 fetch(int version, DownloadDataV3 server) {
    throw new RuntimeException("Function fetch should never be called.");
    // This should never happen, since DownloadDataset is handled in JettyHTTPD.
  }

  public DownloadDataV3 fetchStreaming(int version, DownloadDataV3 server) {
    throw new RuntimeException("Function fetch should never be called.");
    // This should never happen, since DownloadDataset is handled in JettyHTTPD.
  }
}
