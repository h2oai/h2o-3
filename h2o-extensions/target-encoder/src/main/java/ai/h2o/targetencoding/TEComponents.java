package ai.h2o.targetencoding;

import water.Iced;

public class TEComponents extends Iced<TEComponents> {

  private int _numerator;  // FIXME: numerator is an int only for classification problems
  private int _denominator;

  public TEComponents(int numerator, int denominator) {
    _numerator = numerator;
    _denominator = denominator;
  }

  public int getNumerator() {
    return _numerator;
  }

  public int getDenominator() {
    return _denominator;
  }
}
