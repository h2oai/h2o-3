package ai.h2o.automl.targetencoding;

import water.Iced;

public class BlendingParams extends Iced<BlendingParams> {
  private long k;
  private long f;

  public BlendingParams(long k, long f) {
    this.k = k;
    this.f = f;
  }

  public long getK() {
    return k;
  }

  public long getF() {
    return f;
  }
}