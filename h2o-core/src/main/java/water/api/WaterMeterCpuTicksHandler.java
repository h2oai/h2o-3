package water.api;

import water.api.schemas3.WaterMeterCpuTicksV3;
import water.util.WaterMeterCpuTicks;

public class WaterMeterCpuTicksHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public WaterMeterCpuTicksV3 fetch(int version, WaterMeterCpuTicksV3 s) {
    WaterMeterCpuTicks impl = s.createAndFillImpl();
    impl.doIt();
    return s.fillFromImpl(impl);
  }
}
