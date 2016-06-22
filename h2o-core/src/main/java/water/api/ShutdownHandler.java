package water.api;

import water.H2O;
import water.Iced;
import water.api.schemas3.ShutdownV3;

public class ShutdownHandler extends Handler {
  public static final class Shutdown extends Iced {
  }

  @SuppressWarnings("unused")
  public ShutdownV3 shutdown (int version, ShutdownV3 s) {
    Shutdown t = s.createAndFillImpl();
    H2O.requestShutdown();
    return s.fillFromImpl(t);
  }
}
