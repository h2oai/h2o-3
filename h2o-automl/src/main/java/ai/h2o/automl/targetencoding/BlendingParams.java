package ai.h2o.automl.targetencoding;

import water.Iced;

public class BlendingParams extends Iced<BlendingParams> {
  private double k;
  private double f;

  public BlendingParams(double k, double f) {
    this.k = k;
    this.f = f;
  }

  public double getK() {
    return k;
  }

  public double getF() {
    return f;
  }
}