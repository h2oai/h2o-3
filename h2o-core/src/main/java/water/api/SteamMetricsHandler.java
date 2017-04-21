package water.api;

import water.api.schemas3.SteamMetricsV3;
import water.H2O;

public class SteamMetricsHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public SteamMetricsV3 fetch(int version, SteamMetricsV3 s) {
    s.version = 0;

    // Fields filled in for version 0.
    s.idle_millis = H2O.getIdleTimeMillis();

    return s;
  }
}
