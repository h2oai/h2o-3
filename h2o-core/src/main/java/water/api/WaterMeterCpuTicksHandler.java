package water.api;

import water.H2O;
import water.util.WaterMeterCpuTicks;

public class WaterMeterCpuTicksHandler extends Handler<WaterMeterCpuTicks, WaterMeterCpuTicksV1> {
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  @Override public void compute2() { throw H2O.fail(); }
  @Override protected WaterMeterCpuTicksV1 schema(int version) { return new WaterMeterCpuTicksV1(); }

  public WaterMeterCpuTicksV1 fetch(int version, WaterMeterCpuTicks obj) {
    obj.doIt();
    return this.schema(version).fillFromImpl(obj);
  }
}
