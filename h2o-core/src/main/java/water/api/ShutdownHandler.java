package water.api;

import water.H2O;
import water.Iced;

public class ShutdownHandler extends Handler {
  public static final class Shutdown extends Iced {
  }

  @SuppressWarnings("unused")
  public ShutdownV2 shutdown (int version, ShutdownV2 s) {
    Shutdown t = s.createAndFillImpl();
    H2O.requestShutdown();
    return s.fillFromImpl(t);
  }
}
