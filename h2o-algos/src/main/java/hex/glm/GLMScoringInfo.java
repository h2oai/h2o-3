package hex.glm;

import hex.ScoringInfo;

public class GLMScoringInfo extends ScoringInfo implements ScoringInfo.HasIterations {
  public int iterations;
  public int iterations() { return iterations; };
}
