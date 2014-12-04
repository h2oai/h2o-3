package water.api;

import water.util.WaterMeterCpuTicks;

public class WaterMeterCpuTicksHandler extends Handler {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public WaterMeterCpuTicksV1 fetch(int version, WaterMeterCpuTicksV1 s) {
    WaterMeterCpuTicks impl = s.createAndFillImpl();
    impl.doIt();
    return s.fillFromImpl(impl);
  }
}
