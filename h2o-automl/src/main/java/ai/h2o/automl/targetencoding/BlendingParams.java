package ai.h2o.automl.targetencoding;

import water.Iced;

public class BlendingParams extends Iced<BlendingParams> {
  private double _k;
  private double _f;

  public BlendingParams(double k, double f) {
    _k = k;
    _f = f;
  }

  public double getK() {
    return _k;
  }

  public double getF() {
    return _f;
  }
}