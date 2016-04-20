package hex.api;

import hex.util.Aggregator;
import water.api.Handler;
import water.api.Schema;

public class AggregateHandler extends Handler {

  public AggregatorV3 run(int version, AggregatorV3 spv3) {
    Aggregator sp = spv3.createAndFillImpl();
    return (AggregatorV3) Schema.schema(version, Aggregator.class).fillFromImpl(sp.execImpl());
  }
}