package ai.h2o.targetencoding;

import water.Iced;

public class TEComponents extends Iced<TEComponents> {

  private double _numerator;
  private long _denominator;

  public TEComponents(double numerator, long denominator) {
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
