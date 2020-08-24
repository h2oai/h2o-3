package ai.h2o.targetencoding;

import water.Iced;

class EncodingsComponents extends Iced<EncodingsComponents> {

  private int _targetclass = -1;
  private double _numerator;
  private long _denominator;

  public EncodingsComponents(double numerator, long denominator) {
    _numerator = numerator;
    _denominator = denominator;
  }

  public EncodingsComponents(int targetclass, double numerator, long denominator) {
    _targetclass = targetclass;
    _numerator = numerator;
    _denominator = denominator;
  }
  
  public boolean hasTargetClass() {
    return _targetclass >= 0;
  }

  public int getTargetClass() {
    return _targetclass;
  }

  public double getNumerator() {
    return _numerator;
  }

  public long getDenominator() {
    return _denominator;
  }
}
