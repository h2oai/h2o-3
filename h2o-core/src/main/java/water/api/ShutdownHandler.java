package water.api;

import water.H2O;
import water.Iced;

public class ShutdownHandler extends Handler {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public static final class Shutdown extends Iced {
  }

  @SuppressWarnings("unused")
  public ShutdownV2 shutdown (int version, ShutdownV2 s) {
    Shutdown t = s.createAndFillImpl();
    H2O.requestShutdown();
    return s.fillFromImpl(t);
  }
}
