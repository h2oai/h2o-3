package ai.h2o.automl.targetencoding;

import java.util.Random;

public class TargetEncodingTestFixtures {
  public TargetEncodingTestFixtures() {

  }

  public static TargetEncodingParams defaultTEParams() {
    return new TargetEncodingParams(new BlendingParams(3, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);
  }
  public static TargetEncodingParams randomTEParams(long seed) {
    Random generator = seed == -1 ? new Random() : new Random(seed);
    byte strategy = generator.nextDouble() >= 0.5 ? TargetEncoder.DataLeakageHandlingStrategy.KFold : TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut;
    return new TargetEncodingParams(new BlendingParams(3, 1), strategy, 0.01);
  }
  
  public static TargetEncodingParams randomTEParams() {
    return randomTEParams(-1);
  }
}
