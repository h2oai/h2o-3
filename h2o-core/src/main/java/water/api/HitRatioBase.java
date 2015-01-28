package water.api;

import hex.HitRatio;

public class HitRatioBase<I extends HitRatio, S extends HitRatioBase> extends Schema<I, HitRatioBase<I, S>> {
  @API(help="Hit Ratios", direction=API.Direction.OUTPUT)
  private float[] hit_ratios; // Hit ratios for k=1...K
}
