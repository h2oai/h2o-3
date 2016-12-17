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

  @Override public String toString() {
    return value;
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Value representations
  //--------------------------------------------------------------------------------------------------------------------

  @Override public boolean maybeStr() {
    return true;
  }

  @Override public String getStr() {
    return value;
  }

}
