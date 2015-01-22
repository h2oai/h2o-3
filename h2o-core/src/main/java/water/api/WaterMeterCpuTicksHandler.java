package water.api;

import water.util.WaterMeterCpuTicks;

public class WaterMeterCpuTicksHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public WaterMeterCpuTicksV1 fetch(int version, WaterMeterCpuTicksV1 s) {
    WaterMeterCpuTicks impl = s.createAndFillImpl();
    impl.doIt();
    return s.fillFromImpl(impl);
  }
}
