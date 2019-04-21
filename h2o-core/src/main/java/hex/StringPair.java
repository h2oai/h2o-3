package hex;

import water.Iced;

public class StringPair extends Iced<StringPair> {
  public StringPair() {}

  public StringPair(String a, String b) {
    _a = a;
    _b = b;
  }

  String _a;
  String _b;
}
