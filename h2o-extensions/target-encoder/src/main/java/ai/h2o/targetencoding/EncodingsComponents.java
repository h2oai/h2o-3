package ai.h2o.targetencoding;

import water.Iced;

class EncodingsComponents extends Iced<EncodingsComponents> {

  private int _class = -1;
  private double _numerator;
  private long _denominator;

  public EncodingsComponents(double numerator, long denominator) {
    _numerator = numerator;
    _denominator = denominator;
  }

  public EncodingsComponents(int cls, double numerator, long denominator) {
    _class = cls;
    _numerator = numerator;
    _denominator = denominator;
  }
  
  public boolean hasTargetClass() {
    return _class >= 0;
  }

  public int getTargetClass() {
    return _class;
  }

  public double getNumerator() {
    return _numerator;
  }

  public long getDenominator() {
    return _denominator;
  }
}
