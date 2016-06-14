package water.api;

import water.util.PojoUtils;
import water.util.WaterMeterIo;

public class WaterMeterIoV3 extends SchemaV3<WaterMeterIo, WaterMeterIoV3> {
  @API(help="Index of node to query ticks for (0-based)", direction = API.Direction.INPUT)
  public int nodeidx;

  @API(help="array of IO info", direction = API.Direction.OUTPUT)
  public WaterMeterIo.IoStatsEntry persist_stats[];

  // Version&Schema-specific filling into the implementation object
  public WaterMeterIo createImpl() {
    WaterMeterIo obj = new WaterMeterIo();
    PojoUtils.copyProperties(obj, this, PojoUtils.FieldNaming.CONSISTENT);
    return obj;
  }

  // Version&Schema-specific filling from the implementation object
  public WaterMeterIoV3 fillFromImpl(WaterMeterIo i) {
    PojoUtils.copyProperties(this, i, PojoUtils.FieldNaming.CONSISTENT);
    return this;
  }
}
