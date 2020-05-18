package hex;

import water.Iced;

public class StringPair extends Iced<StringPair> {
  public StringPair() {}

  public StringPair(String a, String b) {
    _a = a;
    _b = b;
  }

  public String _a;
  public String _b;
}
