package ai.h2o.cascade.core;

/**
 * Value corresponding to an array of Strings.
 */
public class ValStrs extends Val {
  private String[] strs;


  public ValStrs(String[] arr) {
    strs = arr;
  }

  @Override
  public Type type() {
    return Type.STRS;
  }

  @Override
  public boolean isStrs() {
    return true;
  }

  @Override
  public String[] getStrs() {
    return strs;
  }
}
