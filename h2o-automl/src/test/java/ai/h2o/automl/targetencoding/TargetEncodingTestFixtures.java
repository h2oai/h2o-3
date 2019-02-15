package ai.h2o.automl.targetencoding;

public class TargetEncodingTestFixtures {
  public TargetEncodingTestFixtures() {

  }

  public static TargetEncodingParams defaultTEParams() {
    return new TargetEncodingParams(new BlendingParams(3, 1), TargetEncoder.DataLeakageHandlingStrategy.KFold, 0.01);
  }
}
