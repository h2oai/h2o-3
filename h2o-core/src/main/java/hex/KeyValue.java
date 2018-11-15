package hex;

import water.Iced;

public class KeyValue extends Iced<KeyValue> {
  public KeyValue() {}

  public KeyValue(String key, double value) {
    _key = key;
    _value = value;
  }

  String _key;
  double _value;

  public String getKey() {
    return _key;
  }

  public double getValue() {
    return _value;
  }
}
