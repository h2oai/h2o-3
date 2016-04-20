package hex.api;

import hex.util.Aggregator;
import water.api.API;
import water.api.KeyV3.FrameKeyV3;
import water.api.RequestSchema;

public class AggregatorV3 extends RequestSchema<Aggregator, AggregatorV3> {
  // INPUT
  @API(help="Dataset", required = true)
  public FrameKeyV3 dataset;

  // OUTPUT
  @API(help="Aggregated Table", direction = API.Direction.OUTPUT)
  public double[][] result;

  @Override
  public AggregatorV3 fillFromImpl(Aggregator impl) {
    super.fillFromImpl(impl);
    return this;
  }

  @Override
  public Aggregator createImpl() {
    return new Aggregator();
  }
}
