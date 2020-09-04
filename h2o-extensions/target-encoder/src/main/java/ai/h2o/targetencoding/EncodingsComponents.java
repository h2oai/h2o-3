package ai.h2o.targetencoding;

import water.Iced;

class EncodingsComponents extends Iced<EncodingsComponents> {

  private double _numerator;
  private long _denominator;

  public EncodingsComponents(double numerator, long denominator) {
    _numerator = numerator;
    _denominator = denominator;
  }

  public double getNumerator() {
    return _numerator;
  }

  public long getDenominator() {
    return _denominator;
  }
}
