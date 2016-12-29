package ai.h2o.cascade.vals;

/**
 * String value.
 */
public class ValStr extends Val {
  private String value;


  public ValStr(String s) {
    value = s;
  }

  @Override public Type type() {
    return Type.STR;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Value representations
  //--------------------------------------------------------------------------------------------------------------------

  @Override public boolean isStr() {
    return true;
  }

  @Override public String getStr() {
    return value;
  }

}
