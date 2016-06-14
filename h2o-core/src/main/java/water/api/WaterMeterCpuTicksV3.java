package water.api;

import water.util.PojoUtils;
import water.util.WaterMeterCpuTicks;

public class WaterMeterCpuTicksV3 extends SchemaV3<WaterMeterCpuTicks, WaterMeterCpuTicksV3> {
  @API(help="Index of node to query ticks for (0-based)", required = true, direction = API.Direction.INPUT)
  public int nodeidx;

  @API(help="array of tick counts per core", direction = API.Direction.OUTPUT)
  public long[][] cpu_ticks;

  // Version&Schema-specific filling into the implementation object
  public WaterMeterCpuTicks createImpl() {
    WaterMeterCpuTicks obj = new WaterMeterCpuTicks();
    PojoUtils.copyProperties(obj, this, PojoUtils.FieldNaming.CONSISTENT);
    return obj;
  }

  // Version&Schema-specific filling from the implementation object
  public WaterMeterCpuTicksV3 fillFromImpl(WaterMeterCpuTicks i) {
    PojoUtils.copyProperties(this, i, PojoUtils.FieldNaming.CONSISTENT);
    return this;
  }
}
