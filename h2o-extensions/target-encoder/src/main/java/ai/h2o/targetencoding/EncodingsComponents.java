package ai.h2o.targetencoding;

import water.Iced;

class EncodingsComponents extends Iced<EncodingsComponents> {

  static final int NO_TARGET_CLASS = -1;
  
  private int _targetclass = NO_TARGET_CLASS;
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
