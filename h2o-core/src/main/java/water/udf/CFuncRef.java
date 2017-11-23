package water.udf;

import water.Iced;
import water.Key;

// FIXME: should be CFuncRef (custom func reference

public class CFuncRef extends Iced<CFuncRef> {
  public static final CFuncRef NOP = null;
  
  public final String keyName;

  public final String funcName;

  public final String language;

  public CFuncRef(String language, String keyName, String funcName) {
    this.language = language;
    this.keyName = keyName;
    this.funcName = funcName;
  }

  /**
   * Create function definition from "lang:keyName=funcName"
   *
   * @param def  function definition
   * @return instance of function of NOP if definition is wrong
   */
  public static CFuncRef from(String def) {
    if (def == null || def.isEmpty()) {
      return NOP;
    }
    String[] parts = def.split("=");
    assert parts.length == 2 : "Input should be `lang:key=funcName`";
    String[] langParts = parts[0].split(":");
    assert langParts.length == 2 : "Input should be `lang:key=funcName`";
    return new CFuncRef(langParts[0], langParts[1], parts[1]);
  }

  public static CFuncRef from(String lang, String keyName, String funcName) {
    return new CFuncRef(lang, keyName, funcName);
  }

  public String getName() {
    return keyName;
  }

  public Key getKey() {
    return Key.make(keyName);
  }

  public String toRef() {
    return String.format("%s:%s=%s", language, keyName, funcName);
  }
}
