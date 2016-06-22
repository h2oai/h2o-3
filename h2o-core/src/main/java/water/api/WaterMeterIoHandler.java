package water.api;

import water.api.schemas3.WaterMeterIoV3;
import water.util.WaterMeterIo;

public class WaterMeterIoHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public WaterMeterIoV3 fetch(int version, WaterMeterIoV3 s) {
    WaterMeterIo impl = s.createAndFillImpl();
    impl.doIt(false);
    return s.fillFromImpl(impl);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public WaterMeterIoV3 fetch_all(int version, WaterMeterIoV3 s) {
    WaterMeterIo impl = s.createAndFillImpl();
    impl.doIt(true);
    return s.fillFromImpl(impl);
  }
}
